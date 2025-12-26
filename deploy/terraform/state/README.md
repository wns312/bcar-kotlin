# Terraform State Bootstrap

이 디렉토리는 Terraform state 저장소(S3 + DynamoDB 락 테이블)를 생성하는 초기화 용도입니다.
초기에는 **로컬 state**로 생성하고, 이후 **공유 S3 state**로 전환하는 절차를 전제로 합니다.

## Prerequisites
- AWS credentials configured (e.g., `aws configure`)
- Terraform >= 1.5

## Step 1. 로컬 state로 버킷/락 테이블 생성
1. Initialize
   ```bash
   terraform init
   ```

2. Review plan
   ```bash
   terraform plan \
     -var="project_name=bcar" \
     -var="region=ap-northeast-2" \
     -var="state_bucket_name=bcar-terraform-state"
   ```

3. Apply
   ```bash
   terraform apply \
     -var="project_name=bcar" \
     -var="region=ap-northeast-2" \
     -var="state_bucket_name=bcar-terraform-state"
   ```

## Step 2. 공유 state로 전환
버킷 생성 이후, 아래처럼 S3 backend 설정을 추가한 뒤 `terraform init -migrate-state`로 이전합니다.

예시 (backend 설정 파일):
```hcl
terraform {
  backend "s3" {
    bucket         = "bcar-terraform-state"
    key            = "terraform/state/bootstrap.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "bcar-terraform-lock"
    encrypt        = true
  }
}
```

적용:
```bash
terraform init -migrate-state
```

## Outputs
- `state_bucket_name`: S3 bucket for Terraform state
- `lock_table_name`: DynamoDB table for state locking

## Notes
- `state_bucket_name` must be globally unique.
- This stack should be applied once per account.
