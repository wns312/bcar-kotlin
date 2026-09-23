# /// script
# requires-python = ">=3.11"
# dependencies = ["boto3", "google-auth", "requests"]
# ///
"""bcar 운영 스크립트. 앱이 하지 않는 일회성 작업만 모은다.

  uv run tools/bcar_admin.py seed-dev            # prod cars → dev 복사 (dry-run)
  uv run tools/bcar_admin.py seed-dev --apply
  uv run tools/bcar_admin.py fill-users --env dev --accounts-env ~/path/.env
  uv run tools/bcar_admin.py control --env dev --stop-detail on

모든 쓰기 명령은 --apply 없이는 계획만 출력한다.
"""

import argparse
import base64
import json
import re
import sys

import boto3

REGION = "ap-northeast-2"
USER_SHEET = "교차로계정정보"
SCOPES = ["https://www.googleapis.com/auth/spreadsheets"]
# 앱이 관리하는 필드. 복사본은 draft+detail만 갖고 할당은 dev에서 새로 한다
CAR_FIELDS = {
    "carNumber", "title", "company", "detailPageNum", "agency",
    "seller", "sellerPhone", "price", "isActive", "detail",
}


def ddb():
    return boto3.client("dynamodb", region_name=REGION)


def table(env):
    return f"bcar-{env}-cars"


def scan(client, name, projection=None):
    token, out = None, []
    while True:
        page = client.scan(
            TableName=name,
            **({"ExclusiveStartKey": token} if token else {}),
            **({"ProjectionExpression": projection} if projection else {}),
        )
        out += page["Items"]
        token = page.get("LastEvaluatedKey")
        if not token:
            return out


