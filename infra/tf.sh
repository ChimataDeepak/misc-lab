#!/usr/bin/env bash
# Terraform isn't installed locally; run it in a container. Usage: ./infra/tf.sh init|plan|apply ...
set -euo pipefail
cd "$(dirname "$0")/terraform"
exec docker run --rm --network host -v "$PWD/../..":/repo -w /repo/infra/terraform \
  -e AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}" -e AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}" \
  hashicorp/terraform:1.9 "$@"
