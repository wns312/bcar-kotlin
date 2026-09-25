# /// script
# requires-python = ">=3.11"
# dependencies = ["boto3", "google-auth", "requests"]
# ///
"""bcar 운영 스크립트. 앱이 하지 않는 일회성 작업만 모은다.

  uv run tools/bcar_admin.py seed-dev            # prod cars → dev 복사 (dry-run)
  uv run tools/bcar_admin.py seed-dev --apply
  uv run tools/bcar_admin.py fill-users --env dev --accounts-env ~/path/.env
  uv run tools/bcar_admin.py site-status --env prod
  uv run tools/bcar_admin.py site-status --env prod --accounts-env ~/path/.env   # 아직 안 옮긴 계정
  uv run tools/bcar_admin.py control --env dev --stop-detail on

모든 쓰기 명령은 --apply 없이는 계획만 출력한다.
"""

import argparse
import base64
import datetime
import json
import re
import sys

import boto3

REGION = "ap-northeast-2"
USER_SHEET = "교차로계정정보"
SITE_SHEET = "사이트정보"
# 기본 UA로는 대상 사이트가 429를 준다
BROWSER_UA = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
)
# 구 bcar-serverless 시트 → 앱 시트. (읽을 범위, 새 탭, 헤더)
# Comment·Margin은 시트가 아니라 앱 리소스(upload-comment.txt, application.yaml)로 갔다
SHEET_COPIES = [
    ("URLs!A4:B", "사이트정보", ["targetSite", "baseUrl"]),
]
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
    import time

    import requests

    url = f"https://sheets.googleapis.com/v4/spreadsheets/{sheet_id}/values/{requests.utils.quote(rng, safe='')}"
    res = getattr(requests, method)(url, headers={"Authorization": f"Bearer {token}"}, **kw)
    res.raise_for_status()
    return res.json()


def sheet_titles(token, sheet_id):
    import requests

    res = requests.get(
        f"https://sheets.googleapis.com/v4/spreadsheets/{sheet_id}",
        headers={"Authorization": f"Bearer {token}"},
        params={"fields": "sheets.properties.title"},
    )
    res.raise_for_status()
    return [s["properties"]["title"] for s in res.json()["sheets"]]


def add_tabs(token, sheet_id, titles):
    import requests

    res = requests.post(
        f"https://sheets.googleapis.com/v4/spreadsheets/{sheet_id}:batchUpdate",
        headers={"Authorization": f"Bearer {token}"},
        json={"requests": [{"addSheet": {"properties": {"title": t}}} for t in titles]},
    )
    res.raise_for_status()


def legacy_sheet(env_path):
    """구 bcar-serverless .env에서 시트 자격증명을 꺼낸다."""
    env_text = open(env_path, encoding="utf-8").read()

    def ev(key):
        value = re.search(rf"^{key}=(.*)$", env_text, re.M)[1].strip()
        return (value[1:-1] if value[:1] in "\"'" else value).replace("\\n", "\n")

    token = sheets_token({
        "type": "service_account",
        "client_email": ev("GOOGLE_CLIENT_EMAIL"),
        "private_key": ev("GOOGLE_PRIVATE_KEY"),
        "token_uri": "https://oauth2.googleapis.com/token",
    })
    return token, ev("GOOGLE_SPREAD_SHEET_ID")


def app_sheet(env):
    secret = json.loads(
        boto3.client("secretsmanager", region_name=REGION)
        .get_secret_value(SecretId=f"bcar-{env}")["SecretString"]
    )
    return sheets_token(json.loads(base64.b64decode(secret["google.sa.json.base64"]))), secret["google.sheets.id"]


