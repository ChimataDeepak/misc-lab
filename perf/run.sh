#!/usr/bin/env bash
# Runs the JMeter plan headless in Docker against the app on host port 8090; HTML report -> perf/report/
set -euo pipefail
cd "$(dirname "$0")"
rm -rf report results.jtl
docker run --rm -v "$PWD":/perf -w /perf justb4/jmeter:5.5 \
  -n -t receipts.jmx -l results.jtl -e -o report -Jhost="${HOST:-host.docker.internal}" -Jport="${PORT:-8090}"
echo "Report: $PWD/report/index.html"
