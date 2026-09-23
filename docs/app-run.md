# 앱 실행 가이드

이 문서는 `bcar-kotlin` 앱 실행 방법과 실행 인자(`--job`, `--next`)를 정리합니다.

## 1) 실행 전 준비

1. Playwright 브라우저 설치

```bash
./gradlew playwrightInstall
```

2. 실행 프로파일 선택
- `local`: [`src/main/resources/application-local.yaml`](../src/main/resources/application-local.yaml) 사용
- `dev`: [`src/main/resources/application-dev.yaml`](../src/main/resources/application-dev.yaml) + AWS Secrets Manager(`bcar-dev`)
- `prod`: [`src/main/resources/application-prod.yaml`](../src/main/resources/application-prod.yaml) + AWS Secrets Manager(`bcar-prod`)

## 2) 실행 커맨드

### Gradle(개발 중 권장)

```bash
./gradlew bootRun --args="--job=collect-draft --next=false --spring.profiles.active=local"
```

### JAR 실행

```bash
./gradlew clean bootJar
java -jar build/libs/bcar-kotlin-0.0.1.jar --job=collect-draft --next=false --spring.profiles.active=local
```

### Docker 실행

```bash
docker build -t bcar-kotlin:local .
docker run --rm bcar-kotlin:local --job=collect-draft --next=false --spring.profiles.active=local
```

## 3) 실행 인자

### `--job`
- 설명: 실행할 잡 이름
- 필수 여부: 실행하려면 사실상 필수
- 값이 없으면: 앱은 실행 가능한 잡 목록만 로그로 출력하고 종료
- 지원 값(현재 코드 기준):
  - `collect-draft`
  - `collect-detail`
  - `assign-cars`
  - `sync-upload`
  - `collect-category`
- 잘못된 값이면: `Unknown job ...` 예외 발생

### `--next`
- 설명: 현재 잡 성공 후 후속 잡 제출 여부
- 필수 여부: 선택
- 기본값: `true`
- 지원 값: `true`, `false`
- 다른 문자열이면: `Invalid --next value ...` 예외 발생

### `collect-detail` 전용: `--shards`, `--shard`, `--hop`
- `--shards=N`: DynamoDB Scan을 N개 세그먼트로 나눔 (기본 1)
- `--shard=i`: 이 잡이 맡을 세그먼트 (기본: `AWS_BATCH_JOB_ARRAY_INDEX`, 없으면 0)
- `--hop=n`: 체인 몇 번째 잡인지 (기본 0). `batch.detail-max-hops` 초과 시 후속 제출 안 함
- `--idle=n`: 직전까지 연속 0건 hop 수 (체인이 자동으로 넘김). `batch.detail-max-idle-hops`(기본 5)에 도달하면 사이트 장애로 보고 종료

상세 페이지는 IP당 ~15건에서 차단되므로 잡 하나는 IP 하나 분량만 처리하고, 남은 차량이 있으면 같은 shard의 후속 잡을 제출한다 (`--next=true`일 때).

### `sync-upload` 전용: `--user`, `--delete`
- `--user=<id>`: 이 잡이 맡을 유저. 시트 `교차로계정정보`에 없는 id면 예외 — 시트에 없는 유저를 동기화하면 그 계정 매물을 통째로 지우게 된다
- `--delete=false`: 사이트 목록을 훑기만 하고 지우지 않는다 (기본 `true`). 로그인·페이지 순회·행 파싱만 확인할 때 쓴다

### 파이프라인

`collect-draft → collect-detail(shard 체인) → assign-cars → sync-upload(유저 체인)`

