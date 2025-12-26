variable "project_name" {
  type        = string
  description = "Project name used for naming state resources."
  default     = "bcar"
}

variable "region" {
  type        = string
  description = "AWS region for the state resources."
  default     = "ap-northeast-2"
}

variable "state_bucket_name" {
  type        = string
  description = "S3 bucket name for Terraform state (must be globally unique)."
  default     = "bcar-terraform-state"
}
