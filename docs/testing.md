# 测试与验收记录 — 任务 01–16

最新日期：2026-11-16（Asia/Shanghai）。任务01–16验证完成；本页按阶段保留历史证据，最新结果见任务16。规模性能及整套系统交付验收尚未完成。

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


## 任务 04 最终验证（2026-11-12）

沿公开 HTTP / 真实 apartment_test / 实际 Chrome 验证。新增 ResidencyHttpTest 8 项与 residency.spec.ts 每尺寸 4 项；旧场景单独计数，不能把重跑次数算作新增场景。

- 红绿循环：本人住宿接口缺失 403 → 入住及本人查询通过；调寝接口缺失 403 → 生命周期与失败回滚通过；当前入住账号原可停用 200 → 五类关联停用均拒绝；浏览器“我的住宿”入口缺失 → 实际页面全流程通过。
- 定向后端 **8 项通过**（`.runtime/task04-residency-tests.log`）。覆盖两学生争一床、单学生争两床、入住与五类停用竞争、角色/CSRF/参数/重放、整条启用链、历史分页与位置快照、数据库双唯一约束及审计操作者/前后数据。
- 真实故障注入：在 apartment_test 的 housing_residency 临时 CHECK 拒绝目标床位插入，另在 operation_log 拒绝入住/调寝/退宿审计；均通过 HTTP 观察原住宿与历史不变，SQL 只核验明确的约束/审计存储契约，finally 清除临时 CHECK。
- 定向浏览器 **8 项通过**（`.runtime/task04-residency-browser.log`）：1366×768 与 1920×1080 各验证完整入住→学生独立会话→调寝→退宿→历史；网络失败保留表单、旧关系冲突、当前入住停用拒绝、历史分页/空/失败重试、角色入口与直访限制、学生加载失败不显示过时住宿。
- 已查看 task04-history-1366、task04-transfer-error-1366、task04-my-residency-1920 截图；列表、长对话框和实际按钮可操作，页面无横向溢出。另保留两个尺寸的 task04-not-resident，全部为实际应用截图。
- V6 已在真实测试库从 V5 原地迁移；V1–V5 未改。演示最小样本沿用已有学生与床位，入住操作由页面真实办理，重复 seed 不重置住宿。

完整回归、独立双轴审查与演示升级结果如下。05 的历史报修与位置、08/10 的应检快照、11 的成绩权限切换明确后续承接；任务 04 不宣称它们或任务 17 性能验收已通过。


- 全量后端 `./scripts/backend.sh -B verify`：**28 项通过**（前序20+入住8），JAR 构建通过；`.runtime/task04-backend-final.log`。修正仅涉及前端，最终空测试库验证再次28项通过，见下段。
- 前端 `npm --prefix frontend run build`：vue-tsc 与 Vite 构建通过；原始 `.runtime/task04-frontend-build.log`，审查修正后 `.runtime/task04-review-build.log`。
- 全量浏览器 `npm --prefix frontend run test:e2e`：原始 **38 项通过**（前序30+入住8），`.runtime/task04-browser-final.log`；03e98be 修正后再次 **38 项通过**，最终 `.runtime/task04-browser-review-fix.log`。不重复累计场景。
- 原始 Standards 0硬违规/1 P3建议，Spec 0；统一三个管理页授权失效处理后，两名 reviewer 分别复核，无剩余发现，见[审查记录](../.codex-work/logs/20261112-task04-review.md)。
- 浏览器服务退出后，明确核验固定库名 apartment_test 与8张预期表，再清空该测试 schema；实际从空库执行 **V1–V6全部6项迁移**并启动应用，28项API/MySQL及打包再次通过，`.runtime/task04-clean-backend-final.log`。该一次性验证脚本仅放 .runtime，拒绝任意库名及额外表；演示库未清理。所有故障CHECK已移除。
- 演示 V5→V6原地升级成功，5174代理实际办理模拟A101→B201，当前1条、历史1条。重复seed和实际重启后数据完全一致：`.runtime/task04-demo-initial.json` / `.runtime/task04-demo-final.json`。演示服务保留运行，测试服务已退出。

任务04覆盖US07–11和共享AC的本票分工，7条准则闭环；05/08/10/11仍待各自开发验证。性能、其他OS/浏览器、公网部署均未新验证，不扩大前序环境结论。

## 任务05最终验证（2026-11-13）

