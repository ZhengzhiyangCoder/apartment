# 已完成待办

## 2026-11-16 任务06 派单与维修闭环

- 七条准则闭环，43后端/50双尺寸浏览器及构建通过，Standards与Spec均0发现。
- V8升级、空库V1–V8、重复seed及真实重启持久化通过。见[归档卡](../task-cards/completed/20261116-task06-implement.md)、[实施日志](../logs/20261116-task06-implement.md)、[审查](../logs/20261116-task06-review.md)。

## 2026-11-13 任务05 学生图文报修

- 8条准则闭环，图文提交/本人历史/三角色取消/安全附件/停用保护已交付；36后端、44全量浏览器通过，审查修正后36后端及6报修浏览器复验通过。
- V7升级、空库迁移、重复seed和实际重启持久化通过。见[归档卡](../task-cards/completed/20261113-task05-implement.md)、[实施日志](../logs/20261113-task05-implement.md)、[双轴审查](../logs/20261113-task05-review.md)。

## 2026-11-12 任务 04 入住流转

- [任务 04](../../.scratch/dorm-management/issues/04-residency-lifecycle.md)已完成：入住、原子调寝、退宿、历史与本人住宿查询，数据库唯一约束、关联停用保护和事务审计。
- 28 项全量 API/MySQL、38 项双尺寸浏览器、构建及两轴审查闭环；完整空库迁移、重复 seed 与实际重启持久化通过。见[实现日志](../logs/20261112-task04-implement.md)、[测试](../../docs/testing.md)、[审查](../logs/20261112-task04-review.md)、[归档卡](../task-cards/completed/20261112-task04-implement.md)。

## 2026-11-11 任务 03 住宿基础资料

- [任务 03](../../.scratch/dorm-management/issues/03-housing-catalog.md)已完成：楼栋、寝室、床位、学生资料的实际持久化、分页、表单、启停、账号一致性与事务审计。
- 20 项全量 API/MySQL、30 项双尺寸浏览器及前后端构建通过，审查建议已修正并复核，无剩余发现。见[实现日志](../logs/20261111-task03-implement.md)、[测试](../../docs/testing.md)、[审查](../logs/20261111-task03-review.md)、[归档卡](../task-cards/completed/20261111-task03-implement.md)。

## 2026-11-11 任务 02 账号管理

- [任务 02](../../.scratch/dorm-management/issues/02-account-management.md)已完成：创建、查询分页、角色变更、重置与启停、旧会话撤销、并发保护及同事务审计。
- 全量 12 项 API/MySQL、22 项双尺寸浏览器及前后端构建通过，Standards / Spec 各 0 发现。见[实现日志](../logs/20261111-task02-implement.md)、[测试](../../docs/testing.md)、[审查](../logs/20261111-task02-review.md)与[归档卡](../task-cards/completed/20261111-task02-implement.md)。

## 2026-11-11 Git 与 GitHub 私有仓库

