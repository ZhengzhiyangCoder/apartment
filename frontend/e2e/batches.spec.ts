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
async function fixtures(page: Page, occupied = true) {
  await login(page, 'system_admin')
  const scheme = await post(page, '/api/hygiene/schemes', { name: '模拟本周卫生标准', dimensions: [{ name: '地面', weight: 40 }, { name: '摆放', weight: 30 }, { name: '整洁', weight: 30 }] })
  await post(page, `/api/hygiene/schemes/${scheme.id}/activate`, { version: 0, activeId: 0 }, 200)
  if (occupied) {
    const rooms = (await (await page.request.get('/api/housing/rooms')).json()).items
    for (const [index, number] of ['A101', 'A102', 'B201'].entries()) {
      const username = `batch_student_${index}`
      const account = await post(page, '/api/identity/accounts', { username, displayName: '模拟应检学生', role: 'STUDENT', password: 'DemoPass2026!' })
      const student = await post(page, '/api/housing/students', { userId: account.id, studentNo: username, fullName: '模拟应检学生', college: '模拟学院', major: '模拟专业', className: '模拟班级' })
      const bed = (await (await page.request.get(`/api/housing/beds?roomId=${rooms.find((r: { roomNo: string }) => r.roomNo === number).id}`)).json()).items[0]
      await post(page, '/api/housing/residencies', { studentId: student.id, bedId: bed.id })
    }
  }
  return scheme
}
test.beforeEach(() => { execFileSync(fileURLToPath(new URL('../../scripts/seed-demo.sh', import.meta.url)), ['--test-reset'], { stdio: 'pipe' }) })

test('宿管显式准备当前周、按固定维度评分并查看真实进度，系统管理员只读评分', async ({ page, browser }) => {
  const scheme = await fixtures(page)
  const context = await browser.newContext({ viewport: page.viewportSize()! }); const manager = await context.newPage()
  try {
    await login(manager, 'dorm_manager'); await manager.getByRole('link', { name: '本周卫生检查', exact: true }).click()
    await expect(manager.getByText('本周尚未准备检查批次', { exact: true })).toBeVisible()
    expect((await (await manager.request.get('/api/hygiene/batches/current')).json()).batch).toBeNull()
    await manager.getByRole('button', { name: '准备本周检查', exact: true }).click()
    await manager.getByRole('button', { name: '确认准备本周', exact: true }).click()
    await expect(manager.getByTestId('batch-progress')).toContainText('0 / 3')
    for (const [number, values] of [['A101', ['80', '90', '70']], ['B201', ['0', '0', '0']]] as const) {
      await manager.getByRole('row').filter({ hasText: number }).getByRole('button', { name: '录入评分', exact: true }).click()
      const form = manager.getByRole('dialog', { name: '寝室正式评分', exact: true })
      for (const [i, value] of values.entries()) await form.getByLabel(`${['地面', '摆放', '整洁'][i]}分值`, { exact: true }).fill(value)
      await expect(form.getByTestId('score-preview')).toContainText(number === 'A101' ? '80.00' : '0.00')
      await form.getByRole('button', { name: '提交正式评分', exact: true }).click(); await expect(form).not.toBeVisible()
    }
    await expect(manager.getByTestId('batch-progress')).toContainText('2 / 3')
    await expect(manager.getByTestId('batch-progress')).toContainText('66.67%')
    await expect(manager.getByRole('row').filter({ hasText: 'A102' })).toContainText('未检查')
    await expect(manager.getByRole('row').filter({ hasText: 'A102' })).not.toContainText('0.00')
    const next = await post(page, `/api/hygiene/schemes/${scheme.id}/copy`, { version: 1 })
    await post(page, `/api/hygiene/schemes/${next.id}`, { version: 0, definition: { name: '模拟新标准', threshold: 75, dimensions: [{ name: '新维度', weight: 100 }] } }, 200)
    await post(page, `/api/hygiene/schemes/${next.id}/activate`, { version: 1, activeId: scheme.id }, 200)
    await manager.getByRole('button', { name: '刷新本周', exact: true }).click()
    await expect(manager.getByTestId('batch-standard')).toContainText('模拟本周卫生标准')
    await manager.getByRole('row').filter({ hasText: 'A101' }).getByRole('button', { name: '查看结果', exact: true }).click()
    const result = manager.getByRole('dialog', { name: '寝室正式评分', exact: true })
    await expect(result).toContainText('80.00'); await expect(result).toContainText('40.00%'); await expect(result).toContainText('60.00')
    await manager.screenshot({ animations: 'disabled', path: `../output/playwright/task08-score-${page.viewportSize()!.width}.png` })
    await result.getByRole('button', { name: '返回', exact: true }).click()
    await page.goto('/inspections'); await expect(page.getByRole('button', { name: '录入评分', exact: true })).toHaveCount(0)
    await expect(page.getByTestId('batch-progress')).toContainText('2 / 3')
    await page.screenshot({ animations: 'disabled', path: `../output/playwright/task08-progress-${page.viewportSize()!.width}.png`, fullPage: true })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  } finally { await context.close() }
})

