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
  name                 = "${local.name_prefix}"
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

resource "aws_batch_job_definition" "main" {
  name = "${local.name_prefix}-job"
  type = "container"

  platform_capabilities = ["FARGATE"]

  container_properties = jsonencode({
    image            = "${aws_ecr_repository.app.repository_url}:latest"
    command          = ["--job=collect-draft", "--next=true"]
    executionRoleArn = aws_iam_role.batch_execution.arn
    jobRoleArn       = aws_iam_role.batch_job.arn
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
  })

  retry_strategy {
    attempts = 3
  }

  timeout {
    attempt_duration_seconds = 900
  }

  tags = local.tags
}