def put_all(client, name, items):
    for i in range(0, len(items), 25):
        pending = {name: [{"PutRequest": {"Item": it}} for it in items[i:i + 25]]}
        while pending:
            pending = client.batch_write_item(RequestItems=pending).get("UnprocessedItems") or {}
        if (i // 25) % 40 == 0:
            print(f"  {min(i + 25, len(items))}/{len(items)}")


def seed_dev(args):
    """prod 수집 결과를 dev로 복사한다. dev에서 23k를 새로 긁으면 몇 시간 + 소스 사이트 부하."""
    client = ddb()
    rows = [it for it in scan(client, table(args.source)) if not it["carNumber"]["S"].startswith("_")]
    if args.limit:
        rows = rows[:args.limit]
    kept = [{k: v for k, v in it.items() if k in CAR_FIELDS} for it in rows]
    detail = sum(1 for it in rows if "detail" in it)
    print(f"{table(args.source)} → {table(args.target)}: {len(kept)}건 (detail {detail})")
    print("제외: _ 접두 아이템(_control), 할당·업로드 필드")
    if not args.apply:
        return print("\ndry-run. 쓰려면 --apply")
    put_all(client, table(args.target), kept)
    print("완료. dev에서 assign-cars를 돌리면 새로 할당된다.")


def sheets_token(info):
    from google.auth.transport.requests import Request
    from google.oauth2 import service_account

    creds = service_account.Credentials.from_service_account_info(info, scopes=SCOPES)
    creds.refresh(Request())
    return creds.token


def sheets_call(method, token, sheet_id, rng, **kw):
    import requests

    url = f"https://sheets.googleapis.com/v4/spreadsheets/{sheet_id}/values/{requests.utils.quote(rng, safe='')}"
    res = getattr(requests, method)(url, headers={"Authorization": f"Bearer {token}"}, **kw)
    res.raise_for_status()
    return res.json()


def fill_users(args):
    """구 bcar-serverless 시트(Accounts)의 region·totalAmount를 앱 시트 C·D열로 옮긴다."""
    import requests

    env_text = open(args.accounts_env, encoding="utf-8").read()

    def ev(key):
        value = re.search(rf"^{key}=(.*)$", env_text, re.M)[1].strip()
        return (value[1:-1] if value[:1] in "\"'" else value).replace("\\n", "\n")

    legacy_token = sheets_token({
        "type": "service_account",
        "client_email": ev("GOOGLE_CLIENT_EMAIL"),
        "private_key": ev("GOOGLE_PRIVATE_KEY"),
        "token_uri": "https://oauth2.googleapis.com/token",
    })
    accounts = sheets_call("get", legacy_token, ev("GOOGLE_SPREAD_SHEET_ID"), "Accounts!A3:K").get("values", [])
    by_id = {r[1]: (r[3], r[4]) for r in accounts if len(r) >= 5}

    secret = json.loads(
        boto3.client("secretsmanager", region_name=REGION)
        .get_secret_value(SecretId=f"bcar-{args.env}")["SecretString"]
    )
    token = sheets_token(json.loads(base64.b64decode(secret["google.sa.json.base64"])))
    sheet_id = secret["google.sheets.id"]
    rows = sheets_call("get", token, sheet_id, f"{USER_SHEET}!A2:D").get("values", [])

    plan, missing = [], []
    for i, row in enumerate(rows, start=2):
        uid = row[0] if row else ""
        current = (row[2:4] + ["", ""])[:2]
        if uid in by_id:
            plan.append(list(by_id[uid]))
            print(f"row {i:>3} {uid:<14} {current} -> {list(by_id[uid])}")
        else:
            plan.append(current)
            missing.append(uid)
            print(f"row {i:>3} {uid:<14} {current}   (Accounts에 없음, 유지)")
    print(f"\nmatched {len(plan) - len(missing)}/{len(rows)}, 미매칭: {missing}")
    if not args.apply:
        return print("\ndry-run. 쓰려면 --apply")
    written = sheets_call(
        "put", token, sheet_id, f"{USER_SHEET}!C2:D{len(rows) + 1}",
        params={"valueInputOption": "RAW"}, json={"values": plan},
    )
    print("written:", written.get("updatedRange"), written.get("updatedCells"), "cells")


def control(args):
    """`_control` 아이템 조회/변경. stopDetail은 파이프라인 정지 스위치."""
    client, name = ddb(), table(args.env)
    key = {"carNumber": {"S": "_control"}}
    if args.stop_detail is None and not args.reset_chains:
        return print(json.dumps(client.get_item(TableName=name, Key=key).get("Item", {}), ensure_ascii=False, indent=2))
    if not args.apply:
        return print(f"dry-run: {name} _control stop-detail={args.stop_detail} reset-chains={args.reset_chains}. 쓰려면 --apply")
    if args.stop_detail == "on":
        client.update_item(TableName=name, Key=key, UpdateExpression="SET stopDetail = :t",
                           ExpressionAttributeValues={":t": {"BOOL": True}})
    elif args.stop_detail == "off":
        client.update_item(TableName=name, Key=key, UpdateExpression="REMOVE stopDetail")
    if args.reset_chains:
        client.update_item(TableName=name, Key=key, UpdateExpression="SET detailChainsDone = :z",
                           ExpressionAttributeValues={":z": {"N": "0"}})
    print(json.dumps(client.get_item(TableName=name, Key=key).get("Item", {}), ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser(prog="bcar-admin", description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    seed = sub.add_parser("seed-dev", help="prod cars를 dev로 복사 (draft+detail만)")
    seed.add_argument("--source", default="prod")
    seed.add_argument("--target", default="dev")
    seed.add_argument("--limit", type=int)
    seed.add_argument("--apply", action="store_true")
    seed.set_defaults(func=seed_dev)

    users = sub.add_parser("fill-users", help="구 Accounts 시트에서 targetSite·quota를 채운다")
    users.add_argument("--env", default="dev", choices=["dev", "prod"])
    users.add_argument("--accounts-env", required=True, help="구 bcar-serverless .env 경로")
    users.add_argument("--apply", action="store_true")
    users.set_defaults(func=fill_users)

    ctl = sub.add_parser("control", help="_control 아이템 조회/변경")
    ctl.add_argument("--env", default="dev", choices=["dev", "prod"])
    ctl.add_argument("--stop-detail", choices=["on", "off"])
    ctl.add_argument("--reset-chains", action="store_true")
    ctl.add_argument("--apply", action="store_true")
    ctl.set_defaults(func=control)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    sys.exit(main())