test('分值校验、断网和已保存响应丢失均保留输入，重复提交冲突可核对正式结果', async ({ page, browser }) => {
  await fixtures(page); await post(page, '/api/hygiene/batches/current/prepare', {}, 200)
  const context = await browser.newContext({ viewport: page.viewportSize()! }); const manager = await context.newPage()
  try {
    await login(manager, 'dorm_manager'); await manager.goto('/inspections')
    await manager.getByRole('row').filter({ hasText: 'A101' }).getByRole('button', { name: '录入评分', exact: true }).click()
    const form = manager.getByRole('dialog', { name: '寝室正式评分', exact: true })
    await form.getByLabel('地面分值', { exact: true }).fill('80.5'); await form.getByLabel('摆放分值', { exact: true }).fill('90'); await form.getByLabel('整洁分值', { exact: true }).fill('70')
    await form.getByRole('button', { name: '提交正式评分', exact: true }).click()
    await expect(form.getByRole('alert')).toContainText('0–100的整数')
    await form.getByLabel('地面分值', { exact: true }).fill('80')
    const pattern = '**/api/hygiene/batches/*/rooms/*/score'
    await manager.route(pattern, route => route.abort('failed'))
    await form.getByRole('button', { name: '提交正式评分', exact: true }).click()
    await expect(form.getByRole('alert')).toContainText('暂时无法连接服务')
    await expect(form.getByLabel('地面分值', { exact: true })).toHaveValue('80')
    await manager.screenshot({ animations: 'disabled', path: `../output/playwright/task08-network-${page.viewportSize()!.width}.png` })
    await manager.unroute(pattern)
    await manager.route(pattern, async route => { const saved = await route.fetch(); expect(saved.status()).toBe(201); await route.abort('failed') })
    await form.getByRole('button', { name: '提交正式评分', exact: true }).click()
    await expect(form.getByRole('alert')).toContainText('暂时无法连接服务')
    await manager.unroute(pattern)
    await form.getByRole('button', { name: '提交正式评分', exact: true }).click()
    await expect(form.getByRole('alert')).toContainText('已提交正式评分')
    await expect(form.getByLabel('整洁分值', { exact: true })).toHaveValue('70')
    await expect(form.getByRole('button', { name: '提交正式评分', exact: true })).toHaveCount(0)
    await form.getByRole('button', { name: '核对最新状态（保留输入）', exact: true }).click()
    await expect(form.getByText('正式得分', { exact: false })).toContainText('80.00')
    await expect(form.getByLabel('地面分值', { exact: true })).toHaveValue('80')
    await expect(form.getByLabel('地面分值', { exact: true })).toBeDisabled()
    await manager.screenshot({ animations: 'disabled', path: `../output/playwright/task08-conflict-${page.viewportSize()!.width}.png` })
    await form.getByRole('button', { name: '返回', exact: true }).click()
    await manager.getByRole('button', { name: '刷新本周', exact: true }).click()
    await expect(manager.getByTestId('batch-progress')).toContainText('1 / 3')
    await manager.route('**/api/hygiene/batches/*/rooms/*', route => route.abort('failed'))
    await manager.getByRole('row').filter({ hasText: 'A102' }).getByRole('button', { name: '录入评分', exact: true }).click()
    await expect(form.getByRole('alert')).toContainText('暂时无法连接服务')
    await expect(form.getByRole('button', { name: '提交正式评分', exact: true })).toHaveCount(0)
    await manager.unroute('**/api/hygiene/batches/*/rooms/*')
    await form.getByRole('button', { name: '核对最新状态（保留输入）', exact: true }).click()
    await expect(form.getByLabel('地面分值', { exact: true })).toBeEnabled()
  } finally { await context.close() }
})

