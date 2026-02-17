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

## 4) `collect-draft` 실행 예시

### 후속 잡 제출 비활성화(로컬 검증용)

```bash
./gradlew bootRun --args="--job=collect-draft --next=false --spring.profiles.active=local"
```

### 후속 잡 제출 활성화(기본값)

```bash
./gradlew bootRun --args="--job=collect-draft --spring.profiles.active=dev"
```

`collect-draft` 성공 시 기본 체인에서 다음 잡(`collect-detail`) 제출 요청이 생성됩니다.
현재 `BatchJobSubmitter` 구현은 `LoggingBatchJobSubmitter`이므로 실제 외부 배치 제출 대신 로그를 남깁니다.

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
