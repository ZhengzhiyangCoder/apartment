#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ ! -f deploy/.env ]]; then
  echo 'Run scripts/setup-local.sh first.' >&2
  exit 1
fi
set -a
source deploy/.env
set +a
cd backend
if [[ $# -eq 0 ]]; then set -- spring-boot:run; fi
exec ./mvnw "$@"
