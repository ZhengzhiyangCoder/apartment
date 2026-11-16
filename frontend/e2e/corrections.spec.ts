import { test, expect, type Page } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

async function login(page: Page, username: string) {
  if ((await page.request.get('/api/auth/me')).status() === 200) {
    const token = await (await page.request.get('/api/auth/csrf')).json()
    expect((await page.request.post('/api/auth/logout', { headers: { [token.headerName]: token.token } })).status()).toBe(204)
  }
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
async function formal(manager: Page, f: Awaited<ReturnType<typeof fixture>>, value=80) {
  return post(manager, `/api/hygiene/batches/${f.batch.id}/rooms/${f.room.id}/score`, { dimensions: f.batch.dimensions.map((d: {id:number}) => ({dimensionId:d.id,score:value})) })
}
async function openResult(page: Page) {
  await page.goto('/inspections');await page.getByRole('row').filter({hasText:'A101'}).getByRole('button',{name:'查看结果',exact:true}).click()
  return page.getByRole('dialog',{name:'寝室正式评分',exact:true})
}
test.beforeEach(() => { execFileSync(fileURLToPath(new URL('../../scripts/seed-demo.sh', import.meta.url)), ['--test-reset'], { stdio: 'pipe' }) })

test('管理员在关闭批次更正原成绩，保留前后值并联动整改', async ({page,browser}) => {
  const context=await browser.newContext({viewport:page.viewportSize()!});const manager=await context.newPage()
  try {
    const f=await fixture(page,manager);const original=await formal(manager,f)
    await post(page,`/api/hygiene/batches/${f.batch.id}/state`,{version:0,action:'CLOSE',reason:'模拟已完成检查'},200)
    const dialog=await openResult(page)
    await expect(dialog.getByText('暂无成绩更正记录。',{exact:true})).toBeVisible()
    await dialog.getByRole('button',{name:'更正成绩',exact:true}).click()
    await dialog.getByLabel('更正地面分值',{exact:true}).fill('50');await dialog.getByLabel('更正摆放分值',{exact:true}).fill('50')
    await dialog.getByRole('button',{name:'保存成绩更正',exact:true}).click();await expect(dialog.getByRole('alert')).toContainText('请填写更正原因')
    await dialog.getByLabel('更正原因',{exact:true}).fill('模拟原检查表误录，核对为50分')
    await dialog.getByRole('button',{name:'保存成绩更正',exact:true}).click();await expect(dialog.getByRole('status').filter({hasText:'成绩更正已保存'})).toBeVisible()
    await expect(dialog.getByRole('list',{name:'成绩更正记录',exact:true})).toContainText('80.00 → 50.00')
    const event=(await (await page.request.get('/api/hygiene/rectifications')).json()).items[0]
    await post(manager,`/api/hygiene/rectifications/${event.id}/follow-ups`,{version:event.version,note:'模拟原跟进，必须保留'},200)
    await dialog.getByRole('button',{name:'更正成绩',exact:true}).click();await dialog.getByLabel('更正地面分值',{exact:true}).fill('90');await dialog.getByLabel('更正摆放分值',{exact:true}).fill('90')
    await dialog.getByLabel('更正原因',{exact:true}).fill('模拟复核成绩达标90分')
    await dialog.getByRole('button',{name:'保存成绩更正',exact:true}).click();await expect(dialog.getByRole('status').filter({hasText:'成绩更正已保存'})).toBeVisible()
    await expect(dialog.getByRole('list',{name:'成绩更正记录',exact:true})).toContainText('50.00 → 90.00')
    await dialog.getByText('寝室正式评分',{exact:true}).scrollIntoViewIfNeeded();await page.screenshot({path:`../output/playwright/task14-history-${page.viewportSize()!.width}.png`,fullPage:true,animations:'disabled'})
    expect((await (await page.request.get(`/api/hygiene/scores/${original.id}`)).json()).id).toBe(original.id)
    await dialog.getByRole('button',{name:'返回',exact:true}).click();await page.getByRole('link',{name:'卫生整改',exact:true}).click()
    await page.getByRole('button',{name:'查看与跟进',exact:true}).click();const eventDialog=page.getByRole('dialog',{name:'整改详情与处理',exact:true})
    await expect(eventDialog).toContainText('触发成绩已更正');await expect(eventDialog).toContainText('当前成绩 90.00');await expect(eventDialog).toContainText('模拟原跟进，必须保留')
    await expect(eventDialog.getByTestId('rectification-state')).toContainText('待核实')
    await eventDialog.getByLabel('处理操作',{exact:true}).selectOption('verify');await eventDialog.getByLabel('处理说明',{exact:true}).fill('模拟现场复核更正后记录')
    await eventDialog.getByRole('button',{name:'保存处理记录',exact:true}).click();await expect(eventDialog).toContainText('已核实，可关闭')
    await eventDialog.getByLabel('处理操作',{exact:true}).selectOption('close');await eventDialog.getByLabel('处理说明',{exact:true}).fill('模拟人工关闭，保留原依据')
    await eventDialog.getByRole('button',{name:'保存处理记录',exact:true}).click();await expect(eventDialog.getByTestId('rectification-state')).toContainText('已关闭')
    await eventDialog.getByText('整改详情与处理',{exact:true}).scrollIntoViewIfNeeded();await page.screenshot({path:`../output/playwright/task14-linked-${page.viewportSize()!.width}.png`,fullPage:true,animations:'disabled'})
    const managerDialog=await openResult(manager);await expect(managerDialog.getByRole('button',{name:'更正成绩',exact:true})).toHaveCount(0)
    await expect(managerDialog.getByRole('list',{name:'成绩更正记录',exact:true})).toContainText('模拟复核成绩达标90分')
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy()
  } finally { await context.close() }
})

test('并发与网络失败保留更正输入，核对最新版本后可继续', async ({page,browser}) => {
  const context=await browser.newContext({viewport:page.viewportSize()!});const other=await context.newPage()
  try {
    const f=await fixture(page,other);const score=await formal(other,f);const path=`/api/hygiene/scores/${score.id}`
    await login(other,'system_admin');const dialog=await openResult(page)
    await dialog.getByRole('button',{name:'更正成绩',exact:true}).click()
    await dialog.getByLabel('更正地面分值',{exact:true}).fill('55');await dialog.getByLabel('更正摆放分值',{exact:true}).fill('45');await dialog.getByLabel('更正原因',{exact:true}).fill('模拟本窗口尚未保存的原因')
    await page.route(`**${path}/corrections`,route=>route.abort())
    await dialog.getByRole('button',{name:'保存成绩更正',exact:true}).click();await expect(dialog.getByRole('alert')).toContainText('暂时无法连接服务')
    await expect(dialog.getByLabel('更正原因',{exact:true})).toHaveValue('模拟本窗口尚未保存的原因');await page.unroute(`**${path}/corrections`)
    await post(other,`${path}/corrections`,{version:0,reason:'模拟其他管理员先更新',dimensions:f.batch.dimensions.map((d:{id:number})=>({dimensionId:d.id,score:90}))},200)
    await dialog.getByRole('button',{name:'保存成绩更正',exact:true}).click();await expect(dialog.getByRole('alert')).toContainText('成绩已被其他管理员更正')
    await expect(dialog.getByRole('button',{name:'保存成绩更正',exact:true})).toBeDisabled()
    await page.route(`**${path}`,route=>route.abort())
    await dialog.getByRole('button',{name:'核对最新成绩（保留输入）',exact:true}).click();await expect(dialog.getByRole('alert')).toContainText('暂时无法连接服务')
    await expect(dialog.getByLabel('更正地面分值',{exact:true})).toHaveValue('55');await page.unroute(`**${path}`)
    await dialog.getByRole('button',{name:'核对最新成绩（保留输入）',exact:true}).click();await expect(dialog.getByRole('status').filter({hasText:'已载入最新成绩'})).toBeVisible()
    await expect(dialog.getByLabel('更正原因',{exact:true})).toHaveValue('模拟本窗口尚未保存的原因');await expect(dialog.getByLabel('更正摆放分值',{exact:true})).toHaveValue('45')
    await expect(dialog.locator('.inspection-saved')).toContainText('90.00')
    await dialog.getByText('寝室正式评分',{exact:true}).scrollIntoViewIfNeeded();await page.screenshot({path:`../output/playwright/task14-conflict-${page.viewportSize()!.width}.png`,fullPage:true,animations:'disabled'})
    await dialog.getByRole('button',{name:'保存成绩更正',exact:true}).click();await expect(dialog.getByRole('status').filter({hasText:'成绩更正已保存'})).toBeVisible()
    await expect(dialog.locator('.inspection-saved')).toContainText('54.90');await expect(dialog.getByRole('list',{name:'成绩更正记录',exact:true})).toContainText('模拟其他管理员先更新')
    await expect(dialog.getByRole('list',{name:'成绩更正记录',exact:true})).toContainText('模拟本窗口尚未保存的原因')
  } finally {await context.close()}
})

test('更正历史失败可重试，分页保留前后值且离开页面后旧请求不回写', async ({page,browser}) => {
  const context=await browser.newContext({viewport:page.viewportSize()!});const manager=await context.newPage()
  try {
    const f=await fixture(page,manager);const score=await formal(manager,f);const path=`/api/hygiene/scores/${score.id}/corrections`
    for(let version=0;version<11;version++) await post(page,path,{version,reason:`模拟更正序号${version+1}`,dimensions:f.batch.dimensions.map((d:{id:number})=>({dimensionId:d.id,score:81+version}))},200)
    await page.route(`**${path}?*`,route=>route.abort());const dialog=await openResult(page)
    await expect(dialog.getByRole('alert')).toContainText('暂时无法连接服务');await expect(dialog.getByText('暂无成绩更正记录。',{exact:true})).toHaveCount(0)
    await page.unroute(`**${path}?*`);await dialog.getByRole('button',{name:'重试更正记录',exact:true}).click()
    await expect(dialog.getByRole('list',{name:'成绩更正记录',exact:true})).toContainText('模拟更正序号11')
    await dialog.getByRole('button',{name:'下一页更正',exact:true}).click();await expect(dialog.getByRole('list',{name:'成绩更正记录',exact:true})).toContainText('模拟更正序号1')
    await expect(dialog.getByRole('button',{name:'下一页更正',exact:true})).toBeDisabled()
    let release!:()=>void;const gate=new Promise<void>(resolve=>{release=resolve});let started!:()=>void;const pending=new Promise<void>(resolve=>{started=resolve});let finished!:()=>void;const completed=new Promise<void>(resolve=>{finished=resolve})
    await page.route(`**${path}?page=1&size=10`,async route=>{const response=await route.fetch();started();await gate;await route.fulfill({response});finished()})
    await dialog.getByRole('button',{name:'上一页更正',exact:true}).click();await pending
    await dialog.getByRole('button',{name:'返回',exact:true}).click()
    await page.getByRole('link',{name:'个人资料',exact:true}).click();release();await completed;await page.unroute(`**${path}?page=1&size=10`)
    await expect(page.getByRole('heading',{name:'认识自己的一方空间',exact:true})).toBeVisible();await expect(page.getByRole('list',{name:'成绩更正记录',exact:true})).toHaveCount(0)
  } finally {await context.close()}
})

test('更正当前值同步到学生管理页面，停检仍排除且其他角色不能更正', async ({page,browser}) => {
  const context=await browser.newContext({viewport:page.viewportSize()!});const student=await context.newPage()
  try {
    const f=await fixture(page,student);const score=await formal(student,f)
    await post(page,`/api/hygiene/scores/${score.id}/corrections`,{version:0,reason:'模拟统计更正为90',dimensions:f.batch.dimensions.map((d:{id:number})=>({dimensionId:d.id,score:90}))},200)
    await page.goto('/statistics');await expect(page.getByRole('table',{name:'楼栋成绩对比',exact:true})).toContainText('90.00');await expect(page.getByRole('table',{name:'寝室成绩排行',exact:true})).toContainText('90.00')
    await login(student,'student');await student.goto('/my-results');await expect(student.getByTestId('result-summary')).toContainText('90.00')
    await post(page,`/api/hygiene/batches/${f.batch.id}/state`,{version:0,action:'SUSPEND',reason:'模拟停检排除'},200)
    const dialog=await openResult(page);await dialog.getByRole('button',{name:'更正成绩',exact:true}).click();await dialog.getByLabel('更正地面分值',{exact:true}).fill('70');await dialog.getByLabel('更正摆放分值',{exact:true}).fill('70');await dialog.getByLabel('更正原因',{exact:true}).fill('模拟停检期间继续留痕纠错')
    await dialog.getByRole('button',{name:'保存成绩更正',exact:true}).click();await expect(dialog.getByRole('status').filter({hasText:'成绩更正已保存'})).toBeVisible()
    await student.reload();await expect(student.getByTestId('result-summary')).not.toContainText('70.00');await expect(student.getByRole('table',{name:'周期检查明细',exact:true})).toContainText('停检，不计成绩')
    for(const user of ['student','maintainer']) {
      await login(student,user);await post(student,`/api/hygiene/scores/${score.id}/corrections`,{version:2,reason:'无权更正',dimensions:f.batch.dimensions.map((d:{id:number})=>({dimensionId:d.id,score:10}))},403)
      expect((await student.request.get(`/api/hygiene/scores/${score.id}/corrections`)).status()).toBe(403)
      await student.goto('/inspections');await expect(student).toHaveURL(/\/forbidden$/)
    }
  } finally {await context.close()}
})

test('保存更正后的列表不会被迟到旧查询覆盖', async ({page,browser}) => {
  const context=await browser.newContext({viewport:page.viewportSize()!});const manager=await context.newPage()
  let release=()=>{}
  try {
    const f=await fixture(page,manager);await formal(manager,f);const dialog=await openResult(page)
    await dialog.getByRole('button',{name:'更正成绩',exact:true}).click()
    await dialog.getByLabel('更正地面分值',{exact:true}).fill('70');await dialog.getByLabel('更正摆放分值',{exact:true}).fill('70');await dialog.getByLabel('更正原因',{exact:true}).fill('模拟延迟查询期间保存更正')
    const gate=new Promise<void>(resolve=>{release=resolve});let started!:()=>void;const pending=new Promise<void>(resolve=>{started=resolve});let finished!:()=>void;const complete=new Promise<void>(resolve=>{finished=resolve});let first=true
    await page.route('**/api/hygiene/batches/*/rooms?*',async route=>{
      if(!first) {await route.continue();return}first=false
      const response=await route.fetch();started();await gate;await route.fulfill({response});finished()
    })
    await dialog.getByRole('button',{name:'核对最新成绩（保留输入）',exact:true}).click();await pending
    await expect(dialog.getByRole('status').filter({hasText:'已载入最新成绩'})).toBeVisible()
    await dialog.getByRole('button',{name:'保存成绩更正',exact:true}).click();await expect(dialog.getByRole('status').filter({hasText:'成绩更正已保存'})).toBeVisible()
    await dialog.getByRole('button',{name:'返回',exact:true}).click();release();await complete;await page.unroute('**/api/hygiene/batches/*/rooms?*')
    await expect(page.getByRole('row').filter({hasText:'A101'})).toContainText('70.00分')
  } finally {release();await context.close()}
})
