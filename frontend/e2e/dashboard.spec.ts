import { test, expect, type Page } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

async function login(page: Page, username: string) {
  if ((await page.request.get('/api/auth/me')).status() === 200) {
    const token = await (await page.request.get('/api/auth/csrf')).json()
    await page.request.post('/api/auth/logout', { headers: { [token.headerName]: token.token } })
  }
  await page.goto('/login'); await page.getByLabel('账号', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill('DemoPass2026!')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: /工作台$/, level: 1 })).toBeVisible()
}
async function post(page: Page, path: string, data: object, status = 201) {
  const token = await (await page.request.get('/api/auth/csrf')).json()
  const response = await page.request.post(path, { headers: { [token.headerName]: token.token }, data })
  expect(response.status(), await response.text()).toBe(status); return response.json()
}
async function home(page: Page) {
  await page.getByRole('link', { name: '个人工作台', exact: true }).click()
  await expect(page.getByTestId('dashboard-content')).toBeVisible()
}
test.beforeEach(() => execFileSync(fileURLToPath(new URL('../../scripts/seed-demo.sh', import.meta.url)), ['--test-reset'], { stdio: 'pipe' }))

test('四角色空工作台只读加载，显示真实空态并可进入各自业务', async ({ page }) => {
  for (const role of ['system_admin', 'dorm_manager', 'student', 'maintainer']) {
    await login(page, role)
    await expect(page.getByTestId('dashboard-content')).toBeVisible()
    if (role === 'system_admin' || role === 'dorm_manager') {
      await expect(page.getByTestId('dashboard-inspection')).toContainText('本周检查尚未准备')
      expect((await (await page.request.get('/api/hygiene/batches')).json()).total).toBe(0)
      await page.getByRole('button', { name: '刷新工作台', exact: true }).click()
      await expect(page.getByTestId('dashboard-content')).toBeVisible()
      expect((await (await page.request.get('/api/hygiene/batches')).json()).total).toBe(0)
      await page.getByTestId('dashboard-inspection').getByRole('link').click()
      await expect(page.getByRole('heading', { name: '本周尚未准备检查' })).toBeVisible()
    } else if (role === 'student') {
      await expect(page.getByTestId('dashboard-residency')).toContainText('尚未入住')
      await expect(page.getByTestId('dashboard-results')).toContainText('入住后可查看寝室成绩')
      await page.getByTestId('dashboard-residency').getByRole('link').click()
      await expect(page.getByRole('heading', { name: '我的住宿', exact: true })).toBeVisible()
    } else {
      await expect(page.getByTestId('queue-PENDING')).toContainText('暂无待处理工单')
      await page.getByTestId('queue-PENDING').getByRole('link', { name: '查看全部', exact: true }).click()
      await expect(page.getByLabel('工单状态', { exact: true })).toHaveValue('PENDING')
      await expect(page.locator('.account-pagination')).toContainText('共 0 条记录')
    }
    await home(page)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
    await page.screenshot({ path: `../output/playwright/task16-empty-${role}-${page.viewportSize()!.width}.png`, fullPage: true, animations: 'disabled' })
  }
})

async function residence(admin: Page) {
  const room = (await (await admin.request.get('/api/housing/rooms?q=A101')).json()).items[0]
  const bed = (await (await admin.request.get(`/api/housing/beds?roomId=${room.id}`)).json()).items[0]
  const student = (await (await admin.request.get('/api/housing/students')).json()).items[0]
  const stay = await post(admin, '/api/housing/residencies', { studentId: student.id, bedId: bed.id })
  return { room, stay }
}
async function prepare(admin: Page) {
  const scheme = await post(admin, '/api/hygiene/schemes', { name: '模拟工作台评分标准', dimensions: [{ name: '地面', weight: 100 }] })
  await post(admin, `/api/hygiene/schemes/${scheme.id}/activate`, { version: 0, activeId: 0 }, 200)
  return post(admin, '/api/hygiene/batches/current/prepare', {}, 200)
}
async function repair(student: Page, description = '模拟工作台水龙头漏水') {
  const token = await (await student.request.get('/api/auth/csrf')).json()
  const response = await student.request.post('/api/repairs', { headers: { [token.headerName]: token.token }, multipart: { category: 'PLUMBING', description, requestId: crypto.randomUUID() } })
  expect(response.status()).toBe(201); return response.json()
}
async function screenshot(page: Page, name: string) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  await page.screenshot({ path: `../output/playwright/task16-${name}-${page.viewportSize()!.width}.png`, fullPage: true, animations: 'disabled' })
}

