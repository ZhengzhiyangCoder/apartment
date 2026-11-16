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
  const scheme = await post(admin, '/api/hygiene/schemes', { name: '模拟管理标准一', threshold: 65, dimensions: [{ name: '地面', weight: 100 }] })
  await post(admin, `/api/hygiene/schemes/${scheme.id}/activate`, { version: 0, activeId: 0 }, 200)
  const batches = []
  for (const [date, value] of [['2026-11-02', 100], ['2026-11-09', 100]] as const) {
    const batch = await post(manager, '/api/hygiene/batches/current/prepare', {}, 200); batches.push(batch)
    for (const [room, score] of [[home, value], [other, 70], ...(date === '2026-11-02' ? [[peer, 0] as const] : [])] as const)
      await post(manager, `/api/hygiene/batches/${batch.id}/rooms/${room.id}/score`, { dimensions: batch.dimensions.map((d: { id: number }) => ({ dimensionId: d.id, score })) })
    for (const room of extra) await post(manager, `/api/hygiene/batches/${batch.id}/rooms/${room.id}/score`, { dimensions: batch.dimensions.map((d: { id: number }) => ({ dimensionId: d.id, score: 90 })) })
    moveWeek(batch.id, date)
    if (date === '2026-11-02') {
      const next = await post(admin, '/api/hygiene/schemes', { name: '模拟管理标准二', threshold: 65, dimensions: [{ name: '地面', weight: 50 }, { name: '摆放', weight: 50 }] })
      await post(admin, `/api/hygiene/schemes/${next.id}/activate`, { version: 0, activeId: scheme.id }, 200)
    }
  }
  return { home, peer, other, residency, batches }
}
test.beforeEach(() => { execFileSync(fileURLToPath(new URL('../../scripts/seed-demo.sh', import.meta.url)), ['--test-reset'], { stdio: 'pipe' }) })

async function month(page: Page) {
  await page.getByLabel('统计周期', { exact: true }).selectOption('MONTH')
  await page.getByLabel('周期内日期', { exact: true }).fill('2026-11-30')
  await page.getByRole('button', { name: '查询统计', exact: true }).click()
  await expect(page.getByTestId('statistics-summary')).toContainText('2026-11')
}

test('管理端全校楼栋与寝室筛选，50分等权均分、趋势与分版本明细一致', async ({ page, browser }) => {
  const managerContext = await browser.newContext()
  try {
    const manager = await managerContext.newPage()
    const f = await fixtures(page, manager)
    await page.getByRole('link', { name: '卫生统计', exact: true }).click(); await month(page)
    const buildings = page.getByRole('table', { name: '楼栋成绩对比', exact: true })
    await expect(buildings.getByRole('row').filter({ hasText: f.home.buildingName })).toContainText('50.00')
    await expect(page.getByTestId('statistics-summary')).toContainText('有效参评 5 次')
    await expect(page.getByRole('table', { name: '寝室成绩排行', exact: true })).toContainText('A101')
    await page.getByLabel('楼栋范围', { exact: true }).selectOption(String(f.home.buildingId))
    await page.getByRole('button', { name: '查询统计', exact: true }).click()
    await expect(page.getByTestId('statistics-summary')).toContainText('有效参评 3 次')
    await expect(buildings).not.toContainText(f.other.buildingName)
    await expect(page.getByRole('img', { name: /成绩趋势/ })).toHaveAccessibleName(/有效样本3次/)
    await page.getByText('查看趋势数据', { exact: true }).click()
    const trend = page.getByRole('table', { name: '趋势数据', exact: true })
    await expect(trend.getByRole('row').filter({ hasText: '2026-11' })).toContainText('50.00')
    await expect(trend.getByRole('row').filter({ hasText: '2026-10' })).toContainText('数据不足')
    await page.getByText('查看趋势数据', { exact: true }).click()
    const old = page.getByRole('region', { name: /模拟管理标准一/ }), next = page.getByRole('region', { name: /模拟管理标准二/ })
    await expect(old).toContainText('100.00%'); await expect(old).toContainText('50.00')
    await expect(next).toContainText('50.00%'); await expect(next).toContainText('100.00')
    await page.evaluate(() => window.scrollTo(0, 0))
    await page.screenshot({ path: `../output/playwright/task12-building-${page.viewportSize()!.width}.png`, fullPage: true, animations: 'disabled' })
    await page.getByRole('table', { name: '寝室成绩排行', exact: true }).getByRole('row').filter({ hasText: 'A101' }).getByRole('button', { name: '查看寝室', exact: true }).click()
    await expect(page.getByTestId('statistics-summary')).toContainText('A101')
    await expect(page.getByRole('img', { name: /成绩趋势/ })).toHaveAccessibleName(/有效样本2次/)
    await page.getByRole('table', { name: '周期检查明细', exact: true }).getByRole('row').filter({ hasText: '第45周' }).getByRole('button', { name: '查看标准与评分', exact: true }).click()
    const detail = page.getByRole('dialog', { name: '历史标准与成绩', exact: true })
    await expect(detail).toContainText('模拟管理标准一'); await expect(detail).toContainText('100.00%')
    await detail.getByRole('button', { name: '关闭', exact: true }).click(); await expect(detail).not.toBeVisible()
    await page.evaluate(() => window.scrollTo(0, 0))
    await page.screenshot({ path: `../output/playwright/task12-room-${page.viewportSize()!.width}.png`, fullPage: true, animations: 'disabled' })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  } finally { await managerContext.close() }
})

