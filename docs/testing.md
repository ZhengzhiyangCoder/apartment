# 测试与验收记录 — 任务 01–03

日期：2026-11-11（Asia/Shanghai）。任务 01–03 验证完成；后续业务及整套系统验收尚未完成。

## 边界与隔离

采用已批准 Spec §5.1：公开 HTTP API → 实际 Spring Boot → 真实 MySQL，不使用 H2 或内存账号代替；从 API 返回结果验证行为。任务 01 的测试 SQL 仅准备样本。任务 02 的账号行为仍通过 API 观察；针对任务票明确要求的审计持久化、密码不明文存储和失败回滚，另核验真实数据库存储契约（不调用私有服务或 mock 调用次数），并在测试库添加/移除临时 CHECK 约束注入审计故障。

API 测试位于后端 IdentityHttpTest，运行前核对数据库产品为 MySQL、库名为 apartment_test，再清理该库模拟账号。浏览器测试位于 frontend/e2e，使用真实 Chrome、前端 5175 和后端 8082，自动启停且不复用未知服务；同样只使用 apartment_test。

## 已执行的红绿循环

| 路径 | 实现前失败 | 实现后 |
|---|---|---|
| 四角色登录、本人资料 | CSRF 入口预期 200，实际 401 | 通过 |
| 本人角色入口、越权拒绝 | 本人入口预期 200，实际 404 | 通过 |
| 改密持久化与旧会话撤销 | 改密校验预期 400，实际 404 | 通过 |

任务 01 当时 6 项 API 测试通过，涵盖四角色、禁用/错误账号、会话与 CSRF 轮换、HttpOnly Cookie、退出旧 Cookie 重放、角色参数篡改、改密非法输入和旧会话撤销。初始 Flyway 迁移已在真实 MySQL 8.4 的干净 apartment_test 执行。

## 最终验证结果

- 后端 `./scripts/backend.sh -B verify`：6 项真实 HTTP / MySQL 测试通过，JAR 打包成功。证据：`backend/target/surefire-reports/`，本轮命令日志 `.runtime/task01-backend-final.log`。
- 前端 `npm run build`：vue-tsc 类型检查和 Vite 构建通过，package-lock 固定依赖。
- 浏览器 `npm run test:e2e`：14 项通过（每种尺寸 7 项）。1366×768 与 1920×1080 均覆盖四角色登录/刷新/本人资料/退出、四角色分别改密/新密码登录/恢复、网络失败保留输入重试、根地址身份恢复和跨角色地址拒绝。总结果日志 `.runtime/task01-browser-final.log`。
- 实际查看[界面截图](../output/playwright/)：task01-login、workspace、profile、password 两种宽度。改密截图增加导航完成等待后单独重取；应用代码未变。
- 干净的 apartment_demo、apartment_test 均实际执行 Flyway V1；后端与 MySQL 停止/重新启动后，维修模拟账号身份和修改后的密码保留。重复加载演示账号也未重置密码，检查后已恢复初始演示密码。证据 `.runtime/task01-persistence-result.json`。
- 真实停止本项目数据库时，原登录响应错误归类为 401；修复后复测为安全 500 SERVER_ERROR，无堆栈/SQL返回，finally 自动恢复容器。证据 `.runtime/task01-red-database-failure.json`、`.runtime/task01-green-database-failure.json`。
- [双轴代码审查](../.codex-work/logs/20261111-task01-review.md)：Standards 原 1 项、Spec 原 2 项，全部修复并分别复核，无剩余发现。

最初浏览器红测为账号表单缺失，随后补齐真实页面；改密表单缺失和已登录系统管理员根地址误判也分别由红测发现并修正。测试不是基于原型模拟。

## 环境与验收边界

本轮实际环境：本机 macOS / arm64，Java 17.0.12、Maven Wrapper 3.9.11、Node 24.18.0、npm 11.16.0、MySQL 8.4、Playwright 1.65.0 与本机 Chrome。依赖锁定在 pom.xml、Wrapper properties 与 package-lock.json；具体浏览器版本由运行环境提供，不声称其他浏览器已验证。

本任务覆盖 AC-01 的登录/退出/基础禁用校验部分、AC-23 的当前账号页面部分、AC-24 的最小启动/迁移部分，不能把共享 AC 整项或 Spec 全部标为已通过。此段记录任务 01 当时范围；任务 02 增量与全量回归见下节，住宿、报修、卫生等仍属于任务 03–18。

