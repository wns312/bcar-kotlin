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

## 4) 운영 체크포인트

- backend 설정(`backend-*.hcl`)과 var 파일(`env/*.tfvars`) 환경 매칭 확인
- `state` 버킷/락 테이블은 계정당 1회 구성 원칙 유지
- `app` 적용 전 AWS 자격 증명/권한 확인
