output "vpc_id" {
  description = "VPC ID."
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs."
  value       = aws_subnet.public[*].id
}

output "batch_security_group_id" {
  description = "Security group ID for Batch jobs."
  value       = aws_security_group.batch.id
}

output "ecr_repository_url" {
  description = "ECR repository URL."
  value       = aws_ecr_repository.app.repository_url
}

output "batch_compute_environment_arn" {
  description = "Batch compute environment ARN."
  value       = aws_batch_compute_environment.main.arn
}

output "batch_job_queue_arn" {
  description = "Batch job queue ARN."
  value       = aws_batch_job_queue.main.arn
}

output "batch_sync_and_upload_job_queue_arn" {
  description = "Batch sync-and-upload job queue ARN."
  value       = aws_batch_job_queue.sync_and_upload.arn
}

output "batch_job_definition_arn" {
  description = "Batch job definition ARN."
  value       = aws_batch_job_definition.main.arn
}

output "batch_log_group_name" {
  description = "CloudWatch log group name for Batch."
  value       = aws_cloudwatch_log_group.batch.name
}