- 新增RepairHttpTest **8项**，公开HTTP/实际MySQL及受控文件系统验证。全量原28+新增8=**36项通过**、JAR构建成功：`.runtime/task05-backend-final.log`；审查修正后再次全量36项通过，最终`.runtime/task05-backend-review-fix.log`。
- 实际空apartment_test完整应用**V1–V7**，原V1–V6不改。固定库名与11张白名单表核验后才清空，演示库未清理。演示V6→V7升级成功。
- 新增浏览器每尺寸3项共**6项**；全量前序38+新增6=**44项通过**（3.9分钟），`.runtime/task05-browser-final.log`。后端边界修正后报修6项再次通过，`.runtime/task05-browser-review-fix.log`。不重复累计场景。前端类型检查与构建通过，`.runtime/task05-frontend-build.log`。
- API覆盖身份/位置防篡改、调寝退宿旧单、本人/管理范围、维修角色拒绝、图片逐次授权和会话撤销、有效JPG/PNG/WebP、格式/大小/数量/截断拒绝、取消原因/重放、分页/查询、并发同请求建单去重、竞争取消、提交与调寝/退宿时点。
- 文件故障将固定test-api目录临时移走并用普通文件占位；数据库故障以临时CHECK分别拒绝图片关联、状态日志及审计。在真实HTTP失败后确认无可见半单、关联/状态/审计无部分写入，新增文件清理；取消失败原单完整。finally恢复目录和约束。
- 前端覆盖实际图文上传、失败保留文件/文字、网络恢复、未入住、调寝历史、管理取消/旧窗口冲突、图片读取失败重试、分页/空/查询失败及角色直访。两尺寸截图共10张：task05-upload-error/detail/cancelled/cancel-conflict/management。已查看1366详情/上传失败及1920管理列表；长对话框内滚动可操作，无页面横向溢出。初次截图过渡态已用动画禁用与关闭断言修正，非功能缺陷。
- [独立审查](../.codex-work/logs/20261113-task05-review.md)：Standards原1项P2模块边界问题，经c94c34e修正复核关闭；Spec原始及复核0。无剩余发现。
- 两条模拟工单（一待分配一已取消）及两张关联图片经重复seed、真实后端重启保持完整详情和图片SHA-256一致；原住宿/基础资料保持不变。证据`.runtime/task05-demo-initial.json`与`task05-demo-final.json`。

红绿证据：提交入口403→201、未入住409；未完工单停用200→409；前端缺少“我的报修”→真实流程通过。测试准备中的模糊账号匹配、首次CSRF并发及静态路径Cookie范围预期已纠正，未改产品行为迁就错误样本。05八条准则闭环；06派单/维修及待处理取消、17性能与其他OS/浏览器均未提前声称完成。


## 任务06最终验证（2026-11-16）

- 后端`./scripts/backend.sh -B verify`：**43项通过**（原36+本轮7），JAR打包通过，`.runtime/task06-backend-final.log`。修复前红测：人员入口403、跳过处理中入口403、未完维修账号可停用200；实现后派单/完整状态/停用保护均通过。
- 明确核验固定apartment_test与11张预期表，在测试服务退出后清空该测试schema，实际完整执行**V1–V8八项迁移**再跑43项回归。V1–V7原字节不变，演示库未清理。
- 前端vue-tsc/Vite构建通过，`.runtime/task06-frontend-build.log`。全量Chrome两尺寸**50项通过**（原44+本轮6），`.runtime/task06-browser-final.log`；报修定向12项通过，`.runtime/task06-repair-browser.log`，不重复累计。API与浏览器顺序使用apartment_test。
- 新增API场景覆盖有效人员查询/分页参数/角色/CSRF、派单时间与身份、重分配姓名快照与旧负责人撤权、严格开始/完成/终态、三角色待处理取消、未完账号停用及改角色拒绝、任务结束后停用与会话失效。
- 并发开始与取消/重分配/重复开始只产生一条成功变化，状态/版本/负责人/日志一致；派单与账号停用或换角色只能一方成功，避免不可用负责人。故障注入只对apartment_test的状态日志与审计分别增加临时CHECK，四类工作流失败后通过公开详情核验责任、状态、时间、结果和版本完全不变，finally移除约束，重试恢复成功。
- 新增浏览器每尺寸3项：完整学生提交→宿管派单→维修开始/完成→学生结果；重分配网络与版本冲突保留原因、旧维修人员图片404且旧按钮失败后清空详情；选项加载失败/空状态/停用候选拒绝及待处理取消。前序维修角色直访拒绝调整为“仅本人任务”，原未分配单仍不可见。
- 页面缺少派单按钮红测→完整链路通过；派单选择、重分配原因、处理说明失败后均保留。12张新截图位于output/playwright，已查看1366学生结果及1920重分配冲突；长对话框内滚动可操作，目标尺寸无影响性遮挡。
- [独立双轴审查](../.codex-work/logs/20261116-task06-review.md)：Standards 0、Spec 0，固定3492624...e788e32，无需修正。
- 演示库V7→V8后先核验原两单/日志/图片摘要与住宿资料保持不变，再通过真实API把原待分配模拟单完成，并新增一张待处理单；共已完成/待处理/已取消3单。重复seed和实际后端停止/重启后所有详情、日志、原住宿及图片SHA-256一致；`.runtime/task06-demo-before.json`、`task06-demo-initial.json`、`task06-demo-final.json`。

06七条准则闭环，US15–18及AC04/05/06/22/23本任务部分已覆盖。未执行任务17规模性能验收、其他OS/浏览器或公网部署；07评分方案未启动。临时故障约束已清除，演示服务保留，测试端口已退出。

## 任务07验证（2026-11-16）

