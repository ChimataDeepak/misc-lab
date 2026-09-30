output "bucket" { value = aws_s3_bucket.receipts.bucket }
output "queue_url" { value = aws_sqs_queue.events.url }
output "table" { value = aws_dynamodb_table.receipts.name }
output "lambda" { value = aws_lambda_function.on_receipt.function_name }
