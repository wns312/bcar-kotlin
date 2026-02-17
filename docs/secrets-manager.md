# Secrets Manager 운영 가이드

Terraform은 Secrets Manager 리소스(시크릿 자체)만 관리하고, 값은 수동으로 운영합니다.

## 1) 시크릿 이름 규칙

- `bcar-dev`
- `bcar-prod`

## 2) 앱 로딩 방식

- `dev`: [`src/main/resources/application-dev.yaml`](../src/main/resources/application-dev.yaml)에서 `aws-secretsmanager:bcar-dev` import
- `prod`: [`src/main/resources/application-prod.yaml`](../src/main/resources/application-prod.yaml)에서 `aws-secretsmanager:bcar-prod` import
- `local`: [`src/main/resources/application-local.yaml`](../src/main/resources/application-local.yaml)로 로컬 오버라이드

## 3) 값 갱신 절차(콘솔)

1. AWS Console -> Secrets Manager 이동
2. 대상 시크릿 선택
3. `Retrieve secret value` -> `Edit`
4. JSON 값 저장(새 버전 생성)

## 4) 값 갱신 절차(CLI)

```bash
aws secretsmanager put-secret-value \
  --secret-id bcar-dev \
  --secret-string '{"key":"value"}'
```

## 5) 권한(최소 권한 기준)

- 앱 실행 Role: `secretsmanager:GetSecretValue`, `secretsmanager:DescribeSecret`
- 운영자(값 변경): `secretsmanager:PutSecretValue`

## 6) 운영 주의사항

- 값 변경 후 앱 재기동/재배포가 필요할 수 있음
- JSON key 이름은 앱의 프로퍼티 바인딩 이름과 일치해야 함

## 7) Google 서비스 계정 JSON을 base64로 변환

```bash
# Linux
base64 -w 0 sa.json > sa.json.b64

# macOS
base64 -i sa.json | tr -d '\n' > sa.json.b64

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("sa.json")) | Set-Content -NoNewline sa.json.b64
```