- 后端`./scripts/backend.sh -B verify`：**50项通过**（原43+评分方案7），JAR打包通过，`.runtime/task07-backend-final.log`。在固定apartment_test且13张白名单表核验后清空该测试schema，实际从空库完整执行**V1–V9九项迁移**；原V1–V8未改，演示库不清理。
- 前端vue-tsc/Vite构建通过，`.runtime/task07-frontend-build.log`。评分方案定向两尺寸**8项通过**，`.runtime/task07-browser.log`；后补了另一窗口已启用时保留旧草稿输入的恢复检查，全量浏览器**58项通过**（原50+本轮8，5.4分钟），`.runtime/task07-browser-final.log`。不重复累计场景；API与浏览器按顺序使用测试库，未并行重置。
- 新API场景覆盖默认阈值60、40/30/30、33.33/33.33/33.34、99.99不能启用、负数/越界/超精度/重复名称/维度数/参数校验；角色、CSRF和会话撤销；列表过滤分页与只读不写审计。启用后维度和阈值固定，复制后调整不影响原始历史，宿管不可读草稿。
- 两个不同草稿同时启用只一方成功，另一个明确409；编辑与启用竞争也只一方成功。真实MySQL直接违反生成列唯一约束被拒。临时CHECK分别拒绝维度保存、创建/修改/复制审计、新版本启用、旧版本归档审计及新版本启用审计，公开HTTP失败后核验定义、版本、启用状态和日志全部恢复，finally移除约束，重试成功。未伪造批次引用或used标志。
- 新浏览器场景覆盖创建启用复制/历史、只读角色与禁止直访、精度/合计/重名反馈、断网保存与加载恢复、编辑冲突/启用冲突后显式刷新、分页末页回退、空状态。两尺寸12张task07截图：history/manager/form-error/draft-conflict/activate-conflict/list。已查看1366历史详情、1920编辑冲突和宿管列表，长表单可滚动、按钮可操作，无页面横向溢出。
- 红绿证据：创建草稿与启用入口403→201/业务校验；精度校验最初通用INVALID_INPUT→明确INVALID_WEIGHT；浏览器缺少评分方案入口→真实链路通过。测试故障构造曾使用MySQL不允许的自增id CHECK，已改为名称/状态谓词，未为迁就样本改变产品规则。日志为`.runtime/task07-red-*`、`task07-green-*`和`task07-scheme-tests.log`。
- [独立审查](../.codex-work/logs/20261116-task07-review.md)：固定9fe0a8a...6a14939，Standards 0、Spec 0，无需修正。V1–V8、已批准Spec/工程结构与原型原字节不变。
- 演示库V8→V9原地升级；真实API建立3个模拟版本（历史40/30/30阈值60、当前50/25/25阈值65、99.99%待调整草稿）。原住宿、3单详情/日志及图片SHA-256完全保留。重复seed与实际停止/重启后全部快照一致：`.runtime/task07-demo-initial.json`与`task07-demo-final.json`。

任务07六条准则在本票分工内闭环，提供已发布版本冻结；真实批次绑定后的历史冻结验收由08回归，完整AC-10未提前宣称通过。未执行任务17性能、其他OS/浏览器或公网部署。最终核验两库迁移均9项成功，测试临时故障约束0；演示5174/8081保留，测试5175/8082已退出。


## 任务08最终验证（2026-11-16）

- 后端`./scripts/backend.sh -B verify`：**61项通过**（原50+新增BatchHttpTest 11项），JAR构建通过，`.runtime/task08-backend-final.log`。测试服务退出后按固定apartment_test及17表白名单核验并清空，仅该测试schema实际从空库完整执行**V1–V11全部11项迁移**；演示库不清理，V1–V9原字节不改。
- 前端vue-tsc/Vite通过，`.runtime/task08-frontend-build.log`。任务08每尺寸4场景共8项；最终全量Chrome **66项通过**（原58+新增8），`.runtime/task08-browser-final.log`。API与浏览器顺序使用隔离库，不重复累计重跑次数。
- API覆盖只读不创建、无启用方案拒绝、管理员/宿管并发准备同一批次、准备与启用竞争绑定完整版本、名单/位置/方案/阈值快照、名单外拒绝、已发布版本冻结与真实外键保留。
- 正式录分覆盖40/30/30的80/90/70=80.00、最终0.005 HALF_UP为0.01、真实零分与未检查区别、完整且唯一维度/整数范围、伪造总分与操作者无效、角色/CSRF/会话撤销、唯一评分并发竞争、数据库唯一约束、查询分页与参数限制。2/3显示66.67%，无应检无百分比，100%保持OPEN。
- 受控Clock通过公开HTTP验证上海2021-01-01仍属ISO2020-W53、归属周一2020-12-28，以及2021-01-04上海零点切为2021-W01；同寝室下一周可再次评分，旧周普通录入拒绝，实际提交时间另存。关闭/停检使用隔离库状态夹具验证拒绝；真实状态管理与授权补录仍由10开发验收。
- 真实MySQL临时CHECK分别拒绝第二条应检快照、批次审计、第二条评分明细及评分审计。HTTP失败后批次/名单或评分/明细/进度/审计无部分状态，finally移除约束并重试成功。退宿后开放批次的未检寝室/楼栋仍禁止停用，评分后可停用且保留历史。
- 浏览器覆盖显式准备、宿管录分与管理员只读、3间完成2间、方案切换后旧标准、分值校验、断网、服务器已保存但响应丢失后重试409并核对正式结果、输入保留、详情失败重试、无标准恢复、空应检、加载失败清除过时进度、全校楼栋/房号/状态查询、11条末页录分后回退、学生和维修人员直访拒绝。
- 红绿证据：当前周接口403→200；正式评分403→201；退宿后应检楼栋原可停用200→409。对应`.runtime/task08-red-prepare/score/references.log`与green日志；前端缺少本周检查入口红测后两尺寸主流程通过。专项首轮6项通过、2项分页因退出后立即导航中止退出请求超时；添加退出完成断言后二尺寸分页通过，最终全量66项通过，未改产品行为迁就测试。
- 12张截图在output/playwright：task08-score/progress/network/conflict/empty/list，各1366与1920。已查看1366进度/列表及1920冲突核对，长弹窗可滚动、按钮可操作、页面无横向溢出；全部为实际应用截图。
- [独立双轴审查](../.codex-work/logs/20261116-task08-review.md)：固定b51bdd2...b7fcc8b，Standards 0硬违例/0需处理smell，Spec 0，无需源码修正。
- 演示V9→V11原地升级前核对07快照；真实API补入2个明确模拟学生，3间应检、A101=80.00/B201=90.00/A102未检。批次绑定50/25/25阈值65，后续启用40/30/30阈值60仍不改旧批次。重复seed与实际后端停止/重启后`.runtime/task08-demo-initial.json`和`task08-demo-final.json`完整相同，原student住宿历史、3张工单/日志及图片SHA-256保持，模拟新增入住单独计入。