- `collect-draft` 성공 시 `batch.detail-shards`개의 shard 체인이 시작된다. 이전 launch의 체인이 아직 도는 shard는 건너뛴다(잡 이름 접두사로 확인).
- shard 체인이 끝나면(남은 차량 0 / hop·idle fuse) `_control.detailChainsDone`을 원자적으로 1 올린다. 그 값이 `shards`와 같아진 **마지막 체인만** `assign-cars`를 제출한다 — 먼저 끝난 체인이 제출하면 아직 수집 중인 shard의 결과가 빠진 채로 할당된다. 카운터는 `collect-draft`가 0으로 리셋한다.
- 잡이 실패해도 후속 제출은 한 번 계산된다 — 무엇을 이을지는 `JobChainDecider`가 정한다. `sync-upload`만 실패해도 다음 유저를 잇고(유저 하나 때문에 나머지가 멈추지 않게), 나머지 잡은 실패 시 아무것도 잇지 않는다. 실패는 Batch 잡 상태로 남는다.
- `sync-upload` 잡 정의는 재시도하지 않는다(`attempts=1`) — 후속 유저를 이미 제출한 잡이 다시 돌면 체인이 두 갈래가 된다. 실패한 유저는 다음 launch에서 다시 맞춰진다.
- `assign-cars`는 시트 유저 순서대로 `sync-upload` 체인을 시작한다. 유저 한 명이 끝나면 그 잡이 다음 유저를 제출한다 — 대상 사이트에 동시에 붙지 않게 한 번에 하나만 돈다. 체인이 이미 돌고 있으면 시작 잡은 제출되지 않는다(`sync-upload-` 접두사로 확인).
- `_control.stopDetail`은 detail뿐 아니라 파이프라인 전체를 세운다 — assign도 제출하지 않는다.

### `assign-cars`
시트 `교차로계정정보`(`A2:D` = id, password, targetSite, quota)의 유저마다 활성 차량을 `quota`까지 채운다. `targetSite`는 시트 `사이트정보`(`A2:B` = targetSite, baseUrl)에서 대상 사이트 주소로 바뀐다 — 매칭되는 행이 없으면 잡이 실패한다(건너뛰면 그 계정만 조용히 빠진다). 실패하지 않는다 — 못 채운 대수는 `shortfall`로만 보고.

계산은 `RatioAssignStrategy`(`AssignStrategy` 빈 교체 가능):
1. 유저별 카테고리 목표 = `quota × assign.ratio`. 카테고리는 `CarCategory.of` (수입 > 화물 > 트럭 > 국산 ≤1300 > 국산 >1300)
2. 카테고리별 미할당 공급 vs 미달 수요. 모자라면 유저 요구량 비례로 나눔
3. 비율 맞는 차가 들어와 quota를 넘기는 만큼만 초과 카테고리를 해제(안 올라간 것부터, 비싼 순). 폴백으로 채운 차는 대체 공급이 생길 때까지 유지 — 매 실행 재실행해도 변화 0. `UPLOADED` 해제는 `NEEDS_REMOVAL`로 표시만 하고 내릴 때까지 할당 정보 유지
4. 남은 자리는 `assign.fallback-order` 카테고리의 남은 공급(해제분 포함)으로 채움
5. 실제 차량은 싼 순으로 유저를 돌아가며 한 대씩

분류 불가(`CarCategory.of`가 null — 국산·수입 제조사 목록 어디에도 없음) 차량은 새로 할당하지 않고, 이미 할당돼 있으면 해제 1순위다.
시트에서 빠진 유저가 쥐고 있던 차량도 해제한다 — 아무도 손대지 않아 DB에 묶여 있게 되기 때문. 단 `UPLOADED`였던 차는 `NEEDS_REMOVAL`만 찍히고, 실제로 내리려면 그 계정이 시트로 돌아와야 한다.
`uploadStatus=UPLOADING`인 차량은 해제하지 않으며, 비울 수 없는 자리만큼 신규 유입도 줄인다 — 업로드 중에 목록이 바뀌지 않게.

새로 할당된 차량은 `assignedUserId`, `targetSite`, `assignedAt`, `uploadStatus=PENDING`이 찍힌다.
소스에서 사라졌거나 해제된 `UPLOADED` 차량은 `NEEDS_REMOVAL`로 표시된다. 아직 안 올라간 차가 소스에서 사라지면 그 자리에서 할당을 비운다(`Car.deactivate`).

