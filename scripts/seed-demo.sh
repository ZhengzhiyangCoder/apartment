#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
database=apartment_demo
reset=false
if [[ ${1:-} == --test-reset && $# -eq 1 ]]; then
  database=apartment_test
  reset=true
elif [[ $# -ne 0 ]]; then
  echo 'Usage: scripts/seed-demo.sh [--test-reset]' >&2
  exit 1
fi
{
  printf 'START TRANSACTION;\n'
  if [[ "$reset" == true ]]; then printf 'DELETE FROM operation_log;\nDELETE FROM housing_residency;\nDELETE FROM dorm_bed;\nDELETE FROM dorm_room;\nDELETE FROM dorm_building;\nDELETE FROM student_profile;\nDELETE FROM sys_user;\n'; fi
  cat backend/src/main/resources/db/demo/accounts.sql
  cat backend/src/main/resources/db/demo/housing.sql
  printf 'COMMIT;\n'
} | docker compose --env-file deploy/.env -f deploy/compose.yaml exec -T mysql \
  sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -u apartment_app "$1"' sh "$database"
echo "Simulated accounts and housing loaded into $database."
