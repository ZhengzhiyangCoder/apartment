import { test, expect, type Page } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

async function login(page: Page, username: string) {
  await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill('DemoPass2026!')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('link', { name: '个人资料', exact: true })).toBeVisible()
}
async function post(page: Page, path: string, data: object, status = 201) {
  const csrf = await (await page.request.get('/api/auth/csrf')).json()
  const response = await page.request.post(path, { headers: { [csrf.headerName]: csrf.token }, data })
  expect(response.status(), await response.text()).toBe(status); return response.json()
}
async function fixture(admin: Page, manager: Page) {
  await login(admin, 'system_admin'); await login(manager, 'dorm_manager')
  const room = (await (await admin.request.get('/api/housing/rooms?q=A101')).json()).items[0]
  const bed = (await (await admin.request.get(`/api/housing/beds?roomId=${room.id}`)).json()).items[0]
  const student = (await (await admin.request.get('/api/housing/students')).json()).items[0]
  await post(admin, '/api/housing/residencies', { studentId: student.id, bedId: bed.id })
  const scheme = await post(admin, '/api/hygiene/schemes', { name: '模拟整改临界标准', dimensions: [{ name: '地面', weight: 99 }, { name: '摆放', weight: 1 }] })
  await post(admin, `/api/hygiene/schemes/${scheme.id}/activate`, { version: 0, activeId: 0 }, 200)
  const batch = await post(manager, '/api/hygiene/batches/current/prepare', {}, 200)
  return { room, batch }
}
function moveWeek(id: number, monday: string) {
  expect(Number.isSafeInteger(id) && id > 0).toBeTruthy(); expect(['2026-10-26', '2026-11-02', '2026-11-09']).toContain(monday)
  const cwd = fileURLToPath(new URL('../..', import.meta.url))
  const args = ['compose', '--env-file', 'deploy/.env', '-f', 'deploy/compose.yaml', 'exec', '-T', 'mysql', 'sh', '-c', 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --skip-column-names --default-character-set=utf8mb4 -u apartment_app apartment_test']
  expect(execFileSync('docker', args, { cwd, input: 'SELECT DATABASE();', encoding: 'utf8' }).trim()).toBe('apartment_test')
  // Time-only fixture; all business writes use public HTTP.
  execFileSync('docker', args, { cwd, input: `UPDATE hygiene_batch SET iso_year=2026, iso_week=MOD(YEARWEEK('${monday}',3),100), week_start='${monday}' WHERE id=${id};`, stdio: ['pipe', 'pipe', 'pipe'] })
}
async function score(page: Page, roomId: number, batch: { id: number; dimensions: { id: number }[] }, first = 60, second = 59) {
  return post(page, `/api/hygiene/batches/${batch.id}/rooms/${roomId}/score`, { dimensions: batch.dimensions.map((d, i) => ({ dimensionId: d.id, score: i ? second : first })) })
}
test.beforeEach(() => { execFileSync(fileURLToPath(new URL('../../scripts/seed-demo.sh', import.meta.url)), ['--test-reset'], { stdio: 'pipe' }) })

test('正式录入59.99触发整改，跟进核实后关闭并保留原标准及日志', async ({ page, browser }) => {
  const context = await browser.newContext({ viewport: page.viewportSize()! }); const admin = await context.newPage()
  try {
    const f = await fixture(admin, page)
    await page.getByRole('link', { name: '卫生整改', exact: true }).click({ timeout: 5000 })
    await expect(page.getByText('暂无符合条件的整改事件。', { exact: true })).toBeVisible()
    await page.getByRole('link', { name: '本周卫生检查', exact: true }).click()
    await page.getByRole('row').filter({ hasText: 'A101' }).getByRole('button', { name: '录入评分', exact: true }).click()
    const entry = page.getByRole('dialog', { name: '寝室正式评分', exact: true })
    await entry.getByLabel('地面分值', { exact: true }).fill('60'); await entry.getByLabel('摆放分值', { exact: true }).fill('59')
    await expect(entry.getByTestId('score-preview')).toContainText('59.99')
    await entry.getByRole('button', { name: '提交正式评分', exact: true }).click()
    await expect(entry).not.toBeVisible(); await expect(page.getByRole('status')).toContainText('正式评分已保存：59.99分。')
    await page.getByRole('link', { name: '卫生整改', exact: true }).click()
    const table = page.getByRole('table', { name: '整改事件', exact: true })
    await expect(table).toContainText('A101'); await table.getByRole('button', { name: '查看与跟进', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '整改详情与处理', exact: true })
    await expect(dialog).toContainText('59.99'); await expect(dialog).toContainText('60.00'); await expect(dialog).toContainText('模拟整改临界标准')
    await dialog.getByRole('button', { name: '保存处理记录', exact: true }).click(); await expect(dialog.getByRole('alert')).toContainText('请填写处理说明')
    await dialog.getByLabel('处理说明', { exact: true }).fill('模拟已督促清洁地面与摆放')
    await dialog.getByRole('button', { name: '保存处理记录', exact: true }).click(); await expect(dialog.getByRole('status')).toContainText('已保存')
    await dialog.getByLabel('处理操作', { exact: true }).selectOption('verify')
    await dialog.getByLabel('处理说明', { exact: true }).fill('模拟现场核实整改完成')
    await dialog.getByRole('button', { name: '保存处理记录', exact: true }).click(); await expect(dialog).toContainText('已核实，可关闭')
    await dialog.getByLabel('处理操作', { exact: true }).selectOption('close')
    await dialog.getByLabel('处理说明', { exact: true }).fill('模拟核实合格，关闭本次整改')
    await dialog.getByRole('button', { name: '保存处理记录', exact: true }).click(); await expect(dialog.getByTestId('rectification-state')).toContainText('已关闭')
    await expect(dialog.getByRole('list', { name: '处理记录', exact: true })).toContainText('模拟已督促清洁地面与摆放')
    await dialog.getByText('整改详情与处理', { exact: true }).scrollIntoViewIfNeeded()
    await page.screenshot({ path: `../output/playwright/task13-closed-${page.viewportSize()!.width}.png`, fullPage: true, animations: 'disabled' })
    await dialog.getByRole('button', { name: '返回列表', exact: true }).click()
    await expect(page.getByText('暂无符合条件的整改事件。', { exact: true })).toBeVisible()
    await page.getByLabel('整改状态', { exact: true }).selectOption('CLOSED'); await page.getByRole('button', { name: '查询整改', exact: true }).click()
    await expect(table).toContainText('已关闭')
    await page.getByLabel('整改关键词', { exact: true }).fill('不存在'); await page.getByRole('button', { name: '查询整改', exact: true }).click()
    await expect(page.getByText('暂无符合条件的整改事件。', { exact: true })).toBeVisible()
    expect(f.room.id).toBeGreaterThan(0); expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  } finally { await context.close() }
})

test('网络失败和并发更新保留处理说明，显式载入新详情后继续', async ({ page, browser }) => {
  const context = await browser.newContext({ viewport: page.viewportSize()! }); const admin = await context.newPage()
  try {
    const f = await fixture(admin, page); await score(page, f.room.id, f.batch)
    await page.goto('/rectifications'); await page.getByRole('button', { name: '查看与跟进', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '整改详情与处理', exact: true })
    await dialog.getByLabel('处理说明', { exact: true }).fill('模拟网络恢复后仍保留的跟进说明')
    await page.route('**/api/hygiene/rectifications/*/follow-ups', route => route.abort())
    await dialog.getByRole('button', { name: '保存处理记录', exact: true }).click(); await expect(dialog.getByRole('alert')).toContainText('暂时无法连接服务')
    await expect(dialog.getByLabel('处理说明', { exact: true })).toHaveValue('模拟网络恢复后仍保留的跟进说明')
    await page.unroute('**/api/hygiene/rectifications/*/follow-ups')
    const event = (await (await admin.request.get('/api/hygiene/rectifications')).json()).items[0]
    await post(admin, `/api/hygiene/rectifications/${event.id}/follow-ups`, { version: event.version, note: '模拟另一管理人员先记录' }, 200)
    await dialog.getByRole('button', { name: '保存处理记录', exact: true }).click(); await expect(dialog.getByRole('alert')).toContainText('整改已被更新')
    await dialog.getByRole('button', { name: '重新载入详情', exact: true }).click(); await expect(dialog).toContainText('模拟另一管理人员先记录')
    await expect(dialog.getByLabel('处理说明', { exact: true })).toHaveValue('模拟网络恢复后仍保留的跟进说明')
    await dialog.getByRole('button', { name: '保存处理记录', exact: true }).click(); await expect(dialog.getByRole('status')).toContainText('已保存')
    await dialog.getByText('整改详情与处理', { exact: true }).scrollIntoViewIfNeeded()
    await page.screenshot({ path: `../output/playwright/task13-follow-up-${page.viewportSize()!.width}.png`, fullPage: true, animations: 'disabled' })
    await dialog.getByRole('button', { name: '返回列表', exact: true }).click()
    await page.route('**/api/hygiene/rectifications?*', route => route.abort())
    await page.getByRole('button', { name: '刷新整改', exact: true }).click(); await expect(page.getByRole('alert')).toBeVisible()
    await expect(page.getByRole('table', { name: '整改事件', exact: true })).toHaveCount(0)
    await page.unroute('**/api/hygiene/rectifications?*')
    await page.getByRole('button', { name: '重新加载列表', exact: true }).click(); await expect(page.getByRole('table', { name: '整改事件', exact: true })).toContainText('A101')
    let release!: () => void, captured!: () => void
    const held = new Promise<void>(resolve => { release = resolve }), ready = new Promise<void>(resolve => { captured = resolve })
    await page.route('**/api/hygiene/rectifications?*', async route => { const response = await route.fetch(); captured(); await held; await route.fulfill({ response }) }, { times: 1 })
    await page.getByRole('button', { name: '刷新整改', exact: true }).click(); await ready
    const latest = await (await admin.request.get(`/api/hygiene/rectifications/${event.id}`)).json()
    const verified = await post(admin, `/api/hygiene/rectifications/${event.id}/verify`, { version: latest.version, note: '模拟核实已完成' }, 200)
    await post(admin, `/api/hygiene/rectifications/${event.id}/close`, { version: verified.version, note: '模拟关闭' }, 200)
    await page.evaluate(() => window.dispatchEvent(new Event('focus')))
    await expect(page.getByText('暂无符合条件的整改事件。', { exact: true })).toBeVisible()
    release(); await page.unrouteAll({ behavior: 'wait' })
    await expect(page.getByText('暂无符合条件的整改事件。', { exact: true })).toBeVisible()
    await expect(page.getByText('第 1 / 1 页', { exact: true })).toBeVisible()
  } finally { await context.close() }
})

test('后续低分合并、合格不自动关闭，关闭后低分生成下一事件', async ({ page, browser }) => {
  const context = await browser.newContext({ viewport: page.viewportSize()! }); const manager = await context.newPage()
  try {
    const f = await fixture(page, manager); await score(manager, f.room.id, f.batch)
    moveWeek(f.batch.id, '2026-10-26')
    const w2 = await post(manager, '/api/hygiene/batches/current/prepare', {}, 200); await score(manager, f.room.id, w2, 0, 0); moveWeek(w2.id, '2026-11-02')
    const w3 = await post(manager, '/api/hygiene/batches/current/prepare', {}, 200); await score(manager, f.room.id, w3, 60, 60); moveWeek(w3.id, '2026-11-09')
    await page.goto('/rectifications'); await expect(page.getByText('共 1 条事件', { exact: false })).toBeVisible()
    await page.getByRole('button', { name: '查看与跟进', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '整改详情与处理', exact: true })
    await expect(dialog.getByRole('list', { name: '触发依据', exact: true }).getByRole('listitem')).toHaveCount(2)
    await expect(dialog.getByTestId('rectification-state')).toContainText('待整改')
    await dialog.getByLabel('处理操作', { exact: true }).selectOption('verify'); await dialog.getByLabel('处理说明', { exact: true }).fill('模拟核实后续已达标')
    await dialog.getByRole('button', { name: '保存处理记录', exact: true }).click(); await expect(dialog).toContainText('已核实，可关闭')
    await dialog.getByLabel('处理操作', { exact: true }).selectOption('close'); await dialog.getByLabel('处理说明', { exact: true }).fill('模拟人工关闭此前两次低分')
    await dialog.getByRole('button', { name: '保存处理记录', exact: true }).click(); await expect(dialog.getByTestId('rectification-state')).toContainText('已关闭')
    await dialog.getByRole('button', { name: '返回列表', exact: true }).click()
    const w4 = await post(manager, '/api/hygiene/batches/current/prepare', {}, 200); await score(manager, f.room.id, w4, 50, 50)
    await page.getByRole('button', { name: '刷新整改', exact: true }).click(); await expect(page.getByText('共 1 条事件', { exact: false })).toBeVisible()
    await page.getByLabel('整改状态', { exact: true }).selectOption(''); await page.getByRole('button', { name: '查询整改', exact: true }).click()
    const table = page.getByRole('table', { name: '整改事件', exact: true })
    await expect(table.getByRole('row')).toHaveCount(3); await expect(table).toContainText('已关闭'); await expect(table).toContainText('待整改')
    await page.evaluate(() => window.scrollTo(0, 0)); await page.screenshot({ path: `../output/playwright/task13-list-${page.viewportSize()!.width}.png`, fullPage: true, animations: 'disabled' })
    await page.getByRole('link', { name: '修改密码', exact: true }).click({ timeout: 5000 }); await expect(page).toHaveURL(/password/)
  } finally { await context.close() }
})

test('空整改列表及学生维修人员权限边界', async ({ page, browser }) => {
  await login(page, 'dorm_manager'); await page.goto('/rectifications')
  await expect(page.getByText('暂无符合条件的整改事件。', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '上一页', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '下一页', exact: true })).toBeDisabled()
  for (const username of ['student', 'maintainer']) {
    const context = await browser.newContext()
    try {
      const restricted = await context.newPage(); await login(restricted, username)
      await expect(restricted.getByRole('link', { name: '卫生整改', exact: true })).toHaveCount(0)
      expect((await restricted.request.get('/api/hygiene/rectifications')).status()).toBe(403)
      await restricted.goto('/rectifications'); await expect(restricted).toHaveURL(/forbidden/)
    } finally { await context.close() }
  }
})
