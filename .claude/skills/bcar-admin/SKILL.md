---
name: bcar-admin
description: bcar 운영 스크립트(tools/bcar_admin.py)로 dev 시드 데이터 복사, 유저 시트 targetSite·quota 채우기, 구 URLs 시트 복사, _control 플래그 조회/변경을 한다. "dev에 데이터 채워", "prod 데이터 dev로", "시드", "seed-dev", "유저 시트", "targetSite", "quota 채워", "시트 탭 복사", "사이트정보", "stopDetail", "체인 멈춰", "_control", "체인 카운터 리셋"을 언급하면 명시적으로 부르지 않아도 사용한다. 앱 잡(collect-draft/collect-detail/assign-cars) 실행은 이 스킬이 아니라 AWS Batch로 제출한다.
---

# bcar 운영 스크립트

`uv run tools/bcar_admin.py <명령>` — 레포 루트에서 실행한다. 의존성은 PEP 723 inline이라 설치 불필요.

**모든 쓰기는 `--apply` 없이는 계획만 출력한다. 먼저 dry-run을 돌려 사용자에게 보여주고, 승인받은 뒤 `--apply`를 붙인다.**

## seed-dev — prod 수집 결과를 dev로 복사

```bash
uv run tools/bcar_admin.py seed-dev                      # 건수 확인
uv run tools/bcar_admin.py seed-dev --apply              # 전체
uv run tools/bcar_admin.py seed-dev --apply --limit 2000 # 일부만
```

dev에서 상세를 새로 수집하면 몇 시간 걸리고 소스 사이트가 IP를 막는다. 검증 전에 이걸로 채운다.
draft + `detail`만 복사하고 할당·업로드 필드는 버린다 — dev에서 `assign-cars`를 돌려야 의미가 있다.
`_` 접두 아이템(`_control`)은 제외한다(`stopDetail`이 딸려오면 dev 체인이 조용히 멈춘다).

**dev의 기존 할당 상태를 덮어쓴다.** 실행 전 사용자에게 알린다.

## fill-users — 유저 시트 targetSite·quota 채우기

```bash
uv run tools/bcar_admin.py fill-users --env dev --accounts-env ~/workspace/my-projects/bcar-serverless/env/.env
```

구 bcar-serverless 시트 `Accounts`(id, pw, region, totalAmount)에서 **region → C열(targetSite), totalAmount → D열(quota)**를 옮긴다. A·B열은 건드리지 않고, `Accounts`에 없는 계정은 현재 값을 유지한다.

시트 행에 비밀번호가 있으므로 **출력에 행 전체를 찍지 않는다.** 스크립트는 id와 C·D열만 출력한다.

## copy-sheets — 구 시트 탭을 앱 시트로 복사

```bash
uv run tools/bcar_admin.py copy-sheets --env prod --accounts-env ~/workspace/my-projects/bcar-serverless/env/.env
```

구 시트 `URLs`→`사이트정보`(targetSite, baseUrl). 없는 탭은 만들고 A1부터 덮어쓴다.
구 `Comment`·`Margin`은 시트로 옮기지 않았다 — 각각 `src/main/resources/upload-comment.txt`와 `application.yaml`의 `upload.margins`에 있다.

dev·prod가 같은 스프레드시트를 보므로 한 번만 돌리면 된다. `사이트정보`의 targetSite는 `교차로계정정보` C열과 정확히 같아야 한다 — 업로드 잡이 이걸로 로그인 URL을 조립한다.

## control — `_control` 아이템

```bash
uv run tools/bcar_admin.py control --env dev                          # 현재 값 조회
uv run tools/bcar_admin.py control --env prod --stop-detail on --apply  # 파이프라인 정지
uv run tools/bcar_admin.py control --env prod --stop-detail off --apply # 재개
uv run tools/bcar_admin.py control --env dev --reset-chains --apply     # 체인 카운터 0
```

`stopDetail`은 detail 체인과 그 뒤 `assign-cars`까지 세운다. `detailChainsDone`은 끝난 체인 수이고 `collect-draft`가 리셋하므로, 수동 리셋은 draft 없이 detail만 돌려 볼 때만 쓴다.

## 하지 말 것

- prod 쓰기(`--env prod --apply`, `seed-dev --target prod`)는 사용자가 명시적으로 요청했을 때만
- 이 스크립트에 앱 로직을 넣지 말 것 — 일회성 운영 작업만 둔다