性能验收归任务 17，本轮未执行 5,000 学生/1,000 寝室规模测量，不把测试耗时当性能成绩。Windows、其他操作系统、Safari/Firefox 和公网部署未验证。运行时测试输出可重新生成，稳定结论保存在本文及项目日志。


## 任务 02 增量与最终回归

- 后端 `./scripts/backend.sh -B verify`：**12 项通过**，包含原 6 项身份测试与新增 6 项账号管理测试，JAR 打包成功。证据：`.runtime/task02-backend-final.log`。
- 前端 `cd frontend && npm run build`：vue-tsc 和 Vite 构建通过，依赖未新增。证据：`.runtime/task02-frontend-build.log`。
- 浏览器 `cd frontend && npm run test:e2e`：**22 项通过**，每尺寸原 7 项身份场景与新增 4 项账号管理场景，合计 1.9 分钟。证据：`.runtime/task02-browser-final.log`。API 与浏览器共用 apartment_test，必须顺序运行。
- 新增 API 场景：创建/唯一名冲突/查询分页/新账号登录；角色切换、密码重置、启停与旧会话撤销；匿名及三类非管理角色越权、篡改标识和 CSRF 拒绝；非法查询/输入/版本冲突；审计失败回滚；并发最后管理员保护。
- 审计失败测试在 apartment_test 的 operation_log 临时添加拒绝指定动作的 CHECK，调用真实 HTTP，核验创建不存在、旧密码仍有效、旧会话仍有效、版本未变且无残留日志，finally 清除约束。未触及演示库。
- 新增浏览器场景：实际创建→另一会话登录→重置→角色变更→禁用→启用；重复名与断网保留输入/恢复重试/并发版本冲突；三类非管理角色无入口且直访被拒；查询分页/末页项移出后的回退/空数据/最后管理员保护。
- 红绿证据：创建接口原 403→201；角色等变更原 403→200；管理入口不存在→通过；末页唯一账号停用后原空页→回到有效页。不是只在实现后补成功测试。
- V2 升级分别在真实 apartment_test 与 apartment_demo 从 V1 成功迁移；演示后端重启后经 5174 代理登录及账号查询成功，原四个演示账号保留。没有重建或清空演示库。
- [双轴审查](../.codex-work/logs/20261111-task02-review.md)：Standards 0 项，Spec 0 项，固定差异 19077d3...6ff2716。
- [界面证据](../output/playwright/)：task02-accounts、task02-create、task02-disable，1366 与 1920 两种宽度；已查看列表、错误保留输入与确认窗口，目标尺寸下操作无影响性遮挡或横向溢出。截图等待查询完成后另选择性复测 2 项生命周期场景通过（`.runtime/task02-screenshots-final.log`），不改变功能代码，不重复计算独立场景数；HTML 报告为最近这次选择性运行，全量结果保留于上述日志。

任务 02 的 6 条准则已完成，覆盖 US-03 及 AC-01/22/23 的本任务部分。任务 02 完成时尚无住宿/工单/卫生表；任务 03 的后续增量见下节，未来引入业务时的停用限制仍由 04/05/06/08/13 实现；全系统审计查询由 15 交付，不把本轮记录能力当作已完成查询功能。性能与其他环境边界仍如前述。

## 任务 03 最终验证

新增 HousingHttpTest 通过公开接口验证楼栋、寝室、床位、学生资料；共用 HttpIntegrationTest 仅复用既有登录、CSRF、请求与隔离准备。保留原 IdentityHttpTest 的 12 项测试正文。资料列表用于验证持久化；针对审计存储契约检查 actor、时间和前后快照，不检查私有方法调用。

