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
async function post(page: Page, path: string, data: object, status=201) {
  const csrf = await (await page.request.get('/api/auth/csrf')).json()
  const response = await page.request.post(path, { headers: { [csrf.headerName]: csrf.token }, data })
  expect(response.status(), await response.text()).toBe(status); return response.json()
}
async function audit(page: Page, action: string) {
  const response=await page.request.get(`/api/audit/records?action=${action}`);expect(response.status()).toBe(200)
  return (await response.json()).items[0]
}
async function query(page: Page, action: string) {
  await page.getByLabel('操作动作',{exact:true}).selectOption(action)
  await page.getByRole('button',{name:'查询记录',exact:true}).click()
  await expect(page.getByRole('table',{name:'操作记录',exact:true})).toBeVisible()
}
test.beforeEach(() => { execFileSync(fileURLToPath(new URL('../../scripts/seed-demo.sh', import.meta.url)), ['--test-reset'], { stdio: 'pipe' }) })

test('审计筛选分页、前后值与改名停用后的稳定来源定位', async ({page}) => {
  await login(page,'system_admin')
  for(let i=0;i<12;i++) await post(page,'/api/housing/buildings',{name:`模拟审计楼${i}`})
  const original=await audit(page,'BUILDING_CREATED')
  await post(page,`/api/housing/buildings/${original.targetId}`,{name:'模拟楼已改名',version:0},200)
  await post(page,`/api/housing/buildings/${original.targetId}/enabled`,{enabled:false,version:1},200)
  await page.getByRole('link',{name:'管理操作审计',exact:true}).click()
  await expect(page.getByRole('heading',{name:'管理操作审计',exact:true})).toBeVisible()
  await query(page,'BUILDING_CREATED');await expect(page.getByText('共 12 条记录',{exact:true})).toBeVisible()
  await page.getByRole('button',{name:'下一页',exact:true}).click();await expect(page.getByRole('button',{name:'查看记录',exact:true})).toHaveCount(2)
  await page.getByLabel('排列顺序',{exact:true}).selectOption('OLDEST');await page.getByRole('button',{name:'查询记录',exact:true}).click()
  await expect(page.getByRole('button',{name:'查看记录',exact:true})).toHaveCount(10)
  await query(page,'BUILDING_UPDATED');await page.getByRole('button',{name:'查看记录',exact:true}).click()
  const dialog=page.getByRole('dialog',{name:'操作审计详情',exact:true})
  await expect(dialog).toContainText('模拟审计楼11');await expect(dialog).toContainText('模拟楼已改名')
  await expect(dialog.getByRole('table',{name:'操作前后对照',exact:true})).toBeVisible()
  await page.screenshot({path:`../output/playwright/task15-details-${page.viewportSize()!.width}.png`,fullPage:true,animations:'disabled'})
  await dialog.getByRole('link',{name:'住宿资料',exact:true}).click()
  await expect(page.getByText(`已定位记录 #${original.targetId}`,{exact:true})).toBeVisible()
  await expect(page.getByRole('table')).toContainText('模拟楼已改名');await expect(page.getByRole('table')).toContainText('已停用')
  await expect(page.getByRole('table').getByRole('row')).toHaveCount(2)
  await page.getByRole('button',{name:'退出记录定位',exact:true}).click()
  await expect(page.getByText(`已定位记录 #${original.targetId}`,{exact:true})).toHaveCount(0)
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy()
})