def copy_sheets(args):
    """구 시트의 URLs·Comment·Margin을 앱 시트로 옮긴다. 업로드 잡이 URL·마진·설명을 여기서 읽는다."""
    legacy_token, legacy_id = legacy_sheet(args.accounts_env)
    token, sheet_id = app_sheet(args.env)
    titles = sheet_titles(token, sheet_id)

    plan = []
    for source_range, tab, header in SHEET_COPIES:
        rows = sheets_call("get", legacy_token, legacy_id, source_range).get("values", [])
        values = ([header] + rows) if header else rows
        print(f"{source_range} -> {tab}!A1  {len(rows)}행" + ("" if tab in titles else "  (탭 생성 필요)"))
        for row in values[:4]:
            print("   ", row)
        if len(values) > 4:
            print(f"    … {len(values) - 4}행 더")
        plan.append((tab, values))

    if not args.apply:
        return print("\ndry-run. 쓰려면 --apply")

    missing = [tab for tab, _ in plan if tab not in titles]
    if missing:
        add_tabs(token, sheet_id, missing)
        print("탭 생성:", missing)
    for tab, values in plan:
        written = sheets_call(
            "put", token, sheet_id, f"{tab}!A1",
            params={"valueInputOption": "RAW"}, json={"values": values},
        )
        print("written:", written.get("updatedRange"), written.get("updatedCells"), "cells")


def fill_users(args):
    """구 bcar-serverless 시트(Accounts)의 region·totalAmount를 앱 시트 C·D열로 옮긴다."""
    legacy_token, legacy_id = legacy_sheet(args.accounts_env)
    accounts = sheets_call("get", legacy_token, legacy_id, "Accounts!A3:K").get("values", [])
    by_id = {r[1]: (r[3], r[4]) for r in accounts if len(r) >= 5}

    token, sheet_id = app_sheet(args.env)
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


def site_status(args):
    """계정마다 대상 사이트의 무료 한도·진행 매물·포인트를 읽는다. 사이트만 읽고 아무것도 쓰지 않는다.

    업로드 뒤 확인용: 진행 매물이 quota와 맞는지, 포인트가 줄지 않았는지(줄었으면 유료로 올라간 것).
    `진행 - 사용중`은 건수에서 빠지는 유료광고 매물 수다.
    """
    import requests

    token, sheet_id = app_sheet(args.env)
    base_urls = dict(sheets_call("get", token, sheet_id, f"{SITE_SHEET}!A2:B").get("values", []))
    if args.accounts_env:
        # 앱 시트로 옮기기 전에 봐야 하는 계정은 구 Accounts 시트에만 있다. ID·PW·지역·전체가 B~E열
        legacy_token, legacy_id = legacy_sheet(args.accounts_env)
        users = [r[1:5] for r in sheets_call("get", legacy_token, legacy_id, "Accounts!A4:E").get("values", [])]
    else:
        users = sheets_call("get", token, sheet_id, f"{USER_SHEET}!A2:D").get("values", [])

    for uid, password, site, quota in (row[:4] for row in users if len(row) >= 4):
        if site not in base_urls:
            print(f"{uid:<14} {site:<4} 사이트정보에 baseUrl 없음 — 건너뜀")
            continue
        base = base_urls[site]
        manage = f"https://car.{base}/my/car"
        session = requests.Session()
        session.headers["User-Agent"] = BROWSER_UA
        session.get(f"https://ssl.{base}/membership/login?url={manage}", timeout=30)
        session.post(f"https://ssl.{base}/membership/login",
                     data={"id": uid, "passwd": password}, timeout=30)
        html = session.get(manage, timeout=30).text
        text = re.sub(r"\s+", " ", re.sub(r"(?s)<[^>]+>", " ", re.sub(r"(?is)<script.*?</script>", " ", html)))

        used = re.search(r"제외\) ([\d,]+) 건 사용중 ([\d,]+) 건", text)
        listed = re.search(r"진행 \(([\d,]+)\) 마감 \(([\d,]+)\)", text)
        point = re.search(r"포인트 ([\d,]+) P", text)
        if not used:
            # 계정을 쉬지 않고 훑으면 대상 사이트가 이 IP를 막는다 (2026-09-25: 무지연 43계정 중 15번째부터 429).
            # 막힌 채로 계속 두드려봐야 차단만 깊어진다
            if "IP 접근이 차단" in text:
                sys.exit(f"{uid:<14} {site:<4} IP 차단(429). 잠시 뒤 --delay를 늘려 다시 돌려라")
            print(f"{uid:<14} {site:<4} 읽기 실패 (로그인 실패 또는 페이지 변경)")
            continue
        free_used, free_limit = used.group(1), used.group(2)
        progress = listed.group(1) if listed else "?"
        paid = int(progress) - int(free_used) if listed else "?"
        flag = "" if progress == quota else f"  ← quota {quota}와 불일치"
        print(f"{uid:<14} {site:<4} 진행 {progress:>4} (유료 {paid}) | 무료 {free_used}/{free_limit} | "
              f"마감 {listed.group(2) if listed else '?'} | 포인트 {point.group(1) if point else '?'}P{flag}")
        time.sleep(args.delay)


