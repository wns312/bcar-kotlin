locals {
  name_prefix = "${var.project_name}-${var.environment}"
  tags = {
    Project     = var.project_name
    Environment = var.environment
    Managed     = "terraform"
  }
}

data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(local.tags, { Name = "${local.name_prefix}-vpc" })
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = merge(local.tags, { Name = "${local.name_prefix}-igw" })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  tags = merge(local.tags, { Name = "${local.name_prefix}-public-rt" })
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.main.id
}

resource "aws_subnet" "public" {
  count                   = length(var.public_subnet_cidrs)
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = merge(local.tags, { Name = "${local.name_prefix}-public-${count.index + 1}" })
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "batch" {
  name        = "${local.name_prefix}-batch-sg"
  description = "Security group for AWS Batch jobs."
  vpc_id      = aws_vpc.main.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.tags
}

resource "aws_ecr_repository" "app" {
  name                 = local.name_prefix
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = local.tags
}

resource "aws_cloudwatch_log_group" "batch" {
  name              = "/aws/batch/${local.name_prefix}"
  retention_in_days = 14

  tags = local.tags
}

resource "aws_secretsmanager_secret" "app_sensitive" {
  name        = local.name_prefix
  description = "Application config secrets for ${local.name_prefix}."

  tags = local.tags
}

resource "aws_iam_role" "batch_service" {
  name = "${local.name_prefix}-batch-service-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "batch.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "batch_service" {
  role       = aws_iam_role.batch_service.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSBatchServiceRole"
}

resource "aws_iam_role" "batch_execution" {
  name = "${local.name_prefix}-batch-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "batch_execution" {
  role       = aws_iam_role.batch_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role" "batch_job" {
  name = "${local.name_prefix}-batch-job-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })

  tags = local.tags
}

resource "aws_iam_role_policy" "batch_job_secrets_read" {
  name = "${local.name_prefix}-batch-job-secrets-read"
  role = aws_iam_role.batch_job.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:DescribeSecret",
          "secretsmanager:GetSecretValue"
        ]
        Resource = aws_secretsmanager_secret.app_sensitive.arn
      }
    ]
  })
}

resource "aws_dynamodb_table" "cars" {
  name         = "${local.name_prefix}-cars"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "carNumber"

  attribute {
    name = "carNumber"
    type = "S"
  }

  attribute {
    name = "assignedUserId"
    type = "S"
  }

  # 희소 인덱스: 할당된 차량만 들어간다
  global_secondary_index {
    name            = "assignedUserId-index"
    hash_key        = "assignedUserId"
    projection_type = "ALL"
  }

  tags = local.tags
}

resource "aws_iam_role_policy" "batch_job_dynamodb" {
  name = "${local.name_prefix}-batch-job-dynamodb"
  role = aws_iam_role.batch_job.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "dynamodb:Scan",
          "dynamodb:Query",
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:BatchWriteItem"
        ]
        Resource = [
          aws_dynamodb_table.cars.arn,
          "${aws_dynamodb_table.cars.arn}/index/*"
        ]
      }
    ]
  })
}

# 잡이 후속 잡(assign-cars, collect-detail 체인)을 직접 제출한다
resource "aws_iam_role_policy" "batch_job_submit" {
  name = "${local.name_prefix}-batch-job-submit"
  role = aws_iam_role.batch_job.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = ["batch:SubmitJob"]
        # 리비전 없이 정의 이름으로 제출하면 IAM은 리비전 없는 ARN으로 평가한다
        Resource = [
          aws_batch_job_queue.main.arn,
          aws_batch_job_definition.main.arn_prefix,
          "${aws_batch_job_definition.main.arn_prefix}:*",
          aws_batch_job_queue.detail.arn,
          aws_batch_job_definition.detail.arn_prefix,
          "${aws_batch_job_definition.detail.arn_prefix}:*",
          aws_batch_job_queue.sync_and_upload.arn,
          aws_batch_job_definition.sync_upload.arn_prefix,
          "${aws_batch_job_definition.sync_upload.arn_prefix}:*"
        ]
      },
      {
        # 같은 shard 체인 중복 제출 방지용 조회. ListJobs는 리소스 단위 권한이 없다
        Effect   = "Allow"
        Action   = ["batch:ListJobs"]
        Resource = "*"
      }
    ]
  })
}

resource "aws_batch_compute_environment" "main" {
  compute_environment_name = "${local.name_prefix}-ce"
  type                     = "MANAGED"
  service_role             = aws_iam_role.batch_service.arn

  compute_resources {
    type               = "FARGATE"
    max_vcpus          = var.batch_max_vcpus
    subnets            = aws_subnet.public[*].id
    security_group_ids = [aws_security_group.batch.id]
  }

  tags = local.tags
}