async function workflow(page: Page) {
  await login(page,'system_admin')
  const rooms=(await (await page.request.get('/api/housing/rooms')).json()).items
  const first=rooms.find((r:{roomNo:string})=>r.roomNo==='A101'),second=rooms.find((r:{roomNo:string})=>r.roomNo==='B201')
  const bed1=(await (await page.request.get(`/api/housing/beds?roomId=${first.id}`)).json()).items[0]
  const bed2=(await (await page.request.get(`/api/housing/beds?roomId=${second.id}`)).json()).items[0]
  const profile=(await (await page.request.get('/api/housing/students')).json()).items[0]
  const residency=await post(page,'/api/housing/residencies',{studentId:profile.id,bedId:bed1.id})
  const account=(await (await page.request.get('/api/identity/accounts?q=maintainer')).json()).items[0]
  const worker=await post(page,'/api/identity/accounts',{username:'audit_worker',displayName:'模拟替班维修',role:'MAINTAINER',password:'DemoPass2026!'})
  const scheme=await post(page,'/api/hygiene/schemes',{name:'模拟审计标准',dimensions:[{name:'地面',weight:100}]})
  await post(page,`/api/hygiene/schemes/${scheme.id}/activate`,{version:0,activeId:0},200)
  await login(page,'student')
  const csrf=await (await page.request.get('/api/auth/csrf')).json()
  const uploaded=await page.request.post('/api/repairs',{headers:{[csrf.headerName]:csrf.token},multipart:{category:'OTHER',description:'模拟审计用报修',requestId:crypto.randomUUID()}})
  expect(uploaded.status()).toBe(201);const order=await uploaded.json()
  await login(page,'dorm_manager')
  await post(page,`/api/repairs/${order.id}/assign`,{version:0,assigneeId:account.id},200)
  await post(page,`/api/repairs/${order.id}/reassign`,{version:1,assigneeId:worker.id,reason:'模拟原维修人员请假'},200)
  await login(page,'system_admin')
  await post(page,`/api/housing/residencies/${residency.id}/transfer`,{bedId:bed2.id,note:'模拟就近调寝'},200)
  const batch=await post(page,'/api/hygiene/batches/current/prepare',{},200)
  await post(page,`/api/hygiene/batches/${batch.id}/rooms/${second.id}/remove`,{version:0,reason:'模拟名单复核'},200)
  await post(page,`/api/hygiene/batches/${batch.id}/rooms/${second.id}/add`,{version:1,reason:'模拟恢复名单'},200)
  await login(page,'dorm_manager')
  const score=await post(page,`/api/hygiene/batches/${batch.id}/rooms/${second.id}/score`,{dimensions:[{dimensionId:batch.dimensions[0].id,score:80}]})
  await login(page,'system_admin')
  await post(page,`/api/hygiene/scores/${score.id}/corrections`,{version:0,reason:'模拟原成绩80更正50',dimensions:[{dimensionId:batch.dimensions[0].id,score:50}]},200)
  return {batch,order,second,scheme,worker}
}
test('从调寝重分配名单更正和整改审计定位原业务记录', async ({page}) => {
  const f=await workflow(page)
  for(const [action,reason,link] of [
    ['RESIDENCY_TRANSFERRED','模拟就近调寝','原住宿记录'],['REPAIR_REASSIGNED','模拟原维修人员请假','工单详情与状态日志'],
    ['BATCH_ROOM_REMOVED','模拟名单复核','批次与名单变更记录'],['SCORE_CORRECTED','模拟原成绩80更正50','正式成绩与更正明细'],
    ['RECTIFICATION_TRIGGERED','此操作未要求单独填写原因。','整改依据与处理记录'],['SCHEME_ACTIVATED','此操作未要求单独填写原因。','评分方案版本'],
  ]) {
    const entry=await audit(page,action!);await page.goto(`/audit?record=${entry.id}`)
    const dialog=page.getByRole('dialog',{name:'操作审计详情',exact:true});await expect(dialog).toContainText(reason!)
    await dialog.getByRole('link',{name:link!,exact:true}).click();await expect(dialog).toHaveCount(0)
    if(action==='RESIDENCY_TRANSFERRED') {await expect(page.getByRole('table')).toContainText('已调寝');await expect(page.getByRole('table')).toContainText('模拟就近调寝')}
    if(action==='REPAIR_REASSIGNED') {const source=page.getByRole('dialog',{name:`工单详情 #${f.order.id}`,exact:true});await expect(source).toContainText('模拟原维修人员请假');await expect(source).toContainText('模拟替班维修')}
    if(action==='BATCH_ROOM_REMOVED') {await expect(page.getByRole('table',{name:'本周应检寝室',exact:true})).toContainText('B201')}
    if(action==='SCORE_CORRECTED') {
      const source=page.getByRole('dialog',{name:'寝室正式评分',exact:true});await expect(source.getByRole('list',{name:'成绩更正记录',exact:true})).toContainText('80.00 → 50.00')
      await page.screenshot({path:`../output/playwright/task15-score-source-${page.viewportSize()!.width}.png`,fullPage:true,animations:'disabled'})
    }
    if(action==='RECTIFICATION_TRIGGERED') {await expect(page.getByRole('dialog',{name:'整改详情与处理',exact:true})).toContainText('50.00')}
    if(action==='SCHEME_ACTIVATED') {await expect(page.getByRole('dialog',{name:`方案详情 #${f.scheme.id}`,exact:true})).toContainText('模拟审计标准')}
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy()
  }
})