08八条准则在本票分工内闭环，真实方案绑定后的冻结回归已补齐；09连续评分/草稿、10名单状态治理/补录、11学生成绩、13整改以及17规模性能仍未提前宣称完成。两库11项迁移成功、临时故障约束0，演示5174/8081/3308保留运行，测试端口退出；其他OS/浏览器及公网部署未新验证。


## 任务09验证（2026-11-16）

- 后端`./scripts/backend.sh -B verify`：**67项通过**（原61+新增6），JAR构建成功，`.runtime/task09-backend-final.log`。固定apartment_test与18表白名单核验后仅清空该测试schema，实际完整执行**V1–V12全部12项迁移**；V1–V11原字节不变，演示库未清理。
- 前端vue-tsc/Vite构建通过，`.runtime/task09-frontend-build.log`。新增浏览器五场景每尺寸各一遍（共10项）：草稿重登/A101→A102→B201、提交失败与防重复、两宿管草稿冲突、过滤队列及下一间查询失败、草稿暂存失败及多窗口版本恢复。专项前四场景两尺寸8项通过，`.runtime/task09-browser-retry.log`；全量最终结果另列下方，不重复累计重跑次数。
- API新增覆盖：未完成/待修正输入暂存、重登恢复、两账号独占和伪造所有者无效；正式提交重新校验、清本人草稿且保留他人草稿；版本旧写入/旧发布409；公开角色/CSRF/会话撤销、参数与维度校验、分页、旧周及关闭/停检禁止提交并可读原草稿。关闭/停检使用隔离SQL夹具，10仍须真实管理接口与浏览器回归。
- 真实MySQL故障：草稿审计更新拒绝、评分第二条明细拒绝、评分审计拒绝、临时外键引用阻止草稿删除。HTTP失败后检查草稿/正式详情/进度与明细/审计整体保留或回滚，finally移除CHECK和辅助表，再次提交成功。版本竞争与发布/暂存竞争只能保存新草稿或提交正式之一，不覆盖新输入、不复活已清草稿。
- 浏览器实际验证草稿部分输入重登恢复、正式成功才跨楼栋推进、只正式计进度、当前楼栋/关键词范围内排序、参数/断网/500不跳间、请求中按钮锁定且单请求、另一人先提交后保留本人草稿、已保存但下一间加载失败独立重试、同账号版本冲突保持本地值并显式载入新草稿。500页面行为由HTTP边界故障响应验证，真实事务失败另由API/MySQL故障注入验证；未将冲突提示当作回滚证据。
- 红绿证据：草稿入口403→200；正式成功未清草稿→原子清理；过期版本发布201→409。`.runtime/task09-red-draft.log`、`.runtime/task09-red-publish.log`、`.runtime/task09-red-version.log`及green/batch日志。首轮测试因沙箱TCP受限，通过既有权限机制执行；浏览器初次标题定位同时匹配弹窗标题和寝室标题，限定寝室标题后8项通过，未改业务迁就样本。
- 两尺寸8张截图：output/playwright/task09-draft、complete、submit-failure、conflict。已查看1366草稿/完成页和1920冲突；长弹窗可滚动操作，完整页无横向溢出。
- [独立双轴审查](../.codex-work/logs/20261116-task09-review.md)：固定f339b51...d51d76f，Standards硬违规0/smell0，Spec确定发现0。非阻断建议为明示连续队列范围，最终页面提示补充此说明。
- 演示先核对08完整快照，再V11→V12增量升级；只为宿管A102新增87/空/90的个人模拟草稿，原3间应检/2份评分不变。重复seed及实际后端停止/重启后草稿、原方案/评分/住宿/三张工单与图片SHA-256完全一致；`.runtime/task09-demo-before.json`、`task09-demo-initial.json`、`task09-demo-final.json`。测试与演示库始终隔离。

