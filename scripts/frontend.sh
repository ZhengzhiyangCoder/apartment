#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f deploy/.env ]]; then source deploy/.env; fi
export APARTMENT_BACKEND_PORT="${APARTMENT_BACKEND_PORT:-8081}"
export APARTMENT_FRONTEND_PORT="${APARTMENT_FRONTEND_PORT:-5174}"
cd frontend
exec npm run dev -- "$@"