test('宿管仅查本人记录，学生维修人员沿授权工单查看进度', async ({page}) => {
  const f=await workflow(page),adminEntry=await audit(page,'SCORE_CORRECTED'),managerEntry=await audit(page,'REPAIR_REASSIGNED')
  await login(page,'dorm_manager');await page.getByRole('link',{name:'本人操作记录',exact:true}).click()
  await expect(page.getByRole('heading',{name:'本人操作记录',exact:true})).toBeVisible()
  await expect(page.getByLabel('操作者 ID',{exact:true})).toHaveCount(0)
  await expect(page.getByRole('table',{name:'操作记录',exact:true})).toContainText('重分配维修人员')
  await page.getByLabel('操作动作',{exact:true}).selectOption('SCORE_CORRECTED');await page.getByRole('button',{name:'查询记录',exact:true}).click();await expect(page.getByText('暂无符合条件的操作记录。',{exact:true})).toBeVisible()
  expect((await page.request.get(`/api/audit/records?actorId=${adminEntry.actorId}`)).status()).toBe(403)
  await page.goto(`/audit?record=${adminEntry.id}`);await expect(page.getByRole('dialog',{name:'操作审计详情',exact:true})).toContainText('记录不存在或无访问权限')
  await page.goto(`/audit?record=${managerEntry.id}`);await expect(page.getByRole('dialog',{name:'操作审计详情',exact:true})).toContainText('模拟原维修人员请假')
  await page.screenshot({path:`../output/playwright/task15-own-${page.viewportSize()!.width}.png`,fullPage:true,animations:'disabled'})
  for(const username of ['student','audit_worker']) {
    await login(page,username);await expect(page.getByRole('link',{name:'管理操作审计',exact:true})).toHaveCount(0)
    expect((await page.request.get('/api/audit/records')).status()).toBe(403)
    expect((await page.request.get(`/api/audit/records/${managerEntry.id}`)).status()).toBe(403)
    await page.goto('/audit');await expect(page).toHaveURL(/\/forbidden$/)
    await page.goto(`/repairs?record=${f.order.id}`);await expect(page.getByRole('dialog',{name:`工单详情 #${f.order.id}`,exact:true})).toContainText('模拟原维修人员请假')
  }
  await login(page,'maintainer');await page.goto(`/repairs?record=${f.order.id}`)
  await expect(page.getByRole('dialog',{name:`工单详情 #${f.order.id}`,exact:true})).toContainText('无访问权限')
})

test('审计查询失败可重试，关闭详情后迟到响应不覆盖新记录', async ({page}) => {
  await login(page,'system_admin')
  await post(page,'/api/housing/buildings',{name:'模拟先前记录'})
  const first=await audit(page,'BUILDING_CREATED')
  await post(page,'/api/housing/buildings',{name:'模拟最新记录'})
  const latest=await audit(page,'BUILDING_CREATED')
  await page.route('**/api/audit/records?*',route=>route.abort())
  await page.goto('/audit');await expect(page.getByRole('alert')).toContainText('暂时无法连接服务')
  await page.unroute('**/api/audit/records?*');await page.getByRole('button',{name:'重新加载记录',exact:true}).click()
  await expect(page.getByRole('button',{name:'查看记录',exact:true})).toHaveCount(2)
  await page.getByLabel('开始日期',{exact:true}).fill('2099-01-01');await page.getByLabel('结束日期',{exact:true}).fill('2020-01-01')
  await page.getByRole('button',{name:'查询记录',exact:true}).click();await expect(page.getByRole('alert')).toContainText('筛选范围')
  await expect(page.getByLabel('开始日期',{exact:true})).toHaveValue('2099-01-01')
  await page.getByLabel('开始日期',{exact:true}).fill('');await page.getByLabel('结束日期',{exact:true}).fill('');await page.getByRole('button',{name:'查询记录',exact:true}).click()
  await page.route(`**/api/audit/records/${latest.id}`,route=>route.abort())
  await page.getByRole('button',{name:'查看记录',exact:true}).first().click()
  const dialog=page.getByRole('dialog',{name:'操作审计详情',exact:true});await expect(dialog.getByRole('alert')).toContainText('暂时无法连接服务')
  await page.unroute(`**/api/audit/records/${latest.id}`);await dialog.getByRole('button',{name:'重新加载详情',exact:true}).click();await expect(dialog).toContainText('模拟最新记录')
  await dialog.getByRole('button',{name:'关闭详情',exact:true}).click()
  let release!:()=>void,handled!:()=>void
  const gate=new Promise<void>(resolve=>{release=resolve}),done=new Promise<void>(resolve=>{handled=resolve})
  const delayed=await (await page.request.get(`/api/audit/records/${first.id}`)).json()
  await page.route(`**/api/audit/records/${first.id}`,async route=>{await gate;await route.fulfill({json:delayed});handled()})
  await page.getByRole('button',{name:'查看记录',exact:true}).last().click();await expect(dialog).toContainText('正在读取记录详情')
  await dialog.getByRole('button',{name:'关闭详情',exact:true}).click();await page.getByRole('button',{name:'查看记录',exact:true}).first().click()
  await expect(dialog).toContainText('模拟最新记录');release();await done;await page.unroute(`**/api/audit/records/${first.id}`)
  await expect(dialog).not.toContainText('模拟先前记录');await expect(dialog).toContainText('模拟最新记录')
})

