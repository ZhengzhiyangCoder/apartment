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
function moveWeek(id: number, monday: string) {
  expect(Number.isSafeInteger(id) && id > 0).toBeTruthy(); expect(monday).toMatch(/^2026-11-(02|09)$/)
  const cwd = fileURLToPath(new URL('../..', import.meta.url))
  const args = ['compose', '--env-file', 'deploy/.env', '-f', 'deploy/compose.yaml', 'exec', '-T', 'mysql', 'sh', '-c', 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --skip-column-names --default-character-set=utf8mb4 -u apartment_app apartment_test']
  expect(execFileSync('docker', args, { cwd, input: 'SELECT DATABASE();', encoding: 'utf8' }).trim()).toBe('apartment_test')
  // Time-only fixture; score publication and governance use the real HTTP APIs.
  execFileSync('docker', args, { cwd, input: `UPDATE hygiene_batch SET iso_year=2026, iso_week=MOD(YEARWEEK('${monday}',3),100), week_start='${monday}' WHERE id=${id};`, stdio: ['pipe', 'pipe', 'pipe'] })
}
async function fixtures(admin: Page, manager: Page, extraRooms = 0) {
  await login(admin, 'system_admin'); await login(manager, 'dorm_manager')
  const rooms = (await (await admin.request.get('/api/housing/rooms')).json()).items
  const home = rooms.find((r: { roomNo: string }) => r.roomNo === 'A101')
  const peer = rooms.find((r: { roomNo: string }) => r.roomNo === 'A102')
  const other = rooms.find((r: { roomNo: string }) => r.roomNo === 'B201')
  const extra = []
  for (let i = 0; i < extraRooms; i++) {
    const room = await post(admin, '/api/housing/rooms', { buildingId: home.buildingId, roomNo: `A${103 + i}`, floor: 1 })
    await post(admin, '/api/housing/beds', { roomId: room.id, bedNo: '01' }); extra.push(room)
  }
  const student = (await (await admin.request.get('/api/housing/students')).json()).items[0]
  const homeBed = (await (await admin.request.get(`/api/housing/beds?roomId=${home.id}`)).json()).items[0]
  const residency = await post(admin, '/api/housing/residencies', { studentId: student.id, bedId: homeBed.id })
  for (const [i, room] of [peer, other, ...extra].entries()) {
    const user = await post(admin, '/api/identity/accounts', { username: `result_peer_${i}`, displayName: '模拟姓名不公开', role: 'STUDENT', password: 'DemoPass2026!' })
    const profile = await post(admin, '/api/housing/students', { userId: user.id, studentNo: `result_peer_${i}`, fullName: '模拟姓名不公开', college: '模拟学院', major: '模拟专业', className: '模拟班级' })
    const bed = (await (await admin.request.get(`/api/housing/beds?roomId=${room.id}`)).json()).items[0]
    await post(admin, '/api/housing/residencies', { studentId: profile.id, bedId: bed.id })
  }
  const scheme = await post(admin, '/api/hygiene/schemes', { name: '模拟学生历史标准', threshold: 65, dimensions: [{ name: '地面', weight: 100 }] })
  await post(admin, `/api/hygiene/schemes/${scheme.id}/activate`, { version: 0, activeId: 0 }, 200)
  const batches = []
  for (const [date, value] of [['2026-11-02', 80], ['2026-11-09', 90]] as const) {
    const batch = await post(manager, '/api/hygiene/batches/current/prepare', {}, 200); batches.push(batch)
    for (const [room, score] of [[home, value], [peer, 90], [other, 70]] as const)
      await post(manager, `/api/hygiene/batches/${batch.id}/rooms/${room.id}/score`, { dimensions: [{ dimensionId: batch.dimensions[0].id, score }] })
    for (const room of extra) await post(manager, `/api/hygiene/batches/${batch.id}/rooms/${room.id}/score`, { dimensions: [{ dimensionId: batch.dimensions[0].id, score: 90 }] })
    moveWeek(batch.id, date)
  }
  return { home, peer, other, residency, batches }
}
test.beforeEach(() => { execFileSync(fileURLToPath(new URL('../../scripts/seed-demo.sh', import.meta.url)), ['--test-reset'], { stdio: 'pipe' }) })

async function month(page: Page) {
  await page.getByLabel('统计周期', { exact: true }).selectOption('MONTH')
  await page.getByLabel('周期内日期', { exact: true }).fill('2026-11-30')
  await page.getByRole('button', { name: '查询成绩', exact: true }).click()
  await expect(page.getByTestId('result-summary')).toContainText('2026-11')
}

test('榜单末页在全部批次停检后回到空榜第一页', async ({ page, browser }) => {
  const adminContext = await browser.newContext(), managerContext = await browser.newContext()
  try {
    const admin = await adminContext.newPage(), manager = await managerContext.newPage()
    const f = await fixtures(admin, manager, 9); await login(page, 'student'); await page.goto('/my-results'); await month(page)
    await page.getByRole('button', { name: '下一页', exact: true }).click()
    await expect(page.getByText('第 2 / 2 页', { exact: true })).toBeVisible()
    for (const original of f.batches) {
      const path = `/api/hygiene/batches/${original.id}`, batch = await (await admin.request.get(path)).json()
      await post(admin, path + '/state', { version: batch.version, action: 'SUSPEND', reason: '模拟全部样本停检' }, 200)
    }
    await page.getByRole('button', { name: '刷新成绩', exact: true }).click()
    await expect(page.getByText('本周期未检查 / 数据不足，暂无参评寝室。', { exact: true })).toBeVisible()
    await expect(page.getByText('第 1 / 1 页', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '上一页', exact: true })).toBeDisabled()
  } finally { await adminContext.close(); await managerContext.close() }
})

