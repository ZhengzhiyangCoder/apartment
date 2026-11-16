# 项目资料索引

核验日期：2026-11-11。以下是用户提供的业务讨论资料，原文件未修改；已于需求访谈启动时逐字节复制到项目 docs/reference/，项目副本供后续读取。微信原路径保留用于来源追溯。

2026-11-13补充核验：两条微信临时来源路径当前未找到；项目内下列副本均存在，SHA-256与原登记一致。保留原路径仅作来源追溯，后续读取使用项目副本。

| 名称 | 入口 | 核验与限制 |
|---|---|---|
| PRD V1.2 | [/Users/iknow/Library/Containers/com.tencent.xinWeChat/Data/Documents/xwechat_files/wxid_9t386at625rc22_3a9a/temp/drag/高校宿舍管理系统_PRD_V1.2.md](/Users/iknow/Library/Containers/com.tencent.xinWeChat/Data/Documents/xwechat_files/wxid_9t386at625rc22_3a9a/temp/drag/高校宿舍管理系统_PRD_V1.2.md) | UTF-8，完整读取；含已确认决策、默认假设和技术建议 |
| 数据库设计与 ER 图 V1.0 | [/Users/iknow/Library/Containers/com.tencent.xinWeChat/Data/Documents/xwechat_files/wxid_9t386at625rc22_3a9a/temp/drag/高校宿舍管理系统_数据库设计与ER图_V1.0.md](/Users/iknow/Library/Containers/com.tencent.xinWeChat/Data/Documents/xwechat_files/wxid_9t386at625rc22_3a9a/temp/drag/高校宿舍管理系统_数据库设计与ER图_V1.0.md) | UTF-8，完整读取；设计列 18 张表，未执行数据库验证 |

工程配置复用来源：`/Users/iknow/.agents/skills/setup-matt-pocock-skills/` 下的 SKILL.md、issue-tracker-local.md、triage-labels.md、domain.md。已读取模板并应用用户批准配置；没有新增或登记全局复用资产。

## 本项目配置与记录

