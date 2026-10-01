#!/usr/bin/env bash
# JMH microbenchmarks for the statement read path (src/test/java/.../statement/perf/*Benchmark.java).
# Usage: perf/jmh.sh [benchmark regex]   e.g. perf/jmh.sh CacheEviction
set -euo pipefail
cd "$(dirname "$0")/../backend"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
./mvnw -q test-compile -DskipTests
./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/jmh.cp -Dmdep.includeScope=test
mkdir -p target/perf
"$JAVA_HOME/bin/java" -cp "target/test-classes:target/classes:$(cat target/jmh.cp)" org.openjdk.jmh.Main \
  "${1:-statement.perf.*Benchmark}" -rf text -rff target/perf/jmh-results.txt