- 按用户要求初始化本地 main，创建 [ZhengzhiyangCoder/apartment](https://github.com/ZhengzhiyangCoder/apartment) 私有仓库并推送初始提交；main 跟踪 origin/main。
- 凭据、依赖及运行产物排除已验证，本地与远端提交一致。见[归档卡](../task-cards/completed/20261111-git-github.md)与[日志](../logs/20261111-git-github.md)。

## 2026-11-11 正式票发布与任务 01

- 用户认可18项拆分及依赖，已迁入正式 issues；拆分任务归档。
- [任务 01](../../.scratch/dorm-management/issues/01-local-login.md)已完成：真实本地运行、四角色登录退出/资料/改密，6项API/MySQL及14项双尺寸浏览器场景通过。
- 记录：[实现日志](../logs/20261111-task01-implement.md)、[审查](../logs/20261111-task01-review.md)、[验收证据](../../docs/testing.md)、[归档任务卡](../task-cards/completed/20261111-task01-implement.md)。

## 2026-11-11 任务拆分草稿形成

- 完成[审核总览](../../.scratch/dorm-management/task-breakdown.md)与 18 份逐项审核草稿，包含直接依赖、44 条用户故事主责、24 项验收分工及安全性能要求，见[日志](../logs/20261111-task-breakdown.md)。
- 本条只归档“草稿形成”；用户审核与正式发布仍在[当前待办](current.md)，尚未进入 implement。

## 2026-11-11 Spec V1.0 定稿

- 用户在了解用途后接受楼栋均分的寝室等权计算；R1–R3 与 N1 全部确认，[Spec V1.0](../../.scratch/dorm-management/spec.md)已定稿。
- 已同步[确认记录](../../.scratch/dorm-management/spec-review.md)、业务词汇、项目上下文与导航，并归档[审核任务](../task-cards/completed/20261111-spec-review.md)，见[日志](../logs/20261111-spec-review.md)。
- 本轮仅完成文档，业务工程与实际验收尚未开始，后续阶段按用户指示推进。

## 2026-11-11 Spec 部分确认

- 用户确认 R2 跨方案维度分组、R3 本地性能验收目标，并保留 N1 原型名称“栖居 · 高校宿舍管理系统”；相关文档已同步，见[审核清单](../../.scratch/dorm-management/spec-review.md)和[日志](../logs/20261111-spec-review.md)。
- R1 楼栋均分计算口径仍待确认，Spec 整体尚未定稿；本条只归档已确认事项。

## 2026-11-11 工程结构定稿

- 用户接受完整目录与职责，并确认本机独立启动前后端、Compose 仅启动 MySQL 的方案。
- [工程结构 V1.0](../../docs/engineering-structure.md) 已定稿，Spec 对应工程条目、上下文及导航已同步；[任务卡](../task-cards/completed/20261111-engineering-structure.md)已归档，详见[日志](../logs/20261111-engineering-structure.md)。
- 仅完成文档，尚未创建业务工程；Spec 其余审核仍开放。

## 2026-11-11 界面方案选择

- 用户明确“选第一版”，并进一步要求不保留第二版参考。已删除第二版 HTML、说明、设计附件项目副本、两版对比页及相关截图和测试快照；仅保留第一版。
- 已同步上下文、任务卡与导航，见[选择记录](../logs/20261111-ui-selection.md)。本次仅确定界面方案，Spec 审核仍开放。

## 2026-11-11 Soft UI 方案 B（已移除）

- 依据新附件、未使用技能完成另一版单文件 HTML；保留 A 原型，新增带实际截图的风格对比入口。
- 当时完成 118 次页面/页签访问、13 项主要交互及对比链接核验；用户后续选定第一版并要求移除第二版，产物已清理。此条仅为操作历史，见 [历史日志](../logs/20261111-soft-ui-prototype.md)。Spec 审核仍开放。

## 2026-11-11 HTML 原型首版

- 按用户新拟态设计附件交付四角色 HTML 交互原型、说明和截图；实际桌面浏览器主要流程检查通过。
- 详见[原型日志](../logs/20261111-ui-prototype.md)。设计审阅仍开放，不代表 Spec 已批准或正式系统已实施。

## 2026-11-11 工程技能配置

- 已完成环境与资料检查、本地 Markdown 选择、默认 triage 标签确认、完整草稿审阅及 AGENTS.md 创建确认。
- 已生成四份配置并验证与批准草稿一致、UTF-8/LF、引用路径和标签映射。
- 已归档[任务卡](../task-cards/completed/20261111-engineering-skills-setup.md)，详见[操作日志](../logs/20261111-engineering-skills-setup.md)。

## 2026-11-11 需求访谈阶段

- 用户确认完整业务方案、Codex 主导开发及无需教学解释。
- 后续明确先审核 spec；已转入规格审核，未开始实现。
- [已完成访谈任务](../task-cards/completed/20261111-requirements-grilling.md)、[访谈日志](../logs/20261111-requirements-grilling.md)。

## 2026-11-16 · 任务07评分方案与版本

- 完成评分维度/权重/阈值、草稿、启用与历史版本、复制调整、权限及审计；50后端/58全量浏览器、构建、空库V1–V9、演示升级与重启持久化通过，独立双轴0发现。实现6a14939提交本地main，未推送；真实批次冻结由08承接。
- [任务票](../../.scratch/dorm-management/issues/07-scoring-schemes.md)、[归档卡](../task-cards/completed/20261116-task07-implement.md)、[实施](../logs/20261116-task07-implement.md)、[审查](../logs/20261116-task07-review.md)。下一项08；总索引跨目录同步仍在current待办。

## 2026-11-16 · 任务08本周检查与正式评分

- 完成当前ISO周显式准备、名单/方案快照、宿管正式录分、精确计算、并发去重、事务审计、进度及未完应检停用保护；61后端/66全量浏览器、构建、空库V1–V11、演示升级与重启持久化通过，双轴各0发现。实现b7fcc8b在本地main，未推送。
- [任务票](../../.scratch/dorm-management/issues/08-weekly-first-score.md)、[归档卡](../task-cards/completed/20261116-task08-implement.md)、[实施](../logs/20261116-task08-implement.md)、[审查](../logs/20261116-task08-review.md)。下一项09未启动；总索引同步沿用current待办。


## 2026-11-16 · 任务09连续评分与个人草稿

- 完成本人服务端草稿、重登恢复、多窗口版本保护、正式提交原子清理及跨楼栋连续评分；67后端/76全量浏览器、构建、空库V1–V12、演示草稿重启持久化通过。独立双轴无确定缺陷；实现d51d76f在本地main，未推送。
- [任务票](../../.scratch/dorm-management/issues/09-continuous-scoring-drafts.md)、[归档卡](../task-cards/completed/20261116-task09-implement.md)、[实施](../logs/20261116-task09-implement.md)、[审查](../logs/20261116-task09-review.md)。下一项10未启动；总索引跨目录同步仍在current。


## 2026-11-16 · 任务10批次治理与历史补录

- 带原因的名单补入/移出、关闭/停检/恢复/重开、个人草稿只读恢复、历史授权跨周保留及变更追溯完成。75后端、82全量浏览器及修正后28卫生回归、构建、空库V1–V13和演示重启持久化通过。
- [任务票](../../.scratch/dorm-management/issues/10-batch-governance.md)、[归档卡](../task-cards/completed/20261116-task10-implement.md)、[实施](../logs/20261116-task10-implement.md)、[审查](../logs/20261116-task10-review.md)。Standards0违例；Spec两项P2已修复复核。实现4157698、修正52a8a9f本地提交未推送。下一项11；总索引同步保留current。


## 2026-11-16 · 任务11学生成绩、趋势与排行

- 完成当前寝室授权、周月成绩与历史标准、12周期趋势、当前楼栋匿名榜单；停检排除/恢复、原周补录、舍入后竞赛排名、调寝新范围及旧报修验证通过。
- 81后端/96全量双尺寸Chrome、构建及演示重启原数据不变；Spec1项P3修复闭环，Standards零违例。实现308506c、修正4f5950c本地提交，未推送。
- [归档卡](../task-cards/completed/20261116-task11-implement.md)、[实施](../logs/20261116-task11-implement.md)、[审查](../logs/20261116-task11-review.md)。下一项12待开始，当前todo只保留后续及总索引同步。


## 2026-11-16 · 任务12管理端统计

- 完成管理周月全校/楼栋/寝室查询、寝室等权楼栋均分、应检进度、趋势空缺、楼内名次、跨版本维度分组和原标准明细。87后端/104全量双尺寸Chrome及构建通过，无新迁移或依赖，演示原数据及图片保留。
- 独立Standards零违例；Spec侧栏P2已红测修复复核闭环。实现ec51cad、修正04ae2d7本地提交未推送。[归档卡](../task-cards/completed/20261116-task12-implement.md)、[实施](../logs/20261116-task12-implement.md)、[审查](../logs/20261116-task12-review.md)。下一项13，current仅保留后续及总索引同步。


## 2026-11-16 · 任务13低分整改闭环

- 7条准则完成，固定低分触发、并发去重、跟进/核实/关闭、事务与停用保护。94后端/114全量双尺寸Chrome及构建、空库V1–V14、演示升级/持久化通过。独立审查补充P2已修复并复核，无剩余发现。
- 实现f1c9205、修正5e338b3本地提交未推送。[归档卡](../task-cards/completed/20261116-task13-implement.md)、[实施](../logs/20261116-task13-implement.md)、[审查](../logs/20261116-task13-review.md)。current仅保留14及总索引同步。


## 2026-11-16 任务14 成绩更正与整改联动

- 七条准则闭环；101后端/124全量双尺寸Chrome、构建、V1–V16空库及演示原地迁移/普通seed/真实重启持久化通过。
- 独立Standards0，Spec唯一P2已红绿修正并独立复核关闭。见[归档卡](../task-cards/completed/20261116-task14-implement.md)、[实施](../logs/20261116-task14-implement.md)、[审查](../logs/20261116-task14-review.md)。下一项15未启动。


## 2026-11-16 任务15 审计查询与业务追溯

- 六条准则完成；106后端/142全量双尺寸Chrome、18项专项及构建通过，演示只读快照/图片完整保留，无新迁移/依赖。
- 独立Standards与Spec发现均真实红绿修正并复核关闭。见[归档卡](../task-cards/completed/20261116-task15-implement.md)、[实施](../logs/20261116-task15-implement.md)、[审查](../logs/20261116-task15-review.md)。下一项16未启动。