版本库：[GitHub 私有仓库 ZhengzhiyangCoder/apartment](https://github.com/ZhengzhiyangCoder/apartment)，本地 main 跟踪 origin/main，已完成初始推送；见[建立日志](../logs/20261111-git-github.md)与[归档任务卡](../task-cards/completed/20261111-git-github.md)。环境凭据及运行产物仅保留本机。

| 内容 | 入口 | 状态 |
|---|---|---|
| 项目协作规则 | [AGENTS.md](../../AGENTS.md) | 已批准并写入 |
| 本地任务跟踪 | [issue-tracker.md](../../docs/agents/issue-tracker.md) | 已批准并写入 |
| 默认分类标签 | [triage-labels.md](../../docs/agents/triage-labels.md) | 已批准并写入 |
| 单领域文档规则 | [domain.md](../../docs/agents/domain.md) | 已批准并写入；领域文档按需建立 |
| 配置任务卡 | [已完成任务](../task-cards/completed/20261111-engineering-skills-setup.md) | 已归档 |
| 配置操作日志 | [日志](../logs/20261111-engineering-skills-setup.md) | 已记录验证与未同步事项 |

## 原始资料项目副本

- [高校宿舍管理系统_PRD_V1.2.md](../../docs/reference/高校宿舍管理系统_PRD_V1.2.md)
  - 原始位置：`/Users/iknow/Library/Containers/com.tencent.xinWeChat/Data/Documents/xwechat_files/wxid_9t386at625rc22_3a9a/temp/drag/高校宿舍管理系统_PRD_V1.2.md`
  - SHA-256：`86793f1a5597deb56d1628e597466788708689d47f2830091b5590c7aa0ea0a1`
  - 状态：字节一致；讨论资料，不自动构成实施授权。
- [高校宿舍管理系统_数据库设计与ER图_V1.0.md](../../docs/reference/高校宿舍管理系统_数据库设计与ER图_V1.0.md)
  - 原始位置：`/Users/iknow/Library/Containers/com.tencent.xinWeChat/Data/Documents/xwechat_files/wxid_9t386at625rc22_3a9a/temp/drag/高校宿舍管理系统_数据库设计与ER图_V1.0.md`
  - SHA-256：`f9c51d17076cae819a2fb3b51fa1d918d74714404b9b4ba0d6e7f74c010bf4af`
  - 状态：字节一致；讨论资料，不自动构成实施授权。

## 需求访谈产物

- [业务词汇](../../CONTEXT.md)：依据用户已接受的附件基线创建，仅领域术语。
- [已确认项目上下文](../context/project.md)：Codex 主导开发、无需工期和教学解释等用户偏好。
- [开发确认稿](../drafts/20261111-development-agreement.md)：用户已接受的业务方案；最新执行顺序为先审核 Spec。
- [已完成访谈任务](../task-cards/completed/20261111-requirements-grilling.md)：设计树、用户回答、确认结果与阶段调整。

## 已确认工程设计

- [工程结构 V1.0](../../docs/engineering-structure.md)、[已完成任务](../task-cards/completed/20261111-engineering-structure.md)、[讨论与定稿日志](../logs/20261111-engineering-structure.md)：单仓库、独立前后端、五业务模块与完整文件组织已确认；采用本机 Node/npm 前端、Java/Maven 后端及 Compose MySQL，仅本地运行。文档已定稿，未创建业务工程。

## 已确认规格交付

- [Spec 审核确认记录](../../.scratch/dorm-management/spec-review.md)：楼栋均分、跨方案维度分组、本地性能目标和“栖居 · 高校宿舍管理系统”名称均已确认，无剩余规格审核决策。
- [开发规格 Spec V1.0](../../.scratch/dorm-management/spec.md)：用户确认完成并定稿，覆盖功能、规则、权限、验收与交付；正式实现尚未启动。
- [已完成 Spec 审核任务](../task-cards/completed/20261111-spec-review.md)：审核结论与当前执行边界。
- [Spec 日志](../logs/20261111-spec-review.md)：来源、验证与阶段记录。

## 已批准开发任务

- [任务总览 V1.0](../../.scratch/dorm-management/task-breakdown.md)：18 项任务的顺序、依赖、故事主责及验收分工；每项正式任务位于同功能的 `issues/`，由总览逐项链接。
- [拆分任务卡](../task-cards/completed/20261111-task-breakdown.md)、[拆分日志](../logs/20261111-task-breakdown.md)：用户已批准，正式票已迁入 `issues/`，任务 01 已完成并验证；Spec 与工程结构未变。

## 界面设计与原型

最新状态（2026-11-11）：仅保留第一版 Neumorphism 作为界面设计基准，第二版及对比产物已按用户要求移除，见[选择记录](../logs/20261111-ui-selection.md)。Spec 已定稿，后续界面细化沿用第一版及已确认名称。

- 用户原始设计要求：`/Users/iknow/Downloads/design.md`，UTF-8，浅色 Neumorphism 风格。
- [设计附件副本](../../docs/reference/界面风格要求_Neumorphism.md)：与原件逐字节一致，SHA-256 `709dbf9a036ad69f0e7803e6e932a04909bad3365e9ede40dd5a1908cca1bd88`。
- [HTML 原型 V0.1](../../prototypes/dorm-management/index.html)：四角色、四工作区、页面内交互模拟；已选定的唯一界面基准。
- [原型说明](../../prototypes/dorm-management/README.md)：设计规格、覆盖范围、审阅路径、实际检查和未实现边界。
- [原型任务卡](../task-cards/active/20261111-ui-prototype.md)、[原型日志](../logs/20261111-ui-prototype.md)。
- [浏览器截图](../../output/playwright/)：两种目标桌面尺寸的工作台、评分、学生主页及报修流程截图。


## 任务 01 实际工程与交付

- [项目 README](../../README.md)、[本地运行](../../docs/local-run.md)、[接口](../../docs/api.md)、[实际数据库](../../docs/database.md)、[模拟演示账号](../../docs/demo.md)。
- [测试记录](../../docs/testing.md)：6 项 API/MySQL、14 项双尺寸浏览器场景、真实迁移/重启与数据库故障反馈。
- [任务 01](../../.scratch/dorm-management/issues/01-local-login.md)、[归档卡](../task-cards/completed/20261111-task01-implement.md)、[实现日志](../logs/20261111-task01-implement.md)、[双轴审查](../logs/20261111-task01-review.md)。
- [当前截图](../../output/playwright/)：task01- 前缀为实际应用；其他截图仍为原型历史产物，不混作业务验收。

这些入口只登记本项目资产，不作为已验证的跨项目全局工具注册。其余功能按正式票推进。


## 任务 02 账号管理交付

- [正式任务 02](../../.scratch/dorm-management/issues/02-account-management.md)、[归档卡](../task-cards/completed/20261111-task02-implement.md)、[实现日志](../logs/20261111-task02-implement.md)、[双轴审查](../logs/20261111-task02-review.md)。
- [接口](../../docs/api.md)、[数据库](../../docs/database.md)、[演示](../../docs/demo.md)、[测试证据](../../docs/testing.md)已扩展到 01–02。
- [实际页面与弹窗截图](../../output/playwright/)：task02-accounts、task02-create、task02-disable 两种宽度；运行产物仅留本机，不进入 Git。


## 任务 03 住宿基础资料交付

- [正式任务 03](../../.scratch/dorm-management/issues/03-housing-catalog.md)、[归档卡](../task-cards/completed/20261111-task03-implement.md)、[实现日志](../logs/20261111-task03-implement.md)、[双轴审查](../logs/20261111-task03-review.md)。
- [接口](../../docs/api.md)、[数据库](../../docs/database.md)、[演示](../../docs/demo.md)、[测试证据](../../docs/testing.md)扩展到 01–03；20 项 API/MySQL、30 项双尺寸浏览器通过。
- 实际迁移 V3–V5，模拟资料脚本为 backend/src/main/resources/db/demo/housing.sql；[截图](../../output/playwright/) task03-rooms、room-conflict、student-form、students，两种宽度；运行产物留本机。
- 沿用既有项目脚本，不安装或注册新的跨项目资产。此前各章节“未实现”等为定稿阶段历史，当前实现以本节和项目上下文为准。


## 任务 04 入住流转交付（2026-11-12）

- [正式任务 04](../../.scratch/dorm-management/issues/04-residency-lifecycle.md)、[归档卡](../task-cards/completed/20261112-task04-implement.md)、[实施日志](../logs/20261112-task04-implement.md)、[独立双轴审查](../logs/20261112-task04-review.md)。
- [接口](../../docs/api.md)、[实际数据库](../../docs/database.md)、[演示](../../docs/demo.md)、[测试证据](../../docs/testing.md)更新至 01–04；28 项全量 API/MySQL、38 项双尺寸浏览器通过。
- V6 住宿历史表、CurrentResidencyQuery 受控当前住宿查询、ResidencyReferences 停用检查均位于 backend housing；前端“入住办理”“我的住宿”实际页面。
- [截图](../../output/playwright/) task04-my-residency / transfer-error / not-resident / history，各两尺寸，共8张；运行产物仅留本机。复用现有演示资料与脚本，未登记全局新资产。
- 下一项为 [05](../../.scratch/dorm-management/issues/05-student-repairs.md)，04/05/08/10/11 已记录跨业务衔接；后续任务未提前执行。

## 任务05实现与验证（2026-11-13）

- [任务票](../../.scratch/dorm-management/issues/05-student-repairs.md)、[归档卡](../task-cards/completed/20261113-task05-implement.md)、[实施日志](../logs/20261113-task05-implement.md)、[审查](../logs/20261113-task05-review.md)。
- [V7迁移](../../backend/src/main/resources/db/migration/V7__student_repairs.sql)、[报修后端](../../backend/src/main/java/com/example/apartment/modules/repair/)、[私有图片存储](../../backend/src/main/java/com/example/apartment/support/storage/RepairImageStorage.java)、[前端](../../frontend/src/features/repair/)。
- [API测试](../../backend/src/test/java/com/example/apartment/repair/RepairHttpTest.java)、[浏览器测试](../../frontend/e2e/repairs.spec.ts)、[测试结果](../../docs/testing.md)、[模拟演示](../../docs/demo.md)。36后端/44全量浏览器、修正后36后端/6报修浏览器通过。
- [API](../../docs/api.md)、[数据库](../../docs/database.md)、[运行](../../docs/local-run.md)随片更新。V1–V7空库及演示升级、重复seed与重启后的图片摘要验证完成。
- 项目内复用既有脚本/测试与受控身份住宿边界，未安装或登记全局新资产。临时模拟/验证脚本在忽略的.runtime，仅本项目本轮使用；不登记为跨项目可用资产。下一项06待开始。


## 任务06派单与维修闭环（2026-11-16）

- [任务票](../../.scratch/dorm-management/issues/06-repair-workflow.md)、[归档卡](../task-cards/completed/20261116-task06-implement.md)、[实施](../logs/20261116-task06-implement.md)、[双轴审查](../logs/20261116-task06-review.md)。
- [V8迁移](../../backend/src/main/resources/db/migration/V8__repair_workflow.sql)、[后端](../../backend/src/main/java/com/example/apartment/modules/repair/)、[界面](../../frontend/src/features/repair/)、[API测试](../../backend/src/test/java/com/example/apartment/repair/RepairHttpTest.java)、[浏览器测试](../../frontend/e2e/repairs.spec.ts)。
- [测试结果](../../docs/testing.md)：43后端/50全量双尺寸浏览器，空库V1–V8，演示升级和重启持久化一致；[演示](../../docs/demo.md)、[API](../../docs/api.md)、[数据库](../../docs/database.md)已更新。
- [截图](../../output/playwright/)task06-assign-error/complete-error/student-result/reassign-conflict/access-revoked/pending-cancel各两尺寸共12张，运行产物不入Git。复用项目既有脚本与测试，无新增全局资产；一次性演示核验脚本仅保留.runtime，不登记跨项目可用。下一项07待开始。


## 任务07评分方案与版本（2026-11-16）

- [任务票](../../.scratch/dorm-management/issues/07-scoring-schemes.md)、[归档卡](../task-cards/completed/20261116-task07-implement.md)、[实施](../logs/20261116-task07-implement.md)、[独立审查](../logs/20261116-task07-review.md)。
- [V9迁移](../../backend/src/main/resources/db/migration/V9__hygiene_schemes.sql)、[后端](../../backend/src/main/java/com/example/apartment/modules/hygiene/)、[页面](../../frontend/src/features/hygiene/)、[API测试](../../backend/src/test/java/com/example/apartment/hygiene/SchemeHttpTest.java)、[浏览器测试](../../frontend/e2e/schemes.spec.ts)。
- [测试](../../docs/testing.md)：50后端/58全量浏览器、空库V1–V9、演示升级与重启持久化通过；[演示](../../docs/demo.md)、[API](../../docs/api.md)、[数据库](../../docs/database.md)、[运行](../../docs/local-run.md)已更新。
- [截图](../../output/playwright/) task07-history/manager/form-error/draft-conflict/activate-conflict/list各两尺寸共12张；运行产物不入Git。复用项目现有工具，无新全局资产；一次性核验脚本留.runtime，不登记跨项目可用。下一项08承接真实方案绑定与冻结回归。


## 任务08本周检查与正式评分（2026-11-16）

- [任务票](../../.scratch/dorm-management/issues/08-weekly-first-score.md)、[归档卡](../task-cards/completed/20261116-task08-implement.md)、[实施](../logs/20261116-task08-implement.md)、[独立审查](../logs/20261116-task08-review.md)。
- [V10](../../backend/src/main/resources/db/migration/V10__weekly_inspections.sql)、[V11](../../backend/src/main/resources/db/migration/V11__formal_scores.sql)、[后端](../../backend/src/main/java/com/example/apartment/modules/hygiene/)、[页面](../../frontend/src/features/hygiene/InspectionsPage.vue)、[HTTP测试](../../backend/src/test/java/com/example/apartment/hygiene/BatchHttpTest.java)、[浏览器测试](../../frontend/e2e/batches.spec.ts)。
- [测试结果](../../docs/testing.md)：61后端/66全量浏览器、空库V1–V11、演示升级与重启持久化；[演示](../../docs/demo.md)、[API](../../docs/api.md)、[数据库](../../docs/database.md)、[运行](../../docs/local-run.md)已更新。
- [截图](../../output/playwright/)task08-score/progress/network/conflict/empty/list各两尺寸共12张，运行产物不入Git。复用已有脚本/测试/业务锁，无新全局资产；.runtime一次性验证不登记为跨项目工具。下一项09未启动。


## 任务09连续评分与个人草稿（2026-11-16）

- [任务票](../../.scratch/dorm-management/issues/09-continuous-scoring-drafts.md)、[归档卡](../task-cards/completed/20261116-task09-implement.md)、[实施](../logs/20261116-task09-implement.md)、[独立审查](../logs/20261116-task09-review.md)。
- [V12](../../backend/src/main/resources/db/migration/V12__personal_score_drafts.sql)、[后端](../../backend/src/main/java/com/example/apartment/modules/hygiene/)、[页面](../../frontend/src/features/hygiene/InspectionsPage.vue)、[API测试](../../backend/src/test/java/com/example/apartment/hygiene/BatchHttpTest.java)、[浏览器测试](../../frontend/e2e/batches.spec.ts)。
- [测试](../../docs/testing.md)：67后端/76全量浏览器、空库V1–V12、演示草稿和原业务重启持久化；[演示](../../docs/demo.md)、[API](../../docs/api.md)、[数据库](../../docs/database.md)、[运行](../../docs/local-run.md)已更新。
- [截图](../../output/playwright/)task09-draft/complete/submit-failure/conflict各两尺寸共8张。复用已有项目工具，未安装/登记全局资产；一次性清库/演示核验只存.runtime，不作通用初始化入口。下一项10接续真实批次治理。


## 任务10批次治理与历史补录（2026-11-16）

- [任务票](../../.scratch/dorm-management/issues/10-batch-governance.md)、[归档卡](../task-cards/completed/20261116-task10-implement.md)、[实施](../logs/20261116-task10-implement.md)、[双轴审查](../logs/20261116-task10-review.md)。
- [V13迁移](../../backend/src/main/resources/db/migration/V13__batch_governance.sql)、[卫生后端](../../backend/src/main/java/com/example/apartment/modules/hygiene/)、[治理组件](../../frontend/src/features/hygiene/BatchGovernance.vue)、[公开API测试](../../backend/src/test/java/com/example/apartment/hygiene/BatchGovernanceHttpTest.java)、[浏览器测试](../../frontend/e2e/batches.spec.ts)。
- [测试证据](../../docs/testing.md)：75后端、82全量双尺寸浏览器与修正后28卫生回归，空库迁移、原地升级及重启持久化通过；[演示](../../docs/demo.md)、[接口](../../docs/api.md)、[数据库](../../docs/database.md)、[运行](../../docs/local-run.md)同步更新。
- [截图](../../output/playwright/)task10-removed-draft/changes/roster/history，两尺寸8张。运行产物与一次性.runtime校验脚本不入Git、不注册为全局可用资产。下一项11未启动。


## 任务11学生成绩、趋势与排行（2026-11-16）

- [任务票](../../.scratch/dorm-management/issues/11-student-results-rankings.md)、[归档卡](../task-cards/completed/20261116-task11-implement.md)、[实施](../logs/20261116-task11-implement.md)、[双轴审查](../logs/20261116-task11-review.md)。
- [统计查询](../../backend/src/main/java/com/example/apartment/modules/hygiene/ResultMapper.java)、[学生服务](../../backend/src/main/java/com/example/apartment/modules/hygiene/StudentResultsService.java)、[页面](../../frontend/src/features/hygiene/StudentResultsPage.vue)、[API测试](../../backend/src/test/java/com/example/apartment/hygiene/StudentResultsHttpTest.java)、[浏览器测试](../../frontend/e2e/results.spec.ts)。
- [测试](../../docs/testing.md)：81后端、96全量两尺寸浏览器、构建及演示重启数据不变；[演示](../../docs/demo.md)、[API](../../docs/api.md)、[数据库](../../docs/database.md)、[运行](../../docs/local-run.md)更新。
- [截图](../../output/playwright/)task11-results/detail/transferred/empty各两尺寸。无新依赖或迁移；复用既有测试工具，.runtime一次性脚本不登记全局资产。下一项12待开始。


## 任务12管理端统计（2026-11-16）

- [任务票](../../.scratch/dorm-management/issues/12-management-statistics.md)、[归档卡](../task-cards/completed/20261116-task12-implement.md)、[实施](../logs/20261116-task12-implement.md)、[独立审查](../logs/20261116-task12-review.md)。
- [统计查询](../../backend/src/main/java/com/example/apartment/modules/hygiene/StatisticsMapper.java)、[管理服务](../../backend/src/main/java/com/example/apartment/modules/hygiene/StatisticsService.java)、[共用明细](../../backend/src/main/java/com/example/apartment/modules/hygiene/ResultDetailsQuery.java)、[实际页面](../../frontend/src/features/hygiene/StatisticsPage.vue)、[API测试](../../backend/src/test/java/com/example/apartment/hygiene/ManagementStatisticsHttpTest.java)、[浏览器测试](../../frontend/e2e/statistics.spec.ts)。
- [测试](../../docs/testing.md)：87后端/104全量双尺寸Chrome、构建、原演示数据与图片摘要重启保留；[API](../../docs/api.md)、[数据库](../../docs/database.md)、[演示](../../docs/demo.md)、[运行](../../docs/local-run.md)同步更新。
- [截图](../../output/playwright/)task12-building/room/empty各两尺寸共6张。复用既有项目工具、学生图表与历史明细，不新增依赖/迁移；.runtime一次性核验不登记全局资产。下一项13未启动。


## 任务13低分整改闭环（2026-11-16）

- [任务票](../../.scratch/dorm-management/issues/13-rectification.md)、[归档卡](../task-cards/completed/20261116-task13-implement.md)、[实施](../logs/20261116-task13-implement.md)、[独立审查](../logs/20261116-task13-review.md)。
- [整改服务](../../backend/src/main/java/com/example/apartment/modules/hygiene/RectificationService.java)、[查询与写入](../../backend/src/main/java/com/example/apartment/modules/hygiene/RectificationMapper.java)、[V14迁移](../../backend/src/main/resources/db/migration/V14__rectification_workflow.sql)、[管理页面](../../frontend/src/features/hygiene/RectificationsPage.vue)、[API测试](../../backend/src/test/java/com/example/apartment/hygiene/RectificationHttpTest.java)、[浏览器测试](../../frontend/e2e/rectifications.spec.ts)。
- [测试](../../docs/testing.md)：94后端/114全量双尺寸Chrome及构建，空库迁移/演示增量迁移与数据保留；[API](../../docs/api.md)、[数据库](../../docs/database.md)、[演示](../../docs/demo.md)、[运行](../../docs/local-run.md)已更新。
- [截图](../../output/playwright/)task13-closed/follow-up/list各两尺寸6张，task13-demo-list/detail共2张。复用既有锁/审计/版本/引用守卫及项目工具，无新依赖；.runtime仅本轮证据，不登记全局资产。下一项14。