- `./scripts/backend.sh -B verify`：**20 项通过**（原 12 + 住宿 8），JAR 构建通过。原始证据 `.runtime/task03-backend-final.log`；审查修正后再次 20 项通过，最终证据 `.runtime/task03-backend-review-fix.log`。
- `npm --prefix frontend run build`：类型检查与构建通过。证据 `.runtime/task03-frontend-build.log`。
- `npm --prefix frontend run test:e2e -- housing.spec.ts`：两种目标尺寸 **8 项通过**。证据 `.runtime/task03-housing-browser-fixed.log`。全量浏览器原始 30 项通过（.runtime/task03-browser-final.log），审查修正后再次 **30 项通过**，最终证据 `.runtime/task03-browser-review-fix.log`。三份结果不重复累计为更多场景。
- 8 项住宿接口场景涵盖四类资料维护与分页、父级启停、账号关联及角色一致性、权限和 CSRF/无硬删除、非法输入、审计故障整体回滚、并发房号/床号/版本/角色竞争、审计操作者及前后数据。
- 审计故障测试仅在 apartment_test 清理准备阶段审计，再添加拒绝新住房审计的 CHECK：四类资料各自创建/编辑/停用共 12 次真实写入均报安全 500，列表内容、状态、版本和日志数量保持不变，finally 移除 CHECK。
- 新建楼栋、寝室床位、学生资料接口红测 403 → 通过；页面入口缺失 → 完整维护通过；小数楼层 1.5 原被转换为 1 → 改为 400。浏览器登录/退出等待不足产生的 401/超时已修正测试同步，不将其当产品功能缺陷。
- [截图](../output/playwright/) task03-rooms、room-conflict、student-form、students，两种宽度，共 8 张；已查看寝室查询、重复房号反馈、学生表单失败保留内容及学籍列表。长表单允许对话框内滚动，保存与取消实际点击通过，无页面横向溢出。

本轮覆盖 US-04/05/06 和任务 03 对 AC-02/22/23 的分工；不宣称入住/历史流转、后续业务关联限制、全系统审计查询或性能验收完成。新增数据库结构以 V3–V5 迁移为准；不改 V1/V2、已定稿 Spec、工程结构或第一版原型。

- [独立审查](../.codex-work/logs/20261111-task03-review.md)：Standards 原 0 硬违规、1 项 P3 复用建议，已在 3c31667 修正并复核；Spec 原始与复核均 0；无剩余发现。
- 演示库原地 V2→V5 成功；显式 seed 后有 2 楼栋/3 寝室/6 床位/1 学生资料，4 个原演示账号保留。重复 seed 的 API 内容完全相同，最终后端重启后也保留；5174 代理登录与查询通过。证据 `.runtime/task03-demo-check.json`、`.runtime/task03-demo-final.json`。
- 任务 03 六条准则已闭环；联合账号/学籍创建未提供，其条件项不适用。未启动任务 04。全系统与其他环境边界保持前述。


## 任务 04 当前验证（2026-11-12）

沿公开 HTTP / 真实 apartment_test / 实际 Chrome 验证。新增 ResidencyHttpTest 8 项与 residency.spec.ts 每尺寸 4 项；旧场景单独计数，不能把重跑次数算作新增场景。

- 红绿循环：本人住宿接口缺失 403 → 入住及本人查询通过；调寝接口缺失 403 → 生命周期与失败回滚通过；当前入住账号原可停用 200 → 五类关联停用均拒绝；浏览器“我的住宿”入口缺失 → 实际页面全流程通过。
- 定向后端 **8 项通过**（`.runtime/task04-residency-tests.log`）。覆盖两学生争一床、单学生争两床、入住与五类停用竞争、角色/CSRF/参数/重放、整条启用链、历史分页与位置快照、数据库双唯一约束及审计操作者/前后数据。
- 真实故障注入：在 apartment_test 的 housing_residency 临时 CHECK 拒绝目标床位插入，另在 operation_log 拒绝入住/调寝/退宿审计；均通过 HTTP 观察原住宿与历史不变，SQL 只核验明确的约束/审计存储契约，finally 清除临时 CHECK。
- 定向浏览器 **8 项通过**（`.runtime/task04-residency-browser.log`）：1366×768 与 1920×1080 各验证完整入住→学生独立会话→调寝→退宿→历史；网络失败保留表单、旧关系冲突、当前入住停用拒绝、历史分页/空/失败重试、角色入口与直访限制、学生加载失败不显示过时住宿。
- 已查看 task04-history-1366、task04-transfer-error-1366、task04-my-residency-1920 截图；列表、长对话框和实际按钮可操作，页面无横向溢出。另保留两个尺寸的 task04-not-resident，全部为实际应用截图。
- V6 已在真实测试库从 V5 原地迁移；V1–V5 未改。演示最小样本沿用已有学生与床位，入住操作由页面真实办理，重复 seed 不重置住宿。

尚待本轮完整回归、独立双轴审查与演示库升级完成。05 的历史报修与位置、08/10 的应检快照、11 的成绩权限切换明确后续承接；任务 04 不宣称它们或任务 17 性能验收已通过。
