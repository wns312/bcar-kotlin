terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Shared state example (enable after bootstrap):
  backend "s3" {
    bucket         = "bcar-terraform-state"
    key            = "terraform/state/bootstrap.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "bcar-terraform-lock"
    encrypt        = true
  }
}

provider "aws" {
  region = var.region
}