test('无启用方案可恢复，空批次独立展示，读取失败后清除过时进度并重试', async ({ page, browser }) => {
  await login(page, 'dorm_manager'); await page.goto('/inspections')
  await page.getByRole('button', { name: '准备本周检查', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '准备本周检查', exact: true })
  await dialog.getByRole('button', { name: '确认准备本周', exact: true }).click()
  await expect(dialog.getByRole('alert')).toContainText('尚无启用评分方案')
  const context = await browser.newContext({ viewport: page.viewportSize()! })
  try { await fixtures(await context.newPage(), false) } finally { await context.close() }
  await dialog.getByRole('button', { name: '确认准备本周', exact: true }).click(); await expect(dialog).not.toBeVisible()
  await expect(page.getByText('本批次无应检寝室', { exact: true })).toBeVisible()
  await expect(page.getByTestId('batch-progress')).toContainText('0 / 0')
  await expect(page.getByTestId('batch-progress')).not.toContainText('%')
  await page.screenshot({ animations: 'disabled', path: `../output/playwright/task08-empty-${page.viewportSize()!.width}.png` })
  await page.route('**/api/hygiene/batches/current', route => route.abort('failed'))
  await page.getByRole('button', { name: '刷新本周', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('暂时无法连接服务')
  await expect(page.getByTestId('batch-progress')).toHaveCount(0)
  await page.unroute('**/api/hygiene/batches/current'); await page.getByRole('button', { name: '重新加载', exact: true }).click()
  await expect(page.getByText('本批次无应检寝室', { exact: true })).toBeVisible()
})

test('全校楼栋筛选、分页末页评分后回退及非管理角色直访限制', async ({ page, browser }) => {
  test.setTimeout(60000)
  await fixtures(page)
  const building = (await (await page.request.get('/api/housing/buildings')).json()).items.find((b: { name: string }) => b.name === '模拟 A 栋')
  for (let i = 0; i < 8; i++) {
    const room = await post(page, '/api/housing/rooms', { buildingId: building.id, roomNo: `A2${i}1`, floor: 2 })
    const bed = await post(page, '/api/housing/beds', { roomId: room.id, bedNo: '01' })
    const account = await post(page, '/api/identity/accounts', { username: `batch_extra_${i}`, displayName: '模拟分页学生', role: 'STUDENT', password: 'DemoPass2026!' })
    const profile = await post(page, '/api/housing/students', { userId: account.id, studentNo: `batch_extra_${i}`, fullName: '模拟分页学生', college: '模拟学院', major: '模拟专业', className: '模拟班级' })
    await post(page, '/api/housing/residencies', { studentId: profile.id, bedId: bed.id })
  }
  await post(page, '/api/hygiene/batches/current/prepare', {}, 200)
  await page.getByRole('button', { name: '退出登录', exact: true }).click(); await expect(page).toHaveURL(/\/login$/); await login(page, 'dorm_manager'); await page.goto('/inspections')
  await expect(page.getByText('共 11 条记录', { exact: true })).toBeVisible()
  await page.getByLabel('所属楼栋', { exact: true }).selectOption(String(building.id)); await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('共 10 条记录', { exact: true })).toBeVisible()
  await page.getByLabel('搜索应检寝室', { exact: true }).fill('不存在'); await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('暂无符合条件的应检寝室。', { exact: true })).toBeVisible()
  await page.getByLabel('搜索应检寝室', { exact: true }).fill(''); await page.getByLabel('所属楼栋', { exact: true }).selectOption('')
  await page.getByLabel('检查状态', { exact: true }).selectOption('false'); await page.getByRole('button', { name: '查询', exact: true }).click()
  await page.getByRole('button', { name: '下一页', exact: true }).click(); await expect(page.getByRole('row')).toHaveCount(2)
  await page.getByRole('button', { name: '录入评分', exact: true }).click()
  const form = page.getByRole('dialog', { name: '寝室正式评分', exact: true })
  for (const label of ['地面分值', '摆放分值', '整洁分值']) await form.getByLabel(label, { exact: true }).fill('85')
  await form.getByRole('button', { name: '提交正式评分', exact: true }).click(); await expect(form).not.toBeVisible()
  await expect(page.getByText('共 10 条记录', { exact: true })).toBeVisible(); await expect(page.getByRole('button', { name: '上一页', exact: true })).toBeDisabled()
  await expect(page.getByTestId('batch-progress')).toContainText('1 / 11')
  await page.screenshot({ animations: 'disabled', path: `../output/playwright/task08-list-${page.viewportSize()!.width}.png`, fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  for (const role of ['student', 'maintainer']) {
    const context = await browser.newContext({ viewport: page.viewportSize()! }); const other = await context.newPage()
    try { await login(other, role); await expect(other.getByRole('link', { name: '本周卫生检查', exact: true })).toHaveCount(0); await other.goto('/inspections'); await expect(other).toHaveURL(/\/forbidden$/) }
    finally { await context.close() }
  }
})

async function fillScore(page: Page, values = ['80', '90', '70']) {
  const form = page.getByRole('dialog', { name: '寝室正式评分', exact: true })
  for (const [i, value] of values.entries()) await form.getByLabel(`${['地面', '摆放', '整洁'][i]}分值`, { exact: true }).fill(value)
  return form
}

test('个人草稿重登恢复，保存并下一间按A101→A102→B201跨楼栋连续完成', async ({ page }) => {
  await fixtures(page); await post(page, '/api/hygiene/batches/current/prepare', {}, 200)
  await page.getByRole('button', { name: '退出登录', exact: true }).click(); await expect(page).toHaveURL(/\/login$/)
  await login(page, 'dorm_manager'); await page.goto('/inspections')
  await page.getByRole('row').filter({ hasText: 'A101' }).getByRole('button', { name: '录入评分', exact: true }).click()
  const form = await fillScore(page, ['80', '', '70'])
  await form.getByRole('button', { name: '暂存个人草稿', exact: true }).click()
  await expect(form.getByRole('status')).toContainText('个人草稿已保存')
  await expect(page.getByTestId('batch-progress')).toContainText('0 / 3')
  await form.getByRole('button', { name: '返回', exact: true }).click()
  await page.getByRole('button', { name: '退出登录', exact: true }).click(); await expect(page).toHaveURL(/\/login$/)
  await login(page, 'dorm_manager'); await page.goto('/inspections')
  await page.getByTestId('personal-drafts').getByRole('button', { name: '恢复草稿', exact: true }).click()
  await expect(form.getByLabel('地面分值', { exact: true })).toHaveValue('80')
  await expect(form.getByLabel('摆放分值', { exact: true })).toHaveValue('')
  await expect(form.getByLabel('整洁分值', { exact: true })).toHaveValue('70')
  await page.screenshot({ animations: 'disabled', path: `../output/playwright/task09-draft-${page.viewportSize()!.width}.png` })
  for (const [index, number] of ['A101', 'A102', 'B201'].entries()) {
    await expect(form.getByRole('heading', { name: / · / })).toContainText(number)
    await fillScore(page)
    await form.getByRole('button', { name: '保存并下一间', exact: true }).click()
    if (index < 2) await expect(form.getByRole('heading', { name: / · / })).toContainText(['A102', 'B201'][index]!)
  }
  await expect(form).not.toBeVisible()
  await expect(page.getByRole('status')).toContainText('当前筛选范围已全部检查')
  await expect(page.getByTestId('batch-progress')).toContainText('3 / 3')
  expect((await (await page.request.get('/api/hygiene/drafts')).json()).total).toBe(0)
  const batch = (await (await page.request.get('/api/hygiene/batches/current')).json()).batch
  const rows = (await (await page.request.get(`/api/hygiene/batches/${batch.id}/rooms?inspected=true`)).json()).items
  expect(rows.map((r: { roomNo: string; totalScore: number }) => [r.roomNo, Number(r.totalScore)])).toEqual([['A101', 80], ['A102', 80], ['B201', 80]])
  await page.screenshot({ animations: 'disabled', path: `../output/playwright/task09-complete-${page.viewportSize()!.width}.png`, fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
})

test('连续提交的校验、网络和服务器失败保留草稿与输入，请求中不能重复点击', async ({ page }) => {
  await fixtures(page); await post(page, '/api/hygiene/batches/current/prepare', {}, 200)
  await page.getByRole('button', { name: '退出登录', exact: true }).click(); await expect(page).toHaveURL(/\/login$/)
  await login(page, 'dorm_manager'); await page.goto('/inspections')
  await page.getByRole('row').filter({ hasText: 'A101' }).getByRole('button', { name: '录入评分', exact: true }).click()
  const form = await fillScore(page, ['80.5', '90', '70'])
  await form.getByRole('button', { name: '暂存个人草稿', exact: true }).click()
  await expect(form.getByRole('status')).toContainText('个人草稿已保存')
  const batch = (await (await page.request.get('/api/hygiene/batches/current')).json()).batch
  const room = (await (await page.request.get(`/api/hygiene/batches/${batch.id}/rooms?q=A101`)).json()).items[0]
  const path = `/api/hygiene/batches/${batch.id}/rooms/${room.roomId}`
  const draft = (await (await page.request.get(`${path}/draft`)).json()).draft
  await form.getByRole('button', { name: '保存并下一间', exact: true }).click()
  await expect(form.getByRole('alert')).toContainText('0–100的整数')
  await fillScore(page)
  const pattern = '**/api/hygiene/batches/*/rooms/*/score'
  for (const failure of ['network', 'server']) {
    await page.route(pattern, route => failure === 'network' ? route.abort('failed') : route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 'SERVER_ERROR', message: '服务暂时不可用，请稍后重试' }) }))
    await form.getByRole('button', { name: '保存并下一间', exact: true }).click()
    await expect(form.getByRole('alert')).toContainText(failure === 'network' ? '暂时无法连接服务' : '服务暂时不可用')
    await expect(form.getByRole('heading', { name: / · / })).toContainText('A101')
    await expect(form.getByLabel('地面分值', { exact: true })).toHaveValue('80')
    expect((await (await page.request.get(`${path}/draft`)).json()).draft).toEqual(draft)
    expect((await (await page.request.get(path)).json()).score).toBeNull()
    await page.unroute(pattern)
  }
  await page.screenshot({ animations: 'disabled', path: `../output/playwright/task09-submit-failure-${page.viewportSize()!.width}.png` })
  let release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  let requests = 0
  await page.route(pattern, async route => { requests++; await gate; await route.continue() })
  await form.getByRole('button', { name: '保存并下一间', exact: true }).click()
  await expect.poll(() => requests).toBe(1)
  await expect(form.getByRole('button', { name: '保存并下一间', exact: true })).toBeDisabled()
  await expect(form.getByRole('button', { name: '暂存个人草稿', exact: true })).toBeDisabled()
  await expect(form.getByRole('button', { name: '返回', exact: true })).toBeDisabled()
  release()
  await expect(form.getByRole('heading', { name: / · / })).toContainText('A102')
  expect(requests).toBe(1)
  expect((await (await page.request.get(`${path}/draft`)).json()).draft).toBeNull()
  expect(Number((await (await page.request.get(path)).json()).score.totalScore)).toBe(80)
})

