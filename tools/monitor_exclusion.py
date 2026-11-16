"""Read-only monitoring for the manually started exclusion chain."""
import collections
import json
from datetime import datetime, timezone
import boto3

region = 'ap-northeast-2'
batch = boto3.client('batch', region_name=region)
logs = boto3.client('logs', region_name=region)
table = boto3.resource('dynamodb', region_name=region).Table('bcar-prod-cars')
anchor = batch.describe_jobs(jobs=['720db376-c4e4-4090-9959-1c2a5cf23bdd'])['jobs'][0]
rows = []
params = {
    'ProjectionExpression': 'carNumber, agency, assignedUserId, uploadStatus, paid, isActive',
    'FilterExpression': 'contains(agency, :agency)',
    'ExpressionAttributeValues': {':agency': '제이디모터스'},
    'ConsistentRead': True,
}
while True:
    page = table.scan(**params)
    rows.extend(page.get('Items', []))
    if 'LastEvaluatedKey' not in page:
        break
    params['ExclusiveStartKey'] = page['LastEvaluatedKey']
remaining = [r for r in rows if r.get('assignedUserId') and not r.get('paid', False)]
all_jobs = []
for page in batch.get_paginator('list_jobs').paginate(
    jobQueue='bcar-prod-sync-and-upload-queue',
    filters=[{'name': 'AFTER_CREATED_AT', 'values': [str(anchor['createdAt'])]}],
):
    all_jobs.extend(page['jobSummaryList'])
all_jobs.sort(key=lambda j: j['createdAt'])
report = {
    'checked_at': datetime.now(timezone.utc).isoformat(),
    'assignment_status': anchor['status'],
    'matching': len(rows),
    'assigned_unpaid_remaining': len(remaining),
    'statuses': dict(collections.Counter(r.get('uploadStatus', 'NONE') for r in rows)),
    'remaining_by_user': dict(collections.Counter(r['assignedUserId'] for r in remaining)),
    'sync_statuses': dict(collections.Counter(j['status'] for j in all_jobs)),
    'jobs': [{k: j.get(k) for k in ['jobId', 'jobName', 'status', 'createdAt', 'startedAt', 'stoppedAt', 'statusReason']} for j in all_jobs],
}
print('REPORT', json.dumps(report, ensure_ascii=False), flush=True)
for j in all_jobs:
    if j['status'] != 'FAILED':
        continue
    detail = batch.describe_jobs(jobs=[j['jobId']])['jobs'][0]
    stream = detail.get('container', {}).get('logStreamName')
    if stream:
        events = logs.get_log_events(logGroupName='/aws/batch/bcar-prod', logStreamName=stream, startFromHead=False, limit=1000)['events']
        for event in events:
            message = event['message']
            if any(s in message for s in ['ERROR', 'Exception', 'completed:', 'Submitted job', '무료 등록 한도', 'Timeout', 'waiting for', 'navigating to', 'Syncing ']):
                print('FAILED_JOB_LOG', j['jobId'], message, flush=True)
with open('exclusion-status.json', 'w') as f:
    json.dump(report, f, ensure_ascii=False)