resource "aws_batch_job_queue" "main" {
  name     = "${local.name_prefix}-queue"
  state    = "ENABLED"
  priority = 1

  compute_environment_order {
    order               = 1
    compute_environment = aws_batch_compute_environment.main.arn
  }

  tags = local.tags
}

resource "aws_batch_job_queue" "sync_and_upload" {
  name     = "${local.name_prefix}-sync-and-upload-queue"
  state    = "ENABLED"
  priority = 1

  compute_environment_order {
    order               = 1
    compute_environment = aws_batch_compute_environment.main.arn
  }

  tags = local.tags
}

# detail 수집 전용. max_vcpus가 곧 동시 실행 자식 잡 수(자식 1vCPU) — 소스 서버 부하/IP 차단 조절 노브
resource "aws_batch_compute_environment" "detail" {
  compute_environment_name = "${local.name_prefix}-detail-ce"
  type                     = "MANAGED"
  service_role             = aws_iam_role.batch_service.arn

  compute_resources {
    type               = "FARGATE"
    max_vcpus          = var.batch_detail_max_vcpus
    subnets            = aws_subnet.public[*].id
    security_group_ids = [aws_security_group.batch.id]
  }

  tags = local.tags
}

resource "aws_batch_job_queue" "detail" {
  name     = "${local.name_prefix}-detail-queue"
  state    = "ENABLED"
  priority = 1

  compute_environment_order {
    order               = 1
    compute_environment = aws_batch_compute_environment.detail.arn
  }

  tags = local.tags
}

locals {
  container_properties = {
    image            = "${aws_ecr_repository.app.repository_url}:latest"
    executionRoleArn = aws_iam_role.batch_execution.arn
    jobRoleArn       = aws_iam_role.batch_job.arn
    environment = [
      {
        name  = "SPRING_PROFILES_ACTIVE"
        value = var.environment
      }
    ]
    resourceRequirements = [
      {
        type  = "VCPU"
        value = "1"
      },
      {
        type  = "MEMORY"
        value = "2048"
      }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.batch.name
        "awslogs-region"        = var.region
        "awslogs-stream-prefix" = local.name_prefix
      }
    }
    networkConfiguration = {
      assignPublicIp = "ENABLED"
    }
  }
}

resource "aws_batch_job_definition" "main" {
  name = "${local.name_prefix}-job"
  type = "container"

  platform_capabilities = ["FARGATE"]

  container_properties = jsonencode(merge(local.container_properties, {
    command = ["--job=collect-draft", "--next=true"]
  }))

  retry_strategy {
    attempts = 3
  }

  timeout {
    attempt_duration_seconds = 900
  }

  tags = local.tags
}

# 분류 트리 수집. 파이프라인 밖에서 가끔 수동/스케줄로 돈다
resource "aws_batch_job_definition" "category" {
  name = "${local.name_prefix}-category-job"
  type = "container"

  platform_capabilities = ["FARGATE"]

  container_properties = jsonencode(merge(local.container_properties, {
    command = ["--job=collect-category", "--next=false"]
  }))

  retry_strategy {
    attempts = 2
  }

  timeout {
    attempt_duration_seconds = 1800
  }

  tags = local.tags
}

# 유저 한 명 = 잡 하나. 다음 유저는 잡이 직접 제출한다 — 대상 사이트에 동시에 붙지 않게
resource "aws_batch_job_definition" "sync_upload" {
  name = "${local.name_prefix}-sync-upload-job"
  type = "container"

  platform_capabilities = ["FARGATE"]

  container_properties = jsonencode(merge(local.container_properties, {
    command = ["--job=sync-upload", "--next=false"]
  }))

  # 재시도 금지 — 후속 유저를 제출한 잡이 다시 돌면 체인이 두 갈래가 된다. 실패한 유저는 다음 launch에서 다시 맞춘다
  retry_strategy {
    attempts = 1
  }

  # 빈 계정을 quota까지 채우는 첫 실행이 200대에 33분 걸린다(정기 라운드는 3~8분).
  # 재시도가 없으니 타임아웃에 걸리면 그 유저는 다음 launch까지 밀린다
  timeout {
    attempt_duration_seconds = 7200
  }

  tags = local.tags
}

# 잡 하나 = IP 하나 분량(~15건). 차단되면 잡이 스스로 같은 shard의 후속 잡을 제출하므로 retry는 인프라 장애용만
resource "aws_batch_job_definition" "detail" {
  name = "${local.name_prefix}-detail-job"
  type = "container"

  platform_capabilities = ["FARGATE"]

  container_properties = jsonencode(merge(local.container_properties, {
    command = ["--job=collect-detail", "--next=false", "--shards=1"]
  }))

  retry_strategy {
    attempts = 2
  }

  timeout {
    attempt_duration_seconds = 10800
  }

  tags = local.tags
}
