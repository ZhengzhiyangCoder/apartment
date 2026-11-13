#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
source deploy/.env
test_url="jdbc:mysql://127.0.0.1:${APARTMENT_DB_PORT:-3308}/apartment_test?connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&sslMode=DISABLED"
# Command-line properties override all later environment loading in backend.sh.
exec ./scripts/backend.sh "-Dspring-boot.run.arguments=--server.port=8082 --spring.datasource.url=$test_url --apartment.storage.root=../.runtime/uploads/test-browser" spring-boot:run