async function state(admin: Page, id: number, action: string) {
  const path = `/api/hygiene/batches/${id}`, batch = await (await admin.request.get(path)).json()
  return post(admin, path + '/state', { version: batch.version, action, reason: '模拟管理统计治理验证' }, 200)
}

test('宿管分页、停检恢复、断网重试和迟到响应不覆盖最新结果', async ({ page, browser }) => {
  const adminContext = await browser.newContext()
  try {
    const admin = await adminContext.newPage(), f = await fixtures(admin, page, 9)
    await page.getByRole('link', { name: '卫生统计', exact: true }).click(); await month(page)
    await page.getByRole('button', { name: '下一页', exact: true }).click()
    await expect(page.getByText('第 2 / 2 页', { exact: true })).toBeVisible()
    for (const batch of f.batches) await state(admin, batch.id, 'SUSPEND')
    await page.getByRole('button', { name: '刷新统计', exact: true }).click()
    await expect(page.getByText('第 1 / 1 页', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '上一页', exact: true })).toBeDisabled()
    await expect(page.getByTestId('statistics-summary')).toContainText('暂无应检寝室')
    await expect(page.getByText('本周期无有效维度样本。', { exact: true })).toBeVisible()
    await state(admin, f.batches[0].id, 'RESTORE')
    await page.getByRole('button', { name: '刷新统计', exact: true }).click()
    await expect(page.getByTestId('statistics-summary')).toContainText('有效参评 12 次')
    await page.route('**/api/hygiene/statistics?*', route => route.abort())
    await page.getByRole('button', { name: '刷新统计', exact: true }).click()
    await expect(page.getByRole('alert')).toBeVisible(); await expect(page.getByTestId('statistics-summary')).toHaveCount(0)
    await expect(page.getByLabel('统计周期', { exact: true })).toHaveValue('MONTH')
    await page.unroute('**/api/hygiene/statistics?*')
    await page.getByRole('button', { name: '重新加载', exact: true }).click()
    await expect(page.getByTestId('statistics-summary')).toContainText('有效参评 12 次')
    let release!: () => void, captured!: () => void
    const held = new Promise<void>(resolve => { release = resolve }), ready = new Promise<void>(resolve => { captured = resolve })
    await page.route('**/api/hygiene/statistics?*', async route => {
      const response = await route.fetch(); captured(); await held; await route.fulfill({ response })
    }, { times: 1 })
    await page.getByRole('button', { name: '刷新统计', exact: true }).click(); await ready
    await state(admin, f.batches[1].id, 'RESTORE')
    await page.evaluate(() => window.dispatchEvent(new Event('focus')))
    await expect(page.getByTestId('statistics-summary')).toContainText('有效参评 23 次')
    release(); await page.unrouteAll({ behavior: 'wait' })
    await expect(page.getByTestId('statistics-summary')).toContainText('有效参评 23 次')
    await page.getByLabel('楼栋范围', { exact: true }).selectOption(String(f.other.buildingId))
    await page.getByRole('button', { name: '查询统计', exact: true }).click()
    await expect(page.getByText('第 1 / 1 页', { exact: true })).toBeVisible()
    await page.getByLabel('寝室范围', { exact: true }).selectOption(String(f.other.id))
    await page.getByRole('button', { name: '查询统计', exact: true }).click()
    await expect(page.getByTestId('statistics-summary')).toContainText('B201')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  } finally { await adminContext.close() }
})

test('无检查数据明确为空，学生和维修人员没有管理统计入口或接口权限', async ({ page, browser }) => {
  await login(page, 'dorm_manager'); await page.getByRole('link', { name: '卫生统计', exact: true }).click()
  await expect(page.getByTestId('statistics-summary')).toContainText('暂无应检寝室')
  await expect(page.getByText('本周期暂无有效应检记录，数据不足。', { exact: true })).toBeVisible()
  const buildings = await (await page.request.get('/api/housing/buildings')).json()
  await page.getByLabel('楼栋范围', { exact: true }).selectOption(String(buildings.items[0].id))
  await page.getByRole('button', { name: '查询统计', exact: true }).click()
  await expect(page.getByRole('table', { name: '楼栋成绩对比', exact: true })).toContainText('数据不足')
  await expect(page.getByRole('img', { name: /成绩趋势/ })).toHaveAccessibleName(/有效样本0次/)
  await page.screenshot({ path: `../output/playwright/task12-empty-${page.viewportSize()!.width}.png`, fullPage: true, animations: 'disabled' })
  for (const username of ['student', 'maintainer']) {
    const context = await browser.newContext()
    try {
      const restricted = await context.newPage(); await login(restricted, username)
      await expect(restricted.getByRole('link', { name: '卫生统计', exact: true })).toHaveCount(0)
      expect((await restricted.request.get('/api/hygiene/statistics')).status()).toBe(403)
      await restricted.goto('/statistics'); await expect(restricted).toHaveURL(/forbidden/)
    } finally { await context.close() }
  }
})


test('新增统计导航后系统管理员仍可操作侧栏末项', async ({ page }) => {
  await login(page, 'system_admin')
  await page.getByRole('link', { name: '修改密码', exact: true }).click({ timeout: 5000 })
  await expect(page).toHaveURL(/\/password$/)
  await page.getByRole('link', { name: '卫生统计', exact: true }).click()
  await expect(page.getByTestId('statistics-summary')).toBeVisible()
})
