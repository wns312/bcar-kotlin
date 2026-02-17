# CI/CD 및 이미지 푸시 가이드

이 레포는 GitHub Actions로 빌드/푸시합니다.

## 1) 필요한 GitHub Secrets

공통:
- `ECR_REGISTRY` (예: `123456789012.dkr.ecr.ap-northeast-2.amazonaws.com`)
- `ECR_REPOSITORY` (예: `bcar`)

푸시 워크플로우(dev/prod 공통):
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_REGION`

## 2) 트리거

- `dev-build`: main 이외 브랜치 push 시 빌드만 수행
- `dev-push`: 수동 실행(`workflow_dispatch`) 시 빌드 + ECR push
- `prod-build-push`: main push 시 빌드 + ECR push

## 3) 실행 순서(요약)

1. GitHub Secrets 등록
2. 필요 시 GitHub Environments(`dev`, `prod`)에 동일 Secrets 등록
3. 워크플로우 트리거에 맞게 push 또는 수동 실행

## 4) IAM 권한(요약)

- GitHub Actions의 ECR 푸시 권한이 필요
- Terraform 적용에는 VPC/ECR/Batch/CloudWatch Logs 관련 권한이 필요
- 실제 사용 권한 기준으로 최소 권한으로 축소 권장

## 5) IAM 정책 예시(ECR push)

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

## 6) 성공 기준

- Actions에서 `Docker Push` 단계 성공
- 컨테이너 실행 시 `Collecting draft ids.` / `Job 'collect-draft' completed` 로그 확인