09不宣称10真实状态治理/历史补录、11排行、13整改或17规模性能验收已完成；未扩大其他OS/浏览器或公网部署结论。


最终全量Chrome **76项通过**（原66+新增10，7.1分钟），`.runtime/task09-browser-final.log`；其后仅补连续队列范围提示，vue-tsc/Vite再次通过（`.runtime/task09-review-build.log`），无逻辑变更。两库均12项成功迁移、临时故障约束0；测试服务退出、演示5174/8081/3308保留。任务09七条准则在本票分工内闭环，10待开始。


## 任务10批次治理与历史补录最终验证

- 后端`./scripts/backend.sh -B verify`：**75项通过**（原67+BatchGovernanceHttpTest新增8），JAR成功，证据.runtime/task10-backend-final.log。先固定apartment_test库名及18表白名单，再仅清理测试schema，实际空库完整迁移V1–V13。V1–V12未修改；V13也从已有V12原地升级验证。
- 前端vue-tsc/Vite通过，证据.runtime/task10-frontend-final.log，审查修正后task10-review-build.log再次通过。
- 全量Chrome`npm --prefix frontend run test:e2e`：**82项通过**，两尺寸各41项，8.1分钟，证据.runtime/task10-browser-final.log。审查新增2条复现场景（每尺寸各2项），修正后`batches.spec.ts`全部**28项通过**，证据.runtime/task10-review-browser.log；覆盖原有录分/草稿与本票治理。不是声称86项在同一全量命令运行，也不将重复场景累计。
- 新API场景覆盖状态恢复、草稿/成绩保留、名单增减与条件校验、审计前后原因/身份/时间、历史授权反复跨周保持/原归属标准、空名单、角色/CSRF/实时撤权、输入/版本冲突、审计故障整体回滚、名单/状态与正式提交并发、启停引用保护。
- 故障注入只在测试库operation_log临时CHECK拒绝四类治理动作，确认名单、状态、版本、草稿与新周准备整体回滚，finally移除；无测试辅助表。旧会话测试缓存撤权前CSRF直接重放，避免测试帮助函数先取CSRF的401被误认成产品失败。
- 浏览器通过真实页面覆盖关闭/停检/恢复/重开、移出后的草稿只读、补入/移出后的无应检、断网保留原因、版本冲突核对、历史切换及授权补录。跨周样本只在固定apartment_test调整日期，全部治理与发布走真实API或页面；后端受控Clock另外验证真实周界线，不提供改时业务接口。
- 红绿：状态与名单缺API403→通过；跨周未关闭OPEN→关闭且授权例外保留；管理页面缺按钮→通过。独立Spec发现旧候选误提交、旧批次日志迟到覆盖，两条新增测试先在4157698失败（task10-review-red.log），再于52a8a9f修正并通过28项相关回归；Standards0违例，Spec原2项P2全部复核关闭，详见[审查](../.codex-work/logs/20261116-task10-review.md)。
- [界面截图](../output/playwright/)task10-removed-draft/changes/roster/history，共8张。实际查看1366移出草稿、1920历史补录；沿第一版灰蓝新拟态，长弹窗可滚动，操作按钮实际可达。
- 演示先对照09最终快照，原地V12→V13后通过API新增6条模拟治理记录；最终OPEN、3应检/2已检/1本人草稿。重复普通seed和实际Java进程停止/重启后，task10-demo-initial.json与task10-demo-final.json完整相同，原方案/评分/住宿/工单/图片摘要不变。证据.runtime/task10-demo-server.log、task10-demo-restart.log、task10-demo-before.json及上述快照。

API与浏览器顺序使用apartment_test，演示apartment_demo未清空；测试服务退出，演示5174/8081/3308保留。本票完成US25/26及共享AC的分工；11/12统计排除停检、14关闭后更正、15完整审计及17性能仍待后续。未验证其他操作系统、浏览器或公网部署。


## 任务11学生成绩、趋势与排行

