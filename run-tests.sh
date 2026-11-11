#!/usr/bin/env bash
# Runs the same test commands as .github/workflows/ci.yml, locally.
#   ./run-tests.sh            all parts
#   ./run-tests.sh backend go one or more of: backend spark frontend angular python go
# backend needs Java 21 (virtual threads) and Docker for the Testcontainers ITs.
set -euo pipefail
cd "$(dirname "$0")"

if [ -S "$HOME/.rd/docker.sock" ] && [ -z "${DOCKER_HOST:-}" ]; then
  export DOCKER_HOST="unix://$HOME/.rd/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
fi

parts=("$@")
[ ${#parts[@]} -eq 0 ] && parts=(backend spark frontend angular python go)

for p in "${parts[@]}"; do
  echo "==> $p"
  case "$p" in
    backend)  (cd backend && ./mvnw -B verify) ;;
    spark)    (cd spark && ../backend/mvnw -B test) ;;
    frontend) (cd frontend && npm ci && npm test -- --run && npm run build) ;;
    angular)  (cd frontend-angular && npm ci && npm test) ;;
    python)   (cd python-svc && python3 -m pip install -q -e '.[dev]' && python3 -m pytest -q) ;;
    go)       (cd go-verify && go vet ./... && go test ./...) ;;
    *) echo "unknown part: $p" >&2; exit 2 ;;
  esac
done
echo "==> all requested parts passed"
