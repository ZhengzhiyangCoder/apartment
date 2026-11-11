#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY'
from pathlib import Path
import secrets, os
p = Path('deploy/.env')
if p.exists():
    print('deploy/.env already exists; kept unchanged.')
else:
    fd = os.open(p, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w', encoding='utf-8') as f:
        f.write(f'APARTMENT_DB_ROOT_PASSWORD={secrets.token_hex(24)}\n')
        f.write(f'APARTMENT_DB_PASSWORD={secrets.token_hex(24)}\n')
        f.write('APARTMENT_DB_PORT=3308\nAPARTMENT_BACKEND_PORT=8081\nAPARTMENT_FRONTEND_PORT=5174\n')
    print('Generated deploy/.env with private project credentials.')
PY