- `./scripts/backend.sh -B verify`：**81项通过**（原75+StudentResultsHttpTest新增6），JAR构建成功；`.runtime/task11-backend-final.log`。固定边界为公开HTTP→真实应用→隔离MySQL，不用内部服务mock；时间用已有MutableClock。
- `npm --prefix frontend run build`：vue-tsc/Vite通过，`.runtime/task11-frontend-final.log`。
- 修正后全量 `npm --prefix frontend run test:e2e`：**96项通过**（原86+新增5场景×两尺寸），`.runtime/task11-browser-final.log`。1366×768、1920×1080真实Chrome；API先结束再运行浏览器，均只重置apartment_test。
- 固定API样本：80/90月均85.00；90/90/80名次1/1/3；0分有效、缺检/草稿/停检不补零；恢复重纳入，关闭不排除；80.005→80.01、三次80.01/80/80→80.00后与一条80并列；跨2026-W53/2027-W01、周一12月28日归12月、2月补录仍归原周。原标准/权重/阈值、原始提交时间、无数据null均检查固定期望。
- 权限：未入住409，其他角色403、匿名401，寝室/楼栋篡改403；调寝立即可看新寝室历史且旧范围403，退宿收回查询但本人原寝室工单可见；密码重置撤销旧会话。榜单无学生姓名或评分人字段。
- 新增浏览器5场景：成绩/趋势/标准/榜单；实际页面调寝退宿及旧工单；停检恢复、断网清除旧结果及迟到响应隔离；未入住/无数据/角色入口；11寝室榜单末页全部停检后回第一页。截图task11-results/detail/transferred/empty两尺寸，实际查看结果和标准窗口，长页可滚动，无影响操作的遮挡或横向溢出。
- 红绿：未实现查询403、未实现摘要0、缺少页面入口；分别实现后通过。审查发现空榜末页P3，旧代码Chrome复现，再修复并两尺寸专项通过（task11-review-red/green.log），两轴复核关闭。最初调寝测试误选夹具已占用床位，改为实际空床后通过；这是测试准备问题，不计产品缺陷。
- 无结构变动：Flyway验证全部V1–V13；本票未重复清库迁移。演示更新前对照10完整快照，实际后端停止/重启后所有旧业务/图片摘要仍一致，新查询student B201周/月90.00、1次、第1名；`.runtime/task11-demo-before.json`、`task11-demo-after.json`、`task11-demo-server.log`。本票只读，不为演示伪造历史或重建数据库。

任务11七条准则闭环，仅覆盖所分配的US/AC部分；任务12管理统计、14更正、17性能及全系统18验收未完成。依赖、Spec、工程结构、原型及既有迁移不变；其他平台/浏览器仍未验证。


## 任务12最终验证

- `./scripts/backend.sh -B verify`：**87项**真实HTTP/MySQL全部通过（原81+管理6），JAR构建通过；.runtime/task12-backend-final.log。V1–V13校验通过，未新增或修改迁移，没有无必要重复清空库。
- `npm --prefix frontend run build`：vue-tsc/Vite通过，.runtime/task12-frontend-final.log。
- `npm --prefix frontend run test:e2e`：**104项**真实Chrome全量双尺寸通过（原96+管理4场景×2），.runtime/task12-browser-final.log。先后端再浏览器，均隔离apartment_test；专项6项与审查专项2项只作过程证据，不重复累加。
- 6个API场景：100/100与0→楼栋50.00、缺检及草稿不计；同版本维度分楼栋聚合、跨版本同名不同权重；寝室详情/趋势/楼内名次与学生一致、分页；真实退宿不改历史应检、名单移除改变进度、关闭有效/停检排除/恢复；ISO跨年、周一跨月、延迟补录实际时间；非法或冲突范围、无数据、已停用历史、超大页及撤销会话；80.005寝室先舍入80.01再楼栋等权80.01，以及改名不拆样本和并列名次。
- 管理浏览器4场景×2：系统管理员全校/楼栋/寝室下钻、图表数据与版本明细；宿管分页/全停检回第一页/恢复/断网清空旧结果/重试/迟到响应防覆盖；明确空状态、学生维修无入口/路由和API拒绝；完整侧栏末项真实点击。
- 红绿：管理API最初403后通过、页面入口不存在后可用、维度分组改名旧标签红测后同周期统一；审查发现的1366侧栏末项outside of viewport由overflow-y修复，两尺寸通过。测试夹具的数字尾零、楼名空格和退宿note已纠正，不放宽服务端校验。
- [独立审查](../.codex-work/logs/20261116-task12-review.md)：基点358d0d5，实现ec51cad，修正04ae2d7。Standards0违例/0需处理smell；Spec1项P2已修复复核，最终无未解决发现。
- [截图](../output/playwright/)task12-building/room/empty各两尺寸共6张，已查看实际页面；列宽、图表、筛选、明细与侧栏可读可操作，无页面横向溢出。全量包含共用ResultTrend/ResultWeeks后的学生回归。
- 演示后端重启加载只读管理API，全部旧评分/草稿/标准/住房/工单/图片摘要和学生成绩与任务11完全一致。全校本周/11月2间参评、2次、2/3=66.67%、A栋80/B栋90；寝室详情与学生一致，学生及维修人员管理API403。证据.runtime/task12-demo-before.json、task12-demo-after.json、task12-demo-server.log。

已覆盖本票US36–38及AC13/15–18/23分工，不宣称13整改、14更正写入或17性能验收完成。没有新增表、依赖或正式业务样本；不修改演示库日期，不重复seed。测试服务退出，正常5174/8081/3308继续运行。


## 任务13最终验证

