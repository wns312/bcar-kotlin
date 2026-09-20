# Terraform 운영 가이드

이 레포의 Terraform은 스택이 2개입니다.

- [`deploy/terraform/state`](../deploy/terraform/state): Terraform remote state(S3 + DynamoDB lock) bootstrap
- [`deploy/terraform/app`](../deploy/terraform/app): 애플리케이션 인프라(VPC, ECR, AWS Batch 등)

## 1) 권장 적용 순서

1. `state` 스택 1회 적용
2. `app` 스택을 환경별(`dev`, `prod`)로 적용

## 2) state 스택

상세 문서: [deploy/terraform/state/README.md](../deploy/terraform/state/README.md)

```bash
cd deploy/terraform/state
terraform init
terraform plan \
  -var="project_name=bcar" \
  -var="region=ap-northeast-2" \
  -var="state_bucket_name=bcar-terraform-state"
terraform apply \
  -var="project_name=bcar" \
  -var="region=ap-northeast-2" \
  -var="state_bucket_name=bcar-terraform-state"
```

## 3) app 스택

상세 문서: [deploy/terraform/app/README.md](../deploy/terraform/app/README.md)

### dev

```bash
cd deploy/terraform/app
terraform init -backend-config=backend-dev.hcl
terraform plan -var-file=env/dev.tfvars
terraform apply -var-file=env/dev.tfvars
```

### prod

```bash
cd deploy/terraform/app
terraform init -backend-config=backend-prod.hcl
terraform plan -var-file=env/prod.tfvars
terraform apply -var-file=env/prod.tfvars
```

## 4) GitHub Actions로 plan/apply (권장)

워크플로우: [`.github/workflows/terraform.yaml`](../.github/workflows/terraform.yaml)

- PR이 `deploy/terraform/app/**`를 건드리면 dev·prod **plan**이 자동으로 돈다 (fmt/validate 포함)
- apply는 Actions 탭 → Terraform → Run workflow → `environment`(dev/prod) + `action=apply`
- 러너는 매번 새로 init하므로 로컬에서 dev/prod state가 섞이는 실수(`-reconfigure` 누락)가 없다
- prod Environment에 required reviewer를 걸어두면 apply 전 승인 단계가 생긴다
- 필요한 Environment 시크릿은 CI/CD와 동일(`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`). 단 해당 IAM 유저에 Terraform이 만지는 리소스(VPC/ECR/Batch/IAM/CloudWatch Logs/DynamoDB/Secrets Manager)와 state 버킷·lock 테이블 권한이 있어야 한다

## 5) 로컬에서 직접 돌릴 때

같은 디렉터리에서 dev/prod를 오가므로 **환경 전환 시 반드시 `-reconfigure`**. init이 실패한 채 plan을 돌리면 직전 환경 state에 대해 plan이 나온다.

```bash
cd deploy/terraform/app
terraform init -reconfigure -backend-config=backend-dev.hcl && terraform plan -var-file=env/dev.tfvars
```

## 6) 운영 체크포인트

- backend 설정(`backend-*.hcl`)과 var 파일(`env/*.tfvars`) 환경 매칭 확인
- `state` 버킷/락 테이블은 계정당 1회 구성 원칙 유지
- `app` 적용 전 AWS 자격 증명/권한 확인