test('账号和学生资料来源保留精确定位，权限撤销后审计会话失效', async ({page,browser}) => {
  await login(page,'system_admin')
  const account=await post(page,'/api/identity/accounts',{username:'audit_profile',displayName:'模拟资料学生',role:'STUDENT',password:'DemoPass2026!'})
  const profile=await post(page,'/api/housing/students',{userId:account.id,studentNo:'audit_profile',fullName:'模拟资料学生',college:'模拟学院',major:'模拟专业',className:'模拟班级'})
  for(const action of ['ACCOUNT_CREATED','STUDENT_CREATED']) {
    const entry=await audit(page,action);await page.goto(`/audit?record=${entry.id}`)
    await page.getByRole('dialog',{name:'操作审计详情',exact:true}).getByRole('link').click();await expect(page.getByRole('dialog',{name:'操作审计详情',exact:true})).toHaveCount(0)
    await expect(page.getByRole('table')).toContainText('audit_profile');await expect(page.getByRole('table').getByRole('row')).toHaveCount(2)
    await expect(page.getByText(`已定位记录 #${action==='ACCOUNT_CREATED'?account.id:profile.id}`,{exact:true})).toBeVisible()
  }
  const manager=(await (await page.request.get('/api/identity/accounts?q=dorm_manager')).json()).items[0]
  const context=await browser.newContext({viewport:page.viewportSize()!}),other=await context.newPage()
  try {
    await login(other,'dorm_manager');await other.goto('/audit');await expect(other.getByText('暂无符合条件的操作记录。',{exact:true})).toBeVisible()
    await post(page,`/api/identity/accounts/${manager.id}/enabled`,{version:manager.version,enabled:false},200)
    await other.getByRole('button',{name:'刷新记录',exact:true}).click();await expect(other).toHaveURL(/\/login\?accountChanged=1$/)
  } finally {await context.close()}
})

test('审计按原值识别变化，自由文本不套用枚举或时间翻译', async ({page}) => {
  await login(page,'system_admin')
  for(const [before,after] of [['ACTIVE','启用'],['2026-11-16T','2026-11-16T01:00:00Z']]) {
    const item=await post(page,'/api/housing/buildings',{name:before})
    await post(page,`/api/housing/buildings/${item.id}`,{name:after,version:0},200)
    const entry=await audit(page,'BUILDING_UPDATED');await page.goto(`/audit?record=${entry.id}`)
    const table=page.getByRole('table',{name:'操作前后对照',exact:true})
    await expect(table.getByText(before!,{exact:true})).toBeVisible();await expect(table.getByText(after!,{exact:true})).toBeVisible()
    await expect(page.getByText('必要字段没有变化，请结合动作及业务记录查看。',{exact:true})).toHaveCount(0)
  }
})

