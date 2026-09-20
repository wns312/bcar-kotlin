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
`collect-draft` 성공 시 `batch.detail-shards`개의 shard 체인이 시작된다. 이전 launch의 체인이 아직 도는 shard는 건너뛴다(잡 이름 접두사로 확인).

### 상세 수집 체인 정지
`cars` 테이블의 `_control` 아이템으로 모든 체인을 다음 hop에서 멈춘다:

```bash
aws dynamodb put-item --table-name bcar-dev-cars --item '{"carNumber":{"S":"_control"},"stopDetail":{"BOOL":true}}'
# 재개
aws dynamodb delete-item --table-name bcar-dev-cars --key '{"carNumber":{"S":"_control"}}'
```

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
