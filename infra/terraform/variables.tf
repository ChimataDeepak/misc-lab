variable "region" {
  type    = string
  default = "ap-south-1"
}
variable "use_localstack" {
  type    = bool
  default = true
}
variable "localstack_url" {
  type    = string
  default = "http://localhost:4566"
}
variable "bucket_name" {
  type    = string
  default = "misc-receipts"
}
variable "lambda_jar" {
  description = "Path to the shaded Lambda jar built by lambda/ (mvn package)"
  type        = string
  default     = "../../lambda/target/misc-lab-lambda-0.0.1-SNAPSHOT.jar"
}
variable "lambda_handler" {
  type    = string
  default = "com.deepak.misclab.lambda.ReceiptUploadedHandler::handleRequest"
}