function moveFixtureToPreviousWeek(id: number) {
  expect(Number.isSafeInteger(id)&&id>0).toBeTruthy()
  const args=['compose','--env-file','deploy/.env','-f','deploy/compose.yaml','exec','-T','mysql','sh','-c','MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --skip-column-names --default-character-set=utf8mb4 -u apartment_app apartment_test']
  const cwd=fileURLToPath(new URL('../..',import.meta.url))
  expect(execFileSync('docker',args,{cwd,input:'SELECT DATABASE();',encoding:'utf8'}).trim()).toBe('apartment_test')
  // Reuse the approved clock fixture boundary; business writes still use public APIs.
  execFileSync('docker',args,{cwd,input:`UPDATE hygiene_batch SET iso_year=FLOOR(YEARWEEK(DATE_SUB(week_start,INTERVAL 7 DAY),3)/100), iso_week=MOD(YEARWEEK(DATE_SUB(week_start,INTERVAL 7 DAY),3),100), week_start=DATE_SUB(week_start,INTERVAL 7 DAY) WHERE id=${id};`,stdio:['pipe','pipe','pipe']})
}
test('审计来源加载期间切换批次会取消旧的成绩自动定位', async ({page}) => {
  const f=await workflow(page),entry=await audit(page,'SCORE_CORRECTED')
  moveFixtureToPreviousWeek(f.batch.id)
  const current=await post(page,'/api/hygiene/batches/current/prepare',{},200)
  await login(page,'dorm_manager')
  await post(page,`/api/hygiene/batches/${current.id}/rooms/${f.second.id}/score`,{dimensions:[{dimensionId:current.dimensions[0].id,score:95}]})
  await login(page,'system_admin')
  const body=await (await page.request.get(`/api/hygiene/batches/${f.batch.id}/rooms?size=10`)).json()
  let release!:()=>void,arrived!:()=>void,finished!:()=>void
  const gate=new Promise<void>(resolve=>{release=resolve}),received=new Promise<void>(resolve=>{arrived=resolve}),done=new Promise<void>(resolve=>{finished=resolve})
  const pattern=`**/api/hygiene/batches/${f.batch.id}/rooms?*`
  await page.route(pattern,async route=>{arrived();await gate;await route.fulfill({json:body});finished()})
  try {
    await page.goto(`/audit?record=${entry.id}`)
    await page.getByRole('dialog',{name:'操作审计详情',exact:true}).getByRole('link',{name:'正式成绩与更正明细',exact:true}).click()
    await received;await page.getByRole('button',{name:'历史批次',exact:true}).click()
    await page.getByRole('dialog',{name:'选择检查批次',exact:true}).getByRole('row').filter({hasText:current.weekStart}).getByRole('button',{name:'进入批次',exact:true}).click()
    await expect(page.getByRole('table',{name:'本周应检寝室',exact:true})).toContainText('95.00')
    release();await done
    // Settle this deliberately held network response and any resulting detail fetch before asserting absence.
    await page.waitForLoadState('networkidle')
    await expect(page.getByRole('dialog',{name:'寝室正式评分',exact:true})).toHaveCount(0)
    await expect(page.getByRole('table',{name:'本周应检寝室',exact:true})).toContainText('95.00')
  } finally {release();await done;await page.unroute(pattern)}
})

test('降为宿管后本人旧审计不能把学生资料来源替换为楼栋', async ({page,browser}) => {
  await login(page,'system_admin')
  const account=await post(page,'/api/identity/accounts',{username:'former_admin',displayName:'模拟原系统管理员',role:'SYSTEM_ADMIN',password:'DemoPass2026!'})
  const student=await post(page,'/api/identity/accounts',{username:'audit_demotion_student',displayName:'模拟资料学生',role:'STUDENT',password:'DemoPass2026!'})
  const context=await browser.newContext({viewport:page.viewportSize()!}),other=await context.newPage()
  try {
    await login(other,'former_admin')
    const profile=await post(other,'/api/housing/students',{userId:student.id,studentNo:'audit_demotion_student',fullName:'模拟资料学生',college:'模拟学院',major:'模拟专业',className:'模拟班级'})
    const entry=await audit(other,'STUDENT_CREATED')
    await post(page,`/api/identity/accounts/${account.id}/role`,{version:0,role:'DORM_MANAGER'},200)
    await other.goto('/login');await other.getByLabel('账号',{exact:true}).fill('former_admin');await other.getByLabel('密码',{exact:true}).fill('DemoPass2026!');await other.getByRole('button',{name:'登录',exact:true}).click()
    await expect(other.getByRole('link',{name:'本人操作记录',exact:true})).toBeVisible()
    await other.goto(`/audit?record=${entry.id}`)
    await other.getByRole('dialog',{name:'操作审计详情',exact:true}).getByRole('link',{name:'住宿资料',exact:true}).click()
    await expect(other).toHaveURL(/\/forbidden$/)
    expect((await other.request.get(`/api/housing/students/${profile.id}`)).status()).toBe(403)
    await page.goto('/housing?kind=unsupported&record=1')
    await expect(page.getByRole('alert')).toContainText('资料分类无效')
    await expect(page.getByRole('table').getByRole('row')).toHaveCount(1)
  } finally {await context.close()}
})
