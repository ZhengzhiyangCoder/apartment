# 测试与验收记录 — 任务 01–02

日期：2026-11-11（Asia/Shanghai）。任务 01–02 验证完成；后续业务及整套系统验收尚未完成。

## 边界与隔离

采用已批准 Spec §5.1：公开 HTTP API → 实际 Spring Boot → 真实 MySQL，不使用 H2 或内存账号代替；从 API 返回结果验证行为。任务 01 的测试 SQL 仅准备样本。任务 02 的账号行为仍通过 API 观察；针对任务票明确要求的审计持久化、密码不明文存储和失败回滚，另核验真实数据库存储契约（不调用私有服务或 mock 调用次数），并在测试库添加/移除临时 CHECK 约束注入审计故障。

API 测试位于后端 IdentityHttpTest，运行前核对数据库产品为 MySQL、库名为 apartment_test，再清理该库模拟账号。浏览器测试位于 frontend/e2e，使用真实 Chrome、前端 5175 和后端 8082，自动启停且不复用未知服务；同样只使用 apartment_test。

## 已执行的红绿循环

| 路径 | 实现前失败 | 实现后 |
|---|---|---|
| 四角色登录、本人资料 | CSRF 入口预期 200，实际 401 | 通过 |
| 本人角色入口、越权拒绝 | 本人入口预期 200，实际 404 | 通过 |
| 改密持久化与旧会话撤销 | 改密校验预期 400，实际 404 | 通过 |

当前 6 项 API 测试通过，涵盖四角色、禁用/错误账号、会话与 CSRF 轮换、HttpOnly Cookie、退出旧 Cookie 重放、角色参数篡改、改密非法输入和旧会话撤销。初始 Flyway 迁移已在真实 MySQL 8.4 的干净 apartment_test 执行。

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

任务 02 的 6 条准则已完成，覆盖 US-03 及 AC-01/22/23 的本任务部分。当前没有住宿/工单/卫生表，后续引入业务时的停用限制仍由 04/05/06/08/13 实现；全系统审计查询由 15 交付，不把本轮记录能力当作已完成查询功能。性能与其他环境边界仍如前述。
