"""One-off prod assignment trigger; never changes credentials or deploys code."""
import collections
import json
import time

import boto3

REGION = "ap-northeast-2"
batch = boto3.client("batch", region_name=REGION)
table = boto3.resource("dynamodb", region_name=REGION).Table("bcar-prod-cars")


def snapshot():
    rows = []
    params = {"ProjectionExpression": "carNumber, agency, assignedUserId, uploadStatus, paid, isActive"}
    while True:
        page = table.scan(**params)
        rows.extend(r for r in page.get("Items", []) if "제이디모터스" in r.get("agency", ""))
        if "LastEvaluatedKey" not in page:
            break
        params["ExclusiveStartKey"] = page["LastEvaluatedKey"]
    return {
        "matching": len(rows),
        "assigned_unpaid": sum(bool(r.get("assignedUserId")) and not r.get("paid", False) for r in rows),
        "statuses": dict(collections.Counter(r.get("uploadStatus", "NONE") for r in rows)),
    }


control = table.get_item(Key={"carNumber": "_control"}, ConsistentRead=True).get("Item", {})
if control.get("stopDetail", False):
    raise SystemExit("Pipeline stop switch is enabled; no job submitted")

active = []
for queue in ["bcar-prod-queue", "bcar-prod-detail-queue", "bcar-prod-sync-and-upload-queue"]:
    for status in ["SUBMITTED", "PENDING", "RUNNABLE", "STARTING", "RUNNING"]:
        for page in batch.get_paginator("list_jobs").paginate(jobQueue=queue, jobStatus=status):
            active.extend({"id": j["jobId"], "name": j["jobName"], "status": j["status"]} for j in page["jobSummaryList"])
print("ACTIVE_JOBS", json.dumps(active), flush=True)
if active:
    raise SystemExit("Active pipeline jobs found; no duplicate job submitted")

print("BEFORE", json.dumps(snapshot()), flush=True)
response = batch.submit_job(
    jobName="assign-cars-jd-exclusion-manual",
    jobQueue="bcar-prod-queue",
    jobDefinition="bcar-prod-job",
    containerOverrides={"command": ["--job=assign-cars", "--next=true"]},
)
job_id = response["jobId"]
print("SUBMITTED_JOB", job_id, flush=True)
deadline = time.monotonic() + 900
last = None
while time.monotonic() < deadline:
    job = batch.describe_jobs(jobs=[job_id])["jobs"][0]
    status = job["status"]
    if status != last:
        print("ASSIGN_STATUS", status, flush=True)
        last = status
    if status in ["SUCCEEDED", "FAILED"]:
        print("AFTER_ASSIGN", json.dumps(snapshot()), flush=True)
        stream = job.get("container", {}).get("logStreamName")
        if stream:
            logs = boto3.client("logs", region_name=REGION)
            events = logs.get_log_events(logGroupName="/aws/batch/job", logStreamName=stream, startFromHead=False, limit=1000)["events"]
            for event in events:
                message = event["message"]
                if any(word in message for word in ["excluded=", "Submitted job", "Skip submitting", "completed:", "ERROR", "Exception"]):
                    print("ASSIGN_LOG", message, flush=True)
        if status == "FAILED":
            raise SystemExit("Assignment failed: " + job.get("statusReason", "unknown"))
        break
    time.sleep(15)
else:
    raise SystemExit("Assignment is still pending/running; do not resubmit. Job ID: " + job_id)