test('调寝后刷新切换到新寝室历史，旧成绩被拒但本人旧报修仍可见', async ({ page, browser }) => {
  const adminContext = await browser.newContext({ viewport: page.viewportSize()! }), managerContext = await browser.newContext()
  try {
    const admin = await adminContext.newPage(), manager = await managerContext.newPage()
    const f = await fixtures(admin, manager); await login(page, 'student')
    const token = await (await page.request.get('/api/auth/csrf')).json()
    const repair = await page.request.post('/api/repairs', { headers: { [token.headerName]: token.token }, multipart: { requestId: crypto.randomUUID(), category: 'PLUMBING', description: '模拟调寝前水龙头报修' } })
    expect(repair.status()).toBe(201)
    await page.goto('/my-results'); await month(page); await expect(page.getByTestId('result-summary')).toContainText('A101')
    await admin.goto('/residencies'); await admin.getByLabel('搜索住宿').fill('DEMO20260001')
    await admin.getByRole('button', { name: '查询', exact: true }).click()
    const row = admin.getByRole('row').filter({ hasText: 'DEMO20260001' })
    await row.getByRole('button', { name: '调寝', exact: true }).click()
    const dialog = admin.getByRole('dialog', { name: '办理调寝', exact: true })
    await dialog.getByLabel('搜索空床位').fill('B201'); await dialog.getByRole('button', { name: '查找', exact: true }).click()
    await dialog.getByLabel('空床位', { exact: true }).selectOption({ label: '模拟 B 栋 · B201 · 01 号床' })
    await dialog.getByLabel('办理说明').fill('模拟跨楼调寝验证成绩权限')
    await dialog.getByRole('button', { name: '确认调寝', exact: true }).click(); await expect(dialog).not.toBeVisible()
    await page.getByRole('button', { name: '刷新成绩', exact: true }).click()
    await expect(page.getByTestId('result-summary')).toContainText('B201'); await expect(page.getByTestId('result-summary')).toContainText('70.00')
    await expect(page.getByRole('table', { name: '楼栋排行榜', exact: true })).not.toContainText('A101')
    expect((await page.request.get(`/api/hygiene/student/results?roomId=${f.home.id}`)).status()).toBe(403)
    expect((await page.request.get(`/api/hygiene/student/results?buildingId=${f.home.buildingId}`)).status()).toBe(403)
    await page.screenshot({ path: `../output/playwright/task11-transferred-${page.viewportSize()!.width}.png`, fullPage: true, animations: 'disabled' })
    await page.goto('/repairs'); await expect(page.getByRole('table')).toContainText('模拟调寝前水龙头报修'); await expect(page.getByRole('table')).toContainText('A101')
    await row.getByRole('button', { name: '退宿', exact: true }).click()
    const checkout = admin.getByRole('dialog', { name: '办理退宿', exact: true })
    await checkout.getByLabel('办理说明').fill('模拟退宿权限收回'); await checkout.getByRole('button', { name: '确认退宿', exact: true }).click(); await expect(checkout).not.toBeVisible()
    await page.goto('/my-results'); await expect(page.getByRole('heading', { name: '尚未入住', exact: true })).toBeVisible()
    await expect(page.getByTestId('result-summary')).toHaveCount(0)
    expect((await page.request.get('/api/hygiene/student/results')).status()).toBe(409)
  } finally { await adminContext.close(); await managerContext.close() }
})