test('两名宿管草稿相互隔离，另一人先提交后冲突不跳间且重登仍可核对草稿', async ({ page, browser }) => {
  await fixtures(page); await post(page, '/api/hygiene/batches/current/prepare', {}, 200)
  await post(page, '/api/identity/accounts', { username: 'other_manager', displayName: '模拟另一宿管', role: 'DORM_MANAGER', password: 'DemoPass2026!' })
  await page.getByRole('button', { name: '退出登录', exact: true }).click(); await expect(page).toHaveURL(/\/login$/)
  await login(page, 'dorm_manager'); await page.goto('/inspections')
  const context = await browser.newContext({ viewport: page.viewportSize()! }); const other = await context.newPage()
  try {
    await login(other, 'other_manager'); await other.goto('/inspections')
    for (const actor of [page, other]) {
      await actor.getByRole('row').filter({ hasText: 'A101' }).getByRole('button', { name: '录入评分', exact: true }).click()
      const form = await fillScore(actor, actor === page ? ['80', '90', '70'] : ['95', '95', '95'])
      await form.getByRole('button', { name: '暂存个人草稿', exact: true }).click()
      await expect(form.getByRole('status')).toContainText('个人草稿已保存')
    }
    const otherForm = other.getByRole('dialog', { name: '寝室正式评分', exact: true })
    await otherForm.getByRole('button', { name: '提交正式评分', exact: true }).click(); await expect(otherForm).not.toBeVisible()
    const form = page.getByRole('dialog', { name: '寝室正式评分', exact: true })
    await form.getByRole('button', { name: '保存并下一间', exact: true }).click()
    await expect(form.getByRole('alert')).toContainText('已提交正式评分')
    await expect(form.getByRole('heading', { name: / · / })).toContainText('A101')
    await expect(form.getByLabel('地面分值', { exact: true })).toHaveValue('80')
    expect((await (await other.request.get('/api/hygiene/drafts')).json()).total).toBe(0)
    expect((await (await page.request.get('/api/hygiene/drafts')).json()).total).toBe(1)
    await form.getByRole('button', { name: '核对最新状态（保留输入）', exact: true }).click()
    await expect(form.getByText('正式得分', { exact: false })).toContainText('95.00')
    await expect(form.getByLabel('地面分值', { exact: true })).toHaveValue('80')
    await page.screenshot({ animations: 'disabled', path: `../output/playwright/task09-conflict-${page.viewportSize()!.width}.png` })
    await form.getByRole('button', { name: '返回', exact: true }).click()
    await page.getByRole('button', { name: '退出登录', exact: true }).click(); await expect(page).toHaveURL(/\/login$/)
    await login(page, 'dorm_manager'); await page.goto('/inspections')
    await page.getByTestId('personal-drafts').getByRole('button', { name: '恢复草稿', exact: true }).click()
    await expect(form.getByLabel('地面分值', { exact: true })).toHaveValue('80')
    await expect(form.getByLabel('地面分值', { exact: true })).toBeDisabled()
    await expect(form.getByText('正式得分', { exact: false })).toContainText('95.00')
  } finally { await context.close() }
})

