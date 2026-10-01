#!/usr/bin/env bash
# Load test + profile for GET /api/statements/{payerId}. Results table -> backend/target/perf/results-*.md
# Usage: perf/statement-bench.sh [scenarios] [extra JVM args]
#   perf/statement-bench.sh S0-baseline,S1-indexes,S2-fetchjoin,S4-cache
#   PERF_JFR=true perf/statement-bench.sh S1-indexes,S2-fetchjoin
#   perf/statement-bench.sh S4-cache "-Xmx256m -XX:+UseZGC -XX:+ZGenerational"
set -euo pipefail
cd "$(dirname "$0")/../backend"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-/var/run/docker.sock}"
./mvnw -q test -Dtest=StatementPerfBench -Dsurefire.failIfNoSpecifiedTests=false \
  -Dperf.scenarios="${1:-S0-baseline,S1-indexes,S2-fetchjoin,S4-cache}" -Dperf.jfr="${PERF_JFR:-false}" \
  -Dperf.concurrency="${PERF_CONCURRENCY:-32}" -Dperf.warmup="${PERF_WARMUP:-10}" -Dperf.seconds="${PERF_SECONDS:-30}" \
  -DargLine="${2:-}" -DtrimStackTrace=false
