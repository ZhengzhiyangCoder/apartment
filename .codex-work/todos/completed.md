# 已完成待办

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