test('连续队列遵守筛选范围，已保存但下一间查询失败可重试而不重复发布', async ({ page }) => {
  await fixtures(page); await post(page, '/api/hygiene/batches/current/prepare', {}, 200)
  await page.getByRole('button', { name: '退出登录', exact: true }).click(); await expect(page).toHaveURL(/\/login$/)
  await login(page, 'dorm_manager'); await page.goto('/inspections')
  const building = (await (await page.request.get('/api/housing/buildings')).json()).items.find((b: { name: string }) => b.name === '模拟 A 栋')
  await page.getByLabel('所属楼栋', { exact: true }).selectOption(String(building.id))
  await page.getByLabel('搜索应检寝室', { exact: true }).fill('A10')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('共 2 条记录', { exact: true })).toBeVisible()
  await page.getByRole('row').filter({ hasText: 'A101' }).getByRole('button', { name: '录入评分', exact: true }).click()
  const form = await fillScore(page)
  const pattern = '**/api/hygiene/batches/*/rooms?*'
  await page.route(pattern, route => new URL(route.request().url()).searchParams.get('size') === '1' ? route.abort('failed') : route.continue())
  await form.getByRole('button', { name: '保存并下一间', exact: true }).click()
  await expect(form.getByRole('alert')).toContainText('正式评分已保存，下一间暂未载入')
  await expect(form.getByRole('heading', { name: / · / })).toContainText('A101')
  await expect(form.getByRole('button', { name: '保存并下一间', exact: true })).toHaveCount(0)
  await page.unroute(pattern)
  await form.getByRole('button', { name: '继续下一间', exact: true }).click()
  await expect(form.getByRole('heading', { name: / · / })).toContainText('A102')
  await fillScore(page)
  await form.getByRole('button', { name: '保存并下一间', exact: true }).click(); await expect(form).not.toBeVisible()
  await expect(page.getByRole('status')).toContainText('当前筛选范围已全部检查')
  await expect(page.getByTestId('batch-progress')).toContainText('2 / 3')
  const batch = (await (await page.request.get('/api/hygiene/batches/current')).json()).batch
  const pending = (await (await page.request.get(`/api/hygiene/batches/${batch.id}/rooms?inspected=false`)).json()).items
  expect(pending.map((r: { roomNo: string }) => r.roomNo)).toEqual(['B201'])
})