### `collect-category`
대상 사이트 등록 폼의 분류 트리(세그먼트·제조사·모델·세부모델)를 훑어 cars 테이블 `_categories` 아이템에 JSON으로 저장한다. 폼이 이름이 아니라 `data-value`로 고르기 때문에 필요하다.

파이프라인에 끼우지 않는다 — 트리는 거의 안 바뀌는데 매 launch마다 돌면 업로드만 늦어진다. 1분 30초쯤 걸리고 결과는 71KB 남짓(아이템 한도 400KB).

등록 폼 진입에는 `products=car-normal-60`(기본등록) 파라미터가 필요하다. 없으면 광고상품 선택 페이지로 리다이렉트된다.

### `sync-upload`
유저 한 명의 매물을 대상 사이트와 맞춘다. **사이트가 원천이다** — 관리자가 손으로 올리거나 내린 것도 DB에 반영된다.

1. 로그인 → 관리 페이지를 **뒷 페이지부터** 순회(삭제 때문에 앞 페이지가 밀리지 않게)
2. 각 행의 차량번호가 이 유저 할당분(`NEEDS_REMOVAL` 제외)에 없으면 체크해서 일괄 삭제 — `NEEDS_REMOVAL` 내리기가 여기서 같이 처리된다
3. 결과를 `Car.syncedWith`로 반영: 사이트에 있으면 `UPLOADED`, 없으면 다시 올릴 대상(`PENDING`). `FAILED`는 그대로 두고(재시도 가드), `NEEDS_REMOVAL`은 `Car.release()`로 할당을 비운다
4. 업로드 패스(`PENDING`/`FAILED` 등록)는 아직 미구현

## 4) `collect-draft` 실행 예시

### 후속 잡 제출 비활성화(로컬 검증용)

```bash
./gradlew bootRun --args="--job=collect-draft --next=false --spring.profiles.active=local"
```

### 후속 잡 제출 활성화(기본값)

```bash
./gradlew bootRun --args="--job=collect-draft --spring.profiles.active=dev"
```

`collect-draft` 성공 시 `AwsBatchJobSubmitter`가 `batch.jobs.<job>` 설정의 큐/정의로 실제 Batch 잡을 제출합니다. 로컬에서 실수로 제출하지 않도록 `--next=false`를 권장합니다.

## 4-1) 운영 스크립트

앱이 하지 않는 일회성 작업은 [`tools/bcar_admin.py`](../tools/bcar_admin.py)에 있다. `uv run`이 의존성을 알아서 받으므로 설치가 필요 없고, 쓰기 명령은 `--apply` 없이는 계획만 출력한다.

```bash
uv run tools/bcar_admin.py seed-dev --apply            # prod 수집 결과를 dev로 복사
uv run tools/bcar_admin.py fill-users --env dev --accounts-env <구 .env 경로>
uv run tools/bcar_admin.py copy-sheets --env prod --accounts-env <구 .env 경로>
uv run tools/bcar_admin.py control --env dev           # _control 조회
```

dev에서 상세를 새로 긁으면 몇 시간이 걸리고 IP가 막히므로, 검증 전에는 `seed-dev`로 채운다(할당·업로드 필드는 복사하지 않는다).

## 5) 실행 확인 포인트

- `Collecting draft ids.` 로그 출력
- `Job 'collect-draft' completed: drafts collected` 로그 출력
- `--next=true`일 때 `Submitting job 'collect-detail' ...` 로그 출력

## 6) 자주 발생하는 실패 원인

- Playwright 미설치: 브라우저 실행 단계에서 실패
- 프로파일/시크릿 누락:
  - `local`에서 `google.sa.json.base64`, `google.sheets.id` 누락
  - `dev/prod`에서 AWS Secrets Manager 값 누락
- `--next` 오타: `true`/`false` 외 값 사용
