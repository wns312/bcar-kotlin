variable "project_name" {
  type        = string
  description = "Project name used for naming resources."
  default     = "bcar"
}

variable "environment" {
  type        = string
  description = "Environment name (e.g., dev, prod)."
}

variable "region" {
  type        = string
  description = "AWS region for app resources."
  default     = "ap-northeast-2"
}

variable "vpc_cidr" {
  type        = string
  description = "CIDR block for the VPC."
  default     = "10.10.0.0/16"
}

variable "public_subnet_cidrs" {
  type        = list(string)
  description = "CIDR blocks for public subnets."
  default     = ["10.10.0.0/24", "10.10.1.0/24"]
}

variable "batch_max_vcpus" {
  type        = number
  description = "Maximum vCPUs for Batch compute environment."
  default     = 256
}
