import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/setup.ts',
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: 'http://127.0.0.1:5175', channel: 'chrome', trace: 'retain-on-failure' },
  projects: [
    { name: 'desktop-1366', use: { viewport: { width: 1366, height: 768 } } },
    { name: 'desktop-1920', use: { viewport: { width: 1920, height: 1080 } } },
  ],
  webServer: [
    { command: '../scripts/e2e-server.sh', url: 'http://127.0.0.1:8082/api/auth/csrf', timeout: 180000, reuseExistingServer: false },
    { command: 'npm run dev', env: { APARTMENT_BACKEND_PORT: '8082', APARTMENT_FRONTEND_PORT: '5175' }, url: 'http://127.0.0.1:5175', timeout: 30000, reuseExistingServer: false },
  ],
})