- `./scripts/backend.sh -B verify`：**94项**真实HTTP/MySQL全部通过（原87+整改7），JAR构建通过；.runtime/task13-backend-final.log。固定apartment_test空库实际完整应用V1–V14；原V1–V13不改。
- `npm --prefix frontend run build`：vue-tsc/Vite通过，.runtime/task13-frontend-final.log，无新增依赖。
- `npm --prefix frontend run test:e2e`：**114项**全量实际Chrome通过（原104+整改5场景×2），1366×768与1920×1080，.runtime/task13-browser-final.log。API与浏览器顺序运行，仅隔离apartment_test；8项初始专项、2项审查专项和被主动中断的112项初次全量均不重复计数或冒充完成。
- 7个API场景覆盖：99/1权重下60/59=59.99触发、60/60不触发，方案变化不改原阈值；跟进核实关闭、旧版本拒绝、新低分要求重新核实、关闭后新事件；退宿后未完整改阻止楼栋/寝室停用、闭环后允许；并发历史低分一事件、多窗口跟进一胜者及生成列唯一防线；触发关联/日志/审计故障整体回滚、草稿保留；角色/CSRF/会话撤销/参数分页；历史补录、批次停检恢复保留追溯和人工关闭语义。
- 5个浏览器场景覆盖真实录分59.99→跟进→核实→说明关闭；网络失败/旧版本保留说明、显式重载、列表失败清旧数据、迟到响应不能覆盖；多次低分复用/60不自动关闭/关闭后新低分另建；空态、学生和维修人员无入口及越权拒绝；其他管理人员关闭导致409后重载，仍可读取选中复制未提交说明，闭环写入口保持关闭。两尺寸侧栏末项均可操作。
- 红绿证据task13-red-trigger/workflow/guard/browser与task13-review-red/green。独立Standards0硬违例/0待处理smell；Spec补充P2已真实红测修复并独立复核，剩余0，见[审查](../.codex-work/logs/20261116-task13-review.md)。
- 演示V13→V14升级前后原数据完全一致；新增明确模拟A103/50分/阈值65的OPEN整改和跟进后，原成绩/草稿/标准/住宿历史/维修与图片摘要均保留。正常seed与真实后端重启完整新快照一致，5174实际Chrome只读验证通过。当前应检4/已检3/75%、A楼65/B楼90，student仍B201的90/1次/楼内第1。
- [截图](../output/playwright/)task13-closed/follow-up/list各两尺寸，另task13-demo-list/detail；共8张，已检查。测试5175/8082退出，演示5174/8081/3308保留。

任务13的US39/40及AC20/22/23分工已闭环；14成绩更正、17规模性能和18整体交付尚未完成。没有修改批准Spec、工程结构或原型，没有扩大其他OS/浏览器或公网部署结论。


## 任务14最终验证

- `./scripts/backend.sh -B verify`：**101项**公开HTTP/真实MySQL全量通过（原94+更正7），JAR构建通过；.runtime/task14-backend-final.log。固定apartment_test空库完整应用V1–V16，原V1–V14不改。
- `npm --prefix frontend run build`：vue-tsc/Vite通过，.runtime/task14-frontend-final.log；无新依赖。
- `npm --prefix frontend run test:e2e`：**124项**全量真实Chrome通过（原114+5场景×2），1366×768、1920×1080，.runtime/task14-browser-final.log。API与浏览器顺序执行，专项10项不重复计数。
- 7项API覆盖：关闭批次同id、原方案89.80计算和完整前后值；高→低创建、低→低关联去重、达标保留跟进和人工核实、新低分可另起事件；角色/CSRF/撤销/输入与分页；不同管理员同版本并发一200/一409；切换新方案仍按原99/1权重与60阈值；维度/更正/触发/日志/审计故障一致回滚；周月统计/排行/趋势/维度实时一致，停检更正仍排除后恢复。
- 固定统计：寝室80/100原月90与同楼90并列第1；原80改60后月80、第2，楼栋85（寝室等权），维度样本60/90/100均83.33；停检原周后仅100有效，更正原周40仍排除；恢复后本寝室月70、楼栋80。更正时间不改变原归属或原提交时间。
- 5项浏览器覆盖：关闭批次更正/前后历史/整改保留并人工关闭；网络/并发冲突保留分值及原因、显式核对新版本；历史错误重试/11条分页/离页迟到响应；实际学生与管理页面当前值、停检及越权；保存70后迟到80列表查询不能覆盖。
- 红绿证据task14-red-correction/green-correction、red-rectification/green-rectification、red-browser/green-browser，以及独立审查task14-review-red/green。测试初稿的过期csrf助手、范围/多status/标题定位及延迟路由等待已修正，不放宽业务规则。[双轴审查](../.codex-work/logs/20261116-task14-review.md)Standards0，Spec唯一P2已修复复核，剩余0。
- 演示升级前后业务/图片摘要一致；A103原50分在同id留痕更正55、85，原触发50/阈值65及跟进保留且仍OPEN。A10180/B20190、A102草稿、原住宿/维修/图片不变；检查仍3/4=75%，A楼82.50/B楼90，student仍90/1次/第1。普通seed和真实重启后完整快照一致，实际5174Chrome验证通过。自动审批曾担心seed覆盖数据，经具体脚本证据核查后重新获准，最终无阻塞。
- [截图](../output/playwright/)task14-history/linked/conflict各两尺寸，加demo-history/rectification，共8张；实际控件可达，无页面横向溢出。最终demo/test各16个成功迁移，test_临时对象0，5175/8082退出，正常5174/8081/3308保留。

本票US33/41与AC13/19/21/22/23分工已闭环；15全局审计、17规模性能、18整体交付尚未完成。批准Spec/工程结构/原型/依赖未变，未扩大平台或部署结论。