test('停检和恢复联动成绩趋势与榜单，断网清除旧结果且迟到响应不覆盖新结果', async ({ page, browser }) => {
  const adminContext = await browser.newContext(), managerContext = await browser.newContext()
  try {
    const admin = await adminContext.newPage(), manager = await managerContext.newPage()
    const f = await fixtures(admin, manager); await login(page, 'student'); await page.goto('/my-results'); await month(page)
    await expect(page.getByTestId('result-summary')).toContainText('85.00')
    const path = `/api/hygiene/batches/${f.batches[1].id}`
    const change = async (action: string) => {
      const batch = await (await admin.request.get(path)).json()
      await post(admin, path + '/state', { version: batch.version, action, reason: '模拟学生统计验证' }, 200)
    }
    await change('SUSPEND'); await page.getByRole('button', { name: '刷新成绩', exact: true }).click()
    await expect(page.getByTestId('result-summary')).toContainText('80.00'); await expect(page.getByTestId('result-summary')).toContainText('有效参评 1 次')
    await expect(page.getByRole('table', { name: '周期检查明细', exact: true })).toContainText('停检，不计成绩')
    const pattern = '**/api/hygiene/student/results?*'
    await page.route(pattern, route => route.abort('failed'))
    await page.getByRole('button', { name: '刷新成绩', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('暂时无法连接服务')
    await expect(page.getByTestId('result-summary')).toHaveCount(0); await expect(page.getByLabel('统计周期')).toHaveValue('MONTH')
    await page.unroute(pattern); await page.getByRole('button', { name: '重新加载', exact: true }).click()
    await expect(page.getByTestId('result-summary')).toContainText('80.00')
    let release!: () => void, captured!: () => void
    const gate = new Promise<void>(resolve => { release = resolve }), started = new Promise<void>(resolve => { captured = resolve })
    const delayed = page.route(pattern, async route => { const response = await route.fetch(); captured(); await gate; await route.fulfill({ response }) }, { times: 1 })
    await delayed; await page.getByRole('button', { name: '刷新成绩', exact: true }).click(); await started
    await change('RESTORE')
    await page.evaluate(() => window.dispatchEvent(new Event('focus')))
    await expect(page.getByTestId('result-summary')).toContainText('85.00')
    const responseArrived = page.waitForResponse(response => response.url().includes('/api/hygiene/student/results'))
    release(); await responseArrived
    await expect(page.getByTestId('result-summary')).toContainText('85.00'); await expect(page.getByTestId('result-summary')).toContainText('有效参评 2 次')
    await page.getByLabel('统计周期').selectOption('WEEK'); await page.getByLabel('周期内日期').fill('2026-11-09')
    await page.getByRole('button', { name: '查询成绩', exact: true }).click()
    await expect(page.getByTestId('result-summary')).toContainText('90.00'); await expect(page.getByTestId('result-summary')).toContainText('第 1 名')
    await expect(page.getByRole('table', { name: '楼栋排行榜' }).getByRole('row').nth(1)).toContainText('A101')
    await expect(page.getByRole('table', { name: '楼栋排行榜' }).getByRole('row').nth(2).getByRole('cell').first()).toHaveText('1')
  } finally { await adminContext.close(); await managerContext.close() }
})

test('未入住和无有效成绩有明确提示，其他角色没有学生成绩入口', async ({ page, browser }) => {
  await login(page, 'student'); await page.goto('/my-results')
  await expect(page.getByRole('heading', { name: '尚未入住', exact: true })).toBeVisible()
  const context = await browser.newContext()
  try {
    const admin = await context.newPage(); await login(admin, 'system_admin')
    const student = (await (await admin.request.get('/api/housing/students')).json()).items[0]
    const bed = (await (await admin.request.get('/api/housing/beds?q=A101')).json()).items[0]
    await post(admin, '/api/housing/residencies', { studentId: student.id, bedId: bed.id })
    await page.getByRole('button', { name: '刷新成绩', exact: true }).click()
    await expect(page.getByTestId('result-summary')).toContainText('未检查 / 数据不足')
    await expect(page.getByText('本周期未检查 / 数据不足，暂无参评寝室。', { exact: true })).toBeVisible()
    await expect(page.getByRole('table', { name: '周期检查明细' })).toContainText('尚未准备检查')
    await page.screenshot({ path: `../output/playwright/task11-empty-${page.viewportSize()!.width}.png`, fullPage: true, animations: 'disabled' })
    await expect(admin.getByRole('link', { name: '寝室成绩与排行', exact: true })).toHaveCount(0)
    await admin.goto('/my-results'); await expect(admin).toHaveURL(/\/forbidden$/)
    expect((await admin.request.get('/api/hygiene/student/results')).status()).toBe(403)
  } finally { await context.close() }
})

test('学生从住宿查看月均成绩、趋势空缺、历史标准与楼栋榜单', async ({ page, browser }) => {
  const adminContext = await browser.newContext(); const managerContext = await browser.newContext()
  try {
    const admin = await adminContext.newPage(), manager = await managerContext.newPage()
    await fixtures(admin, manager); await login(page, 'student')
    await page.getByRole('link', { name: '我的住宿', exact: true }).click()
    await page.getByRole('link', { name: '查看寝室成绩', exact: true }).click({ timeout: 5000 })
    await page.getByLabel('统计周期', { exact: true }).selectOption('MONTH')
    await page.getByLabel('周期内日期', { exact: true }).fill('2026-11-30')
    await page.getByRole('button', { name: '查询成绩', exact: true }).click()
    const summary = page.getByTestId('result-summary')
    await expect(summary).toContainText('85.00'); await expect(summary).toContainText('有效参评 2 次')
    await expect(summary).toContainText('第 2 名'); await expect(summary).toContainText('2026-11')
    await expect(page.getByRole('img', { name: /成绩趋势/ })).toBeVisible()
    await page.getByText('查看趋势数据', { exact: true }).click()
    const trend = page.getByRole('table', { name: '趋势数据', exact: true })
    await expect(trend.getByRole('row').filter({ hasText: '2026-10' })).toContainText('数据不足')
    await expect(trend.getByRole('row').filter({ hasText: '2026-11' })).toContainText('85.00')
    await page.getByText('查看趋势数据', { exact: true }).click()
    const ranks = page.getByRole('table', { name: '楼栋排行榜', exact: true })
    await expect(ranks).toContainText('A101'); await expect(ranks).toContainText('A102')
    await expect(ranks).not.toContainText('B201'); await expect(ranks).not.toContainText('模拟姓名')
    await page.getByRole('table', { name: '周期检查明细', exact: true }).getByRole('row').filter({ hasText: '第45周' }).getByRole('button', { name: '查看标准与评分', exact: true }).click()
    const detail = page.getByRole('dialog', { name: '历史标准与成绩', exact: true })
    await expect(detail).toContainText('模拟学生历史标准'); await expect(detail).toContainText('65.00')
    await expect(detail).toContainText('100.00%'); await expect(detail).toContainText('80.00')
    await page.screenshot({ path: `../output/playwright/task11-detail-${page.viewportSize()!.width}.png`, animations: 'disabled' })
    await detail.getByRole('button', { name: '关闭', exact: true }).click()
    await expect(detail).not.toBeVisible()
    await page.evaluate(() => window.scrollTo(0, 0))
    await page.screenshot({ path: `../output/playwright/task11-results-${page.viewportSize()!.width}.png`, fullPage: true, animations: 'disabled' })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  } finally { await adminContext.close(); await managerContext.close() }
})
