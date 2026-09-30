terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.70" }
  }
}

provider "aws" {
  region                      = var.region
  skip_credentials_validation = var.use_localstack
  skip_requesting_account_id  = var.use_localstack
  skip_metadata_api_check     = var.use_localstack
  s3_use_path_style           = var.use_localstack

  dynamic "endpoints" {
    for_each = var.use_localstack ? [1] : []
    content {
      s3       = var.localstack_url
      sqs      = var.localstack_url
      dynamodb = var.localstack_url
      lambda   = var.localstack_url
      iam      = var.localstack_url
    }
  }
}

resource "aws_s3_bucket" "receipts" {
  bucket        = var.bucket_name
  force_destroy = true
}

resource "aws_sqs_queue" "events" {
  name = "receipt-events"
}

resource "aws_dynamodb_table" "receipts" {
  name         = "receipts"
  billing_mode = "PAY_PER_REQUEST" # free-tier friendly, no capacity planning
  hash_key     = "receiptId"
  attribute {
    name = "receiptId"
    type = "S"
  }
}

# --- Lambda triggered when an object lands in the bucket ---
resource "aws_iam_role" "lambda" {
  name = "misc-receipts-lambda"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = "sts:AssumeRole", Principal = { Service = "lambda.amazonaws.com" } }]
  })
}

resource "aws_iam_role_policy_attachment" "logs" {
  role       = aws_iam_role.lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_lambda_function" "on_receipt" {
  function_name    = "misc-receipt-on-upload"
  role             = aws_iam_role.lambda.arn
  runtime          = "java21"
  handler          = var.lambda_handler
  filename         = var.lambda_jar
  source_code_hash = filebase64sha256(var.lambda_jar)
  memory_size      = 512
  timeout          = 30
}

resource "aws_lambda_permission" "s3" {
  statement_id  = "AllowS3Invoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.on_receipt.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.receipts.arn
}

resource "aws_s3_bucket_notification" "on_upload" {
  bucket = aws_s3_bucket.receipts.id
  lambda_function {
    lambda_function_arn = aws_lambda_function.on_receipt.arn
    events              = ["s3:ObjectCreated:*"]
  }
  depends_on = [aws_lambda_permission.s3]
}
