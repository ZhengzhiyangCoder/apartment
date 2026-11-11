import { test, expect } from '@playwright/test'

test('四角色可登录、刷新、查看本人资料并退出', async ({ page }) => {
  for (const [username, label] of [
    ['system_admin', '系统管理员'], ['dorm_manager', '宿舍管理员'],
    ['student', '学生'], ['maintainer', '维修人员'],
  ]) {
    await page.goto('/login')
    if (username === 'system_admin') await page.screenshot({ path: `../output/playwright/task01-login-${page.viewportSize()!.width}.png`, fullPage: true })
    await page.getByLabel('账号', { exact: true }).fill(username!)
    await page.getByLabel('密码', { exact: true }).fill('DemoPass2026!')
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page.getByRole('heading', { name: `${label}工作台`, exact: true })).toBeVisible()
    await page.reload()
    await expect(page.getByRole('heading', { name: `${label}工作台`, exact: true })).toBeVisible()
    if (username === 'student') await page.screenshot({ path: `../output/playwright/task01-workspace-${page.viewportSize()!.width}.png`, fullPage: true })
    await page.getByRole('link', { name: '个人资料', exact: true }).click()
    await expect(page.getByTestId('profile-username')).toHaveText(username!)
    if (username === 'student') await page.screenshot({ path: `../output/playwright/task01-profile-${page.viewportSize()!.width}.png`, fullPage: true })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
    await page.getByRole('button', { name: '退出登录', exact: true }).click()
    await expect(page.getByRole('heading', { name: '登录栖居' })).toBeVisible()
  }
})

for (const username of ['system_admin', 'dorm_manager', 'student', 'maintainer']) {
test(`${username} 改密校验失败保留输入，成功后使用新密码登录`, async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill('DemoPass2026!')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.getByRole('link', { name: '修改密码', exact: true }).click()
  await expect(page.getByRole('heading', { name: '修改密码', exact: true })).toBeVisible()
  await page.screenshot({ path: `../output/playwright/task01-password-${page.viewportSize()!.width}.png`, fullPage: true })
  await page.getByLabel('当前密码', { exact: true }).fill('wrong-password')
  await page.getByLabel('新密码', { exact: true }).fill('BrowserPass2026!')
  await page.getByLabel('确认新密码', { exact: true }).fill('BrowserPass2026!')
  await page.getByRole('button', { name: '更新密码', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('当前密码不正确')
  await expect(page.getByLabel('新密码', { exact: true })).toHaveValue('BrowserPass2026!')
  await page.getByLabel('当前密码', { exact: true }).fill('DemoPass2026!')
  await page.getByRole('button', { name: '更新密码', exact: true }).click()
  await expect(page.getByRole('heading', { name: '登录栖居' })).toBeVisible()
  await expect(page.getByRole('status')).toContainText('密码已更新')
  await page.getByLabel('账号', { exact: true }).fill(username)
  await page.getByLabel('密码', { exact: true }).fill('BrowserPass2026!')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.getByRole('link', { name: '修改密码', exact: true }).click()
  await page.getByLabel('当前密码', { exact: true }).fill('BrowserPass2026!')
  await page.getByLabel('新密码', { exact: true }).fill('DemoPass2026!')
  await page.getByLabel('确认新密码', { exact: true }).fill('DemoPass2026!')
  await page.getByRole('button', { name: '更新密码', exact: true }).click()
  await expect(page.getByRole('heading', { name: '登录栖居' })).toBeVisible()
})

}

test('登录网络失败保留输入，恢复后可重试', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill('student')
  await page.getByLabel('密码', { exact: true }).fill('DemoPass2026!')
  await page.route('**/api/auth/login', route => route.abort('failed'))
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('暂时无法连接服务')
  await expect(page.getByLabel('账号', { exact: true })).toHaveValue('student')
  await expect(page.getByLabel('密码', { exact: true })).toHaveValue('DemoPass2026!')
  await page.unroute('**/api/auth/login')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '学生工作台' })).toBeVisible()
})

test('根地址恢复本人入口，手工访问其他角色地址被拒', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('账号', { exact: true }).fill('system_admin')
  await page.getByLabel('密码', { exact: true }).fill('DemoPass2026!')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '系统管理员工作台' })).toBeVisible()
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '系统管理员工作台' })).toBeVisible()
  await page.goto('/workspace/STUDENT')
  await expect(page.getByRole('heading', { name: '此页面暂不可访问' })).toBeVisible()
  expect((await page.request.get('/api/identity/workspaces/STUDENT')).status()).toBe(403)
})
