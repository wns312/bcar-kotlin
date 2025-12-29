# bcar-kotlin

개인용 자동화 배치 앱.

## Terraform 사용법
상세 내용은 `deploy/terraform/app/README.md` 참고.

## Secrets Manager 수동 운영
Terraform은 Secrets Manager의 "껍데기"만 관리하고 값은 수동으로 갱신한다.
앱은 `spring.config.import`로 시크릿을 로딩하며, 로컬은 `application-local.yaml`로 오버라이드한다.

### 시크릿 이름 규칙
- `bcar-dev`
- `bcar-prod`

### 값 갱신 절차
1. AWS Console에서 Secrets Manager로 이동
2. 대상 시크릿 선택 후 "Retrieve secret value" -> "Edit"
3. JSON 형식으로 값 입력 후 저장(새 버전 생성)

### CLI로 값 갱신(옵션)
```bash
aws secretsmanager put-secret-value \
  --secret-id bcar-dev \
  --secret-string '{"key":"value"}'
```

### 권한(최소 권한 원칙)
- 읽기 권한(앱 실행 Role): `secretsmanager:GetSecretValue`, `secretsmanager:DescribeSecret`
- 쓰기 권한(운영자만): `secretsmanager:PutSecretValue`

### 운영 주의사항
- 값 변경 후 앱 재기동/재배포가 필요할 수 있음
- JSON 키는 앱 프로퍼티 바인딩과 동일한 이름을 사용

## 앱 사용법
- 로컬 빌드:
  - `./gradlew clean bootJar`
- 도커 빌드/실행:
  - `docker build -t bcar-kotlin:local .`
  - `docker run --rm bcar-kotlin:local --job=collect-draft --next=true`
- 인자:
  - `--job`: 실행할 잡 이름 (예: `collect-draft`)
  - `--next`: 후속 잡 제출 여부 (`true`/`false`, 기본 `true`)
- 로그 확인:
  - 잡 시작/완료 로그가 출력되는지 확인

## Playwright 준비
- 브라우저 바이너리 설치: `./gradlew playwrightInstall`
- 설정: `src/main/resources/application.yaml`의 `automation.playwright.*`로 브라우저/헤드리스/타임아웃 조정
- 로컬에서 헤드리스 해제: `application-local.yaml`에 `automation.playwright.headless=false` (활성화: `SPRING_PROFILES_ACTIVE=local` 또는 `--spring.profiles.active=local`)
- 컨테이너 실행 시 Playwright 브라우저가 포함된 이미지 사용 권장(필요하면 Dockerfile 조정)

## 워크플로우 실행 준비
이 레포는 GitHub Actions로 빌드/푸시합니다.

### 필요한 Secrets
공통:
- `ECR_REGISTRY`: 예) `123456789012.dkr.ecr.ap-northeast-2.amazonaws.com`
- `ECR_REPOSITORY`: 예) `bcar`

푸시 워크플로우(dev/prod 추가 필요):
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_REGION`

### 트리거
- `dev-build`: main 이외 브랜치 push 시 빌드만 수행
- `dev-push`: 수동 실행(`workflow_dispatch`) 시 빌드 + ECR push
- `prod-build-push`: main push 시 빌드 + ECR push

### 실행 순서(요약)
1. GitHub Secrets 등록
2. 필요 시 GitHub Environments(`dev`, `prod`)에 동일 Secrets 등록
3. 워크플로우 트리거에 맞게 push 또는 수동 실행

### IAM 권한(요약)
- GitHub Actions에서 ECR 푸시를 하려면 `ecr:*` 중 로그인/푸시에 필요한 권한이 있어야 합니다.
- Terraform 적용에는 VPC/ECR/Batch/CloudWatch Logs 관련 권한이 필요합니다.
- 최소 권한 정책은 환경에 따라 달라지므로, 실제로 사용한 권한을 기준으로 축소하세요.

### IAM 정책 예시(최소)
GitHub Actions(ECR 푸시용):
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "EcrAuth",
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken"
      ],
      "Resource": "*"
    },
    {
      "Sid": "EcrPush",
      "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload",
        "ecr:PutImage"
      ],
      "Resource": "arn:aws:ecr:<region>:<account-id>:repository/<repo>"
    }
  ]
}
```

Terraform 적용용(요약):
- VPC/Subnet/IGW/RouteTable, ECR, Batch, IAM Role, CloudWatch Logs 관련 권한 필요
- 개인 프로젝트라면 AdministratorAccess로 시작 후 실제 사용 권한을 기준으로 축소하는 방식 권장

### ECR 리포지토리
- 기본적으로 Terraform이 ECR을 생성합니다.
- Terraform을 적용하지 않았다면 먼저 ECR 리포지토리를 수동 생성해야 합니다.

### Terraform 변수
- 상세 변수는 `deploy/terraform/app/README.md` 참고

### 성공 기준(간단)
- Actions에서 `Docker Push` 단계가 성공
- 컨테이너 실행 시 `Collecting draft ids.` / `Job 'collect-draft' completed` 로그 출력

### 배치 실행 절차(요약)
1. Terraform 적용 후 ECR 리포지토리/Batch 리소스 생성 확인
2. GitHub Actions로 이미지 빌드 및 ECR 푸시
3. Batch Job Definition이 `latest`를 사용 중인지 확인
4. Batch Job 실행(`collect-draft`) 후 CloudWatch Logs에서 잡 로그 확인

---
## Google 서비스계정 json을 base64 문자열로 변환

```shell
# Linux
base64 -w 0 sa.json > sa.json.b64
# Mac OS
base64 -i sa.json | tr -d '\n' > sa.json.b64
# Windows
[Convert]::ToBase64String([IO.File]::ReadAllBytes("sa.json")) | Set-Content -NoNewline sa.json.b64
```