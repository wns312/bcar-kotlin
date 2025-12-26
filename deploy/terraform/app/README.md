# App Infrastructure (Terraform)

이 디렉토리는 애플리케이션 인프라(VPC, ECR, AWS Batch 등)를 관리합니다.
환경은 `environment` 변수로 구분합니다.

## Prerequisites
- Terraform >= 1.5
- AWS credentials configured
- state stack applied (S3 bucket + DynamoDB lock)

## Backend
`deploy/terraform/app/backend-*.hcl` 파일을 사용해 backend를 지정합니다.

```hcl
bucket         = "bcar-terraform-state"
key            = "terraform/bcar/app/dev.tfstate"
region         = "ap-northeast-2"
dynamodb_table = "bcar-terraform-lock"
encrypt        = true
```

## Usage
1. Initialize
   ```bash
   terraform init -backend-config=backend-dev.hcl
   ```

2. Review plan
   ```bash
   terraform plan \
     -var-file=env/dev.tfvars
    ```

3. Apply
   ```bash
   terraform apply \
     -var-file=env/dev.tfvars
    ```

## Environment Examples
- dev: `terraform init -backend-config=backend-dev.hcl` + `-var-file=env/dev.tfvars`
- prod: `terraform init -backend-config=backend-prod.hcl` + `-var-file=env/prod.tfvars`

## Variables
- `environment` (required): `dev` or `prod` (use `env/*.tfvars`)
- `project_name` (default: `bcar`, use `env/*.tfvars`)
- `region` (default: `ap-northeast-2`)
- `vpc_cidr` (default: `10.10.0.0/16`)
- `public_subnet_cidrs` (default: `10.10.0.0/24`, `10.10.1.0/24`)
- `batch_max_vcpus` (default: `256`)

## Notes
- Job Definition이 포함되어 있으며, 기본 이미지 태그는 `latest`입니다.
- 기본 구성은 public subnet 기반입니다. 필요 시 private subnet/NAT 구성으로 확장할 수 있습니다.
- prod 환경은 `terraform init -backend-config=backend-prod.hcl`로 초기화합니다.