test('草稿暂存断网保留输入，多窗口版本冲突须显式载入最新草稿', async ({ page }) => {
  await fixtures(page); await post(page, '/api/hygiene/batches/current/prepare', {}, 200)
  await page.getByRole('button', { name: '退出登录', exact: true }).click(); await expect(page).toHaveURL(/\/login$/)
  await login(page, 'dorm_manager'); await page.goto('/inspections')
  await page.getByRole('row').filter({ hasText: 'A101' }).getByRole('button', { name: '录入评分', exact: true }).click()
  const form = await fillScore(page)
  await form.getByRole('button', { name: '暂存个人草稿', exact: true }).click()
  await expect(form.getByRole('status')).toContainText('个人草稿已保存')
  await form.getByLabel('地面分值', { exact: true }).fill('70')
  const pattern = '**/api/hygiene/batches/*/rooms/*/draft'
  await page.route(pattern, route => route.request().method() === 'POST' ? route.abort('failed') : route.continue())
  await form.getByRole('button', { name: '暂存个人草稿', exact: true }).click()
  await expect(form.getByRole('alert')).toContainText('暂时无法连接服务')
  await expect(form.getByLabel('地面分值', { exact: true })).toHaveValue('70')
  await page.unroute(pattern)
  const saved = (await (await page.request.get('/api/hygiene/drafts')).json()).items[0]
  const path = `/api/hygiene/batches/${saved.batchId}/rooms/${saved.roomId}/draft`
  await post(page, path, { version: saved.version, dimensions: saved.dimensions.map((d: { dimensionId: number }) => ({ dimensionId: d.dimensionId, value: '95' })) }, 200)
  await form.getByRole('button', { name: '暂存个人草稿', exact: true }).click()
  await expect(form.getByRole('alert')).toContainText('另一窗口更新')
  await form.getByRole('button', { name: '核对最新状态（保留输入）', exact: true }).click()
  await expect(form.getByLabel('地面分值', { exact: true })).toHaveValue('70')
  await expect(form.getByLabel('地面分值', { exact: true })).toBeDisabled()
  await form.getByRole('button', { name: '载入服务端草稿（替换输入）', exact: true }).click()
  await expect(form.getByLabel('地面分值', { exact: true })).toHaveValue('95')
  await expect(form.getByLabel('地面分值', { exact: true })).toBeEnabled()
  await form.getByLabel('地面分值', { exact: true }).fill('96')
  await form.getByRole('button', { name: '暂存个人草稿', exact: true }).click()
  await expect(form.getByRole('status')).toContainText('个人草稿已保存')
  expect((await (await page.request.get(path)).json()).draft.version).toBe(3)
  await form.getByRole('button', { name: '返回', exact: true }).click()
  await page.route('**/api/hygiene/drafts?*', route => route.abort('failed'))
  await page.getByRole('button', { name: '刷新草稿', exact: true }).click()
  await expect(page.getByTestId('personal-drafts').getByRole('alert')).toContainText('暂时无法连接服务')
  await expect(page.getByTestId('personal-drafts').getByRole('button', { name: '恢复草稿', exact: true })).toHaveCount(0)
  await page.unroute('**/api/hygiene/drafts?*'); await page.getByRole('button', { name: '刷新草稿', exact: true }).click()
  await page.getByTestId('personal-drafts').getByRole('button', { name: '恢复草稿', exact: true }).click()
  await expect(form.getByLabel('地面分值', { exact: true })).toHaveValue('96')
})