test('宿管从未检卡片正式评分，工作台进度整改与管理统计入口一致', async ({ page, browser }) => {
  await login(page, 'system_admin'); await residence(page); await prepare(page)
  const context = await browser.newContext({ viewport: page.viewportSize()! }), manager = await context.newPage()
  try {
    await login(manager, 'dorm_manager')
    const progress = manager.getByTestId('dashboard-inspection')
    await expect(progress).toContainText('0 / 1 间')
    await progress.getByRole('link', { name: '未检查 1 间', exact: true }).click()
    await expect(manager.getByLabel('检查状态', { exact: true })).toHaveValue('false')
    await manager.getByRole('row').filter({ hasText: 'A101' }).getByRole('button', { name: '录入评分', exact: true }).click()
    const dialog = manager.getByRole('dialog', { name: '寝室正式评分', exact: true })
    await dialog.getByLabel('地面分值', { exact: true }).fill('50')
    await dialog.getByRole('button', { name: '提交正式评分', exact: true }).click()
    await expect(manager.getByRole('status').filter({ hasText: '正式评分已保存' })).toBeVisible()
    await expect(dialog).toBeHidden(); await home(manager)
    await expect(progress).toContainText('1 / 1 间')
    await expect(manager.getByTestId('dashboard-rectifications')).toContainText('1 项未关闭')
    await screenshot(manager, 'manager')
    await manager.getByTestId('dashboard-rectifications').getByRole('link', { name: /A101/ }).click()
    await expect(manager.getByRole('dialog', { name: '整改详情与处理', exact: true })).toContainText('50.00')
    await manager.getByRole('dialog').getByRole('button', { name: '返回列表', exact: true }).click(); await home(manager)
    await manager.getByRole('link', { name: '查看卫生统计', exact: true }).click()
    await expect(manager.getByRole('heading', { name: '卫生统计', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '刷新工作台', exact: true }).click()
    await expect(page.getByTestId('dashboard-inspection')).toContainText('1 / 1 间')
    await screenshot(page, 'admin')
  } finally { await context.close() }
})

test('学生工作台随成绩更正调寝退宿更新，保留本人历史报修入口', async ({ page, browser }) => {
  await login(page, 'system_admin'); const f = await residence(page); const batch = await prepare(page)
  const context = await browser.newContext({ viewport: page.viewportSize()! }), student = await context.newPage()
  try {
    await login(student, 'student'); await expect(student.getByTestId('dashboard-results')).toContainText('本周暂无有效成绩')
    await login(page, 'dorm_manager')
    const score = await post(page, `/api/hygiene/batches/${batch.id}/rooms/${f.room.id}/score`, { dimensions: [{ dimensionId: batch.dimensions[0].id, score: 50 }] })
    await student.getByRole('button', { name: '刷新工作台', exact: true }).click(); await expect(student.getByTestId('dashboard-results')).toContainText('50.00')
    await login(page, 'system_admin')
    await post(page, `/api/hygiene/scores/${score.id}/corrections`, { version: 0, reason: '模拟复核90分', dimensions: [{ dimensionId: batch.dimensions[0].id, score: 90 }] }, 200)
    await student.getByRole('button', { name: '刷新工作台', exact: true }).click(); await expect(student.getByTestId('dashboard-results')).toContainText('90.00')
    await student.getByTestId('dashboard-results').getByRole('link').click()
    await expect(student.getByTestId('result-summary')).toContainText('90.00'); await home(student)
    const order = await repair(student)
    await student.getByRole('button', { name: '刷新工作台', exact: true }).click(); await expect(student.getByTestId('queue-ALL')).toContainText('1 条')
    await screenshot(student, 'student')
    const bed = (await (await page.request.get('/api/housing/beds?q=B201')).json()).items[0]
    const moved = await post(page, `/api/housing/residencies/${f.stay.id}/transfer`, { bedId: bed.id, note: '模拟调寝' }, 200)
    await student.getByRole('button', { name: '刷新工作台', exact: true }).click()
    await expect(student.getByTestId('dashboard-residency')).toContainText('B201'); await expect(student.getByTestId('dashboard-results')).not.toContainText('90.00')
    await student.getByTestId('queue-ALL').getByRole('link', { name: new RegExp(`#${order.id}`) }).click()
    await expect(student.getByRole('dialog')).toContainText('A101')
    await student.getByRole('dialog').getByRole('button', { name: '关闭详情', exact: true }).click(); await home(student)
    await post(page, `/api/housing/residencies/${moved.id}/checkout`, { note: '模拟退宿' }, 200)
    await student.getByRole('button', { name: '刷新工作台', exact: true }).click()
    await expect(student.getByTestId('dashboard-residency')).toContainText('尚未入住'); await expect(student.getByTestId('queue-ALL')).toContainText('1 条')
  } finally { await context.close() }
})

test('维修工作台只显示本人队列，实际完成后数字与筛选列表同步', async ({ page, browser }) => {
  await login(page, 'system_admin'); await residence(page)
  const workerId = (await (await page.request.get('/api/identity/accounts?q=maintainer')).json()).items[0].id
  const context = await browser.newContext({ viewport: page.viewportSize()! }), worker = await context.newPage()
  try {
    await login(worker, 'student'); const order = await repair(worker)
    await post(page, `/api/repairs/${order.id}/assign`, { version: 0, assigneeId: workerId }, 200)
    await login(worker, 'maintainer'); await expect(worker.getByTestId('queue-PENDING')).toContainText('1 条')
    await screenshot(worker, 'maintainer')
    await worker.getByTestId('queue-PENDING').getByRole('link', { name: '查看全部', exact: true }).click()
    await expect(worker.getByLabel('工单状态', { exact: true })).toHaveValue('PENDING'); await expect(worker.locator('.account-pagination')).toContainText('共 1 条记录')
    await worker.getByRole('button', { name: '查看详情', exact: true }).click()
    await worker.getByRole('button', { name: '开始维修', exact: true }).click()
    await expect(worker.getByRole('button', { name: '确认完成维修', exact: true })).toBeVisible()
    await worker.getByRole('dialog').getByRole('button', { name: '关闭详情', exact: true }).click(); await home(worker)
    await expect(worker.getByTestId('queue-PENDING')).toContainText('0 条'); await expect(worker.getByTestId('queue-IN_PROGRESS')).toContainText('1 条')
    await worker.getByTestId('queue-IN_PROGRESS').getByRole('link', { name: new RegExp(`#${order.id}`) }).click()
    const dialog = worker.getByRole('dialog'); await dialog.getByLabel('处理说明', { exact: true }).fill('模拟更换水龙头，已修复')
    await dialog.getByRole('button', { name: '确认完成维修', exact: true }).click(); await expect(dialog.getByTestId('repair-result')).toContainText('已修复')
    await dialog.getByRole('button', { name: '关闭详情', exact: true }).click(); await home(worker)
    await expect(worker.getByTestId('queue-IN_PROGRESS')).toContainText('0 条')
    await page.getByRole('button', { name: '刷新工作台', exact: true }).click(); await expect(page.getByTestId('queue-IN_PROGRESS')).toContainText('0 条')
  } finally { await context.close() }
})

test('工作台加载失败清空旧数据，恢复可重试，角色变更撤销旧范围', async ({ page, browser }) => {
  const context = await browser.newContext({ viewport: page.viewportSize()! }), other = await context.newPage()
  try {
    await login(page, 'dorm_manager'); await expect(page.getByTestId('dashboard-content')).toBeVisible()
    await page.route('**/api/dashboard', route => route.abort())
    await page.getByRole('button', { name: '刷新工作台', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('暂时无法连接服务'); await expect(page.getByTestId('dashboard-content')).toHaveCount(0)
    await screenshot(page, 'error'); await page.unroute('**/api/dashboard')
    await page.getByRole('button', { name: '重新加载', exact: true }).click(); await expect(page.getByTestId('dashboard-content')).toBeVisible()
    await login(other, 'system_admin')
    const account = (await (await other.request.get('/api/identity/accounts?q=dorm_manager')).json()).items[0]
    await post(other, `/api/identity/accounts/${account.id}/role`, { version: account.version, role: 'MAINTAINER' }, 200)
    await page.getByRole('button', { name: '刷新工作台', exact: true }).click(); await expect(page).toHaveURL(/\/login/)
    await expect(page.getByTestId('dashboard-content')).toHaveCount(0)
    await login(page, 'dorm_manager'); await expect(page.getByTestId('dashboard-content')).toBeVisible()
    await expect(page.getByTestId('dashboard-inspection')).toHaveCount(0); await expect(page.getByTestId('queue-PENDING')).toBeVisible()
  } finally { await context.close() }
})

test('迟到的工作台响应不会覆盖新的刷新或离页后的业务', async ({ page }) => {
  await login(page, 'system_admin'); await expect(page.getByTestId('dashboard-content')).toBeVisible()
  let release!: () => void, started!: () => void
  const gate = new Promise<void>(resolve => { release = resolve }), entered = new Promise<void>(resolve => { started = resolve })
  await page.route('**/api/dashboard', async route => {
    const response = await route.fetch(); started(); await gate; await route.fulfill({ response })
  }, { times: 1 })
  await page.getByRole('button', { name: '刷新工作台', exact: true }).click(); await entered
  await expect(page.getByRole('status')).toContainText('正在读取工作台'); await expect(page.getByTestId('dashboard-content')).toHaveCount(0)
  await prepare(page)
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await expect(page.getByTestId('dashboard-inspection')).toContainText('无应检寝室')
  release(); await expect(page.getByTestId('dashboard-inspection')).not.toContainText('尚未准备')
  await page.getByRole('link', { name: '查看卫生统计', exact: true }).click(); await expect(page.getByTestId('dashboard-content')).toHaveCount(0)
})

test('旧工作台迟到身份响应不能覆盖退出后重新登录的学生身份', async ({ page }) => {
  await login(page, 'system_admin'); await expect(page.getByTestId('dashboard-content')).toBeVisible()
  let release!: () => void, started!: () => void
  const gate = new Promise<void>(resolve => { release = resolve }), entered = new Promise<void>(resolve => { started = resolve })
  await page.route('**/api/auth/me', async route => {
    const response = await route.fetch(); expect(response.status()).toBe(200); started(); await gate; await route.fulfill({ response })
  }, { times: 1 })
  await page.getByRole('button', { name: '刷新工作台', exact: true }).click(); await entered
  await page.getByRole('button', { name: '退出登录', exact: true }).click(); await expect(page).toHaveURL(/\/login/)
  await page.getByLabel('账号', { exact: true }).fill('student')
  await page.getByLabel('密码', { exact: true }).fill('DemoPass2026!')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '学生工作台', exact: true })).toBeVisible()
  await expect(page.getByTestId('dashboard-content')).toBeVisible()
  const oldResponse = page.waitForResponse('**/api/auth/me')
  release(); await (await oldResponse).finished()
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
  await expect(page.getByRole('heading', { name: '学生工作台', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: '账号管理', exact: true })).toHaveCount(0)
  await expect(page.getByTestId('dashboard-residency')).toBeVisible()
})