def reset_detail(args):
    """조건에 맞는 차량의 detail을 비워 상세를 다시 긁게 한다.

    상세 파서 규칙이 바뀌어도 이미 detail이 있는 차는 `collect-detail`이 건드리지 않는다
    (대상은 `isActive && detail == null`). 새 규칙을 소급 적용하려면 여기서 비워야 한다.
    """
    client, name = ddb(), table(args.env)
    min_year = datetime.date.today().year - args.years
    hit = []
    for it in scan(client, name):
        if it["carNumber"]["S"].startswith("_") or "detail" not in it:
            continue
        if not it.get("isActive", {}).get("BOOL"):
            continue
        year = (it["detail"].get("M", {}).get("modelYear", {}).get("S") or "")[:4]
        if not year.isdigit() or int(year) < min_year:
            continue
        if int(it["price"]["N"]) >= args.max_price:
            continue
        hit.append(it)

    print(f"{name}: 최근 {args.years}년({min_year}년 이후) 연식 & {args.max_price}만원 미만인 활성 차량 {len(hit)}건")
    for it in sorted(hit, key=lambda i: int(i["price"]["N"]))[:10]:
        print(f'  {it["price"]["N"]:>5}만 {it["carNumber"]["S"]:<10} '
              f'{it.get("uploadStatus", {}).get("S", "-"):<13} {it.get("title", {}).get("S", "")[:40]}')
    if len(hit) > 10:
        print(f"  … {len(hit) - 10}건 더")
    if not args.apply:
        return print("\ndry-run. 쓰려면 --apply")

    for i, it in enumerate(hit, 1):
        client.update_item(TableName=name, Key={"carNumber": it["carNumber"]}, UpdateExpression="REMOVE detail")
        if i % 20 == 0:
            print(f"  {i}/{len(hit)}")
    print(f"완료 {len(hit)}건. 다음 collect-detail이 다시 긁고, 승계 매물이면 내려간다.")


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

    copy = sub.add_parser("copy-sheets", help="구 URLs·Comment·Margin 시트를 앱 시트로 복사")
    copy.add_argument("--env", default="dev", choices=["dev", "prod"])
    copy.add_argument("--accounts-env", required=True, help="구 bcar-serverless .env 경로")
    copy.add_argument("--apply", action="store_true")
    copy.set_defaults(func=copy_sheets)

    users = sub.add_parser("fill-users", help="구 Accounts 시트에서 targetSite·quota를 채운다")
    users.add_argument("--env", default="dev", choices=["dev", "prod"])
    users.add_argument("--accounts-env", required=True, help="구 bcar-serverless .env 경로")
    users.add_argument("--apply", action="store_true")
    users.set_defaults(func=fill_users)

    reset = sub.add_parser("reset-detail", help="조건에 맞는 차량의 detail을 비워 재수집시킨다")
    reset.add_argument("--env", default="dev", choices=["dev", "prod"])
    reset.add_argument("-n", "--years", type=int, default=6, help="최근 N년 이내 연식만 (기본 6 — 리스 계약 기간)")
    reset.add_argument("--max-price", type=int, default=500)
    reset.add_argument("--apply", action="store_true")
    reset.set_defaults(func=reset_detail)

    status = sub.add_parser("site-status", help="시트 계정의 사이트 한도·진행 매물·포인트 조회")
    status.add_argument("--env", default="dev", choices=["dev", "prod"])
    status.add_argument("--accounts-env", help="구 bcar-serverless .env 경로. 주면 앱 시트 대신 구 Accounts 시트 계정을 본다")
    status.add_argument("--delay", type=float, default=5.0, help="계정 사이 대기 초. 너무 빠르면 사이트가 IP를 막는다")
    status.set_defaults(func=site_status)

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