## 任务15最终验证

- `./scripts/backend.sh -B verify`：**106项**公开HTTP/真实MySQL全量通过（原101+审计5），JAR通过；.runtime/task15-backend-final.log。V1–V16校验通过，无新迁移。
- `npm --prefix frontend run build`：vue-tsc/Vite通过，.runtime/task15-frontend-final.log；批准依赖不变。
- `npm --prefix frontend run test:e2e`：**142项**全量真实Chrome通过（原124+9场景×2），1366×768与1920×1080，.runtime/task15-browser-final.log。修复专项18项通过，task15-navigation-green.log；不重复计数。与后端顺序执行。
- API覆盖实际账号/住宿/派单重分配/处理/方案/名单/正式评分/更正/整改操作后的查询，必要原因与前后值；管理员全局、宿管本人、学生/维修无统一审计；来源原授权；筛选/分页/排序白名单与时间包含/排除边界；私有草稿/图片/无关人员字段最小化；过期版本/审计故障无假成功，活动引用阻止停用，结束后停用仍保留原记录及更正历史，停用操作者旧会话失效。
- Chrome覆盖查询分页与空态、改名停用后的精确来源、调寝/重分配/名单/更正/整改/方案跳转、四角色范围、查询/详情失败重试、迟到详情不覆盖、权限撤销，以及自由文本原值、批次切换时旧定位取消、降权来源分类和分类导航忙状态四项审查回归。
- 红绿证据task15-red-api/trace/filters/sources/browser/links；[独立审查](../.codex-work/logs/20261116-task15-review.md)Standards 0硬违例/2个P2启发式、Spec 4个P2，均已真实红绿及独立复核关闭。旧楼名ACTIVE→启用不可被翻译隐藏；延迟A加载时切换B不得错误自动打开B成绩；降权旧学生资料来源必须拒绝、未知分类明确报错；清理来源路由期间锁定分类切换，取消导航不继续查询。初轮全量为修正而中断，不计作通过。
- 演示普通重启前后与task14-demo-after完整业务/图片hash一致，无seed、迁移或新增业务；现有50条审计可查，学生/维修403，实际5174Chrome从审计定位A103原50→55→85更正历史。原演示无重分配记录，该流程在固定隔离库公开API/实际Chrome已验证。
- [截图](../output/playwright/)task15-details/score-source/own各两尺寸，加demo-list/correction共8张；无页面横向溢出。最终demo/test各16项成功迁移、临时test_对象0，测试5175/8082退出，正常5174/8081/3308保留。

本票US42及AC19/21/22/23分工完成；16工作台、17规模性能、18整体交付尚未完成。未扩大平台或部署结论。

## 任务16最终验证

- `./scripts/backend.sh -B verify`：**110项**公开HTTP/真实MySQL全量通过（原106+工作台4），JAR构建成功；`.runtime/task16-backend-final.log`。
- `npm --prefix frontend run build`：vue-tsc/Vite通过；`.runtime/task16-frontend-final.log`。无新依赖、V1–V16不变。
- `npm --prefix frontend run test:e2e -- dashboard.spec.ts`：**14项**双尺寸实际Chrome专项通过（7场景×1366×768/1920×1080）；`.runtime/task16-review-green.log`。最终`npm --prefix frontend run test:e2e`：**156项全量通过**（原142+工作台14），`.runtime/task16-browser-final.log`，专项不重复累计。
- HTTP覆盖未登录/四角色投影；管理只读刷新不准备批次；无应检完成率null；7条工单total与前5项摘要；学生调寝/退宿后的成绩范围及本人旧工单；成绩更正；转派/开始/完成后的维修队列；伪造role/room/owner/assignee不扩大范围；角色变更与禁用撤销会话。
- Chrome覆盖四角色空态和实际入口、未检筛选→正式评分→检查进度/整改/统计、成绩更正与调寝/退宿、维修真实开始/完成、网络失败清空旧结果并重试、角色撤销、迟到业务响应与SPA退出重登后迟到身份响应。
- 接口各切片真实403红测→绿测；页面内容缺失红测→空态绿测。真实会话撤销曾出现空白旧URL，已修正；独立Spec另发现迟到身份覆盖P2，真实SPA红测（task16-review-auth-red-spa.log）→修正后14项绿测，最终Standards/Spec剩余均0。旧测试按钮定位失误、整页导航取消请求的测试初稿及被中断运行不计通过。
- 演示8081普通重启前后与任务15业务/统计/草稿/更正/整改/图片hash一致；四角色5174真实Chrome工作台与领域工单查询一致，未重跑seed或补造业务。`.runtime/task16-demo-{before,after}.json`、`task16-demo-ui.json`；截图`output/playwright/task16-*`。

本票沿已批准公开HTTP、真实隔离apartment_test与实际Chrome边界；后端与浏览器顺序执行。17规模性能、18整套交付尚未验收，其他操作系统/浏览器/公网部署未验证。

- 最终只读核验：demo/test各16条成功迁移，临时test_对象0，演示审计50条不变；测试5175/8082已退出，正常5174/8081/3308保留。`.runtime/task16-final-checks.json`。任务16六条准则完成，17/18待开始。
