import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

export default function setup() {
  execFileSync(fileURLToPath(new URL('../../scripts/seed-demo.sh', import.meta.url)), ['--test-reset'], { stdio: 'inherit' })
}
