# 项目协作约定

## 项目资料与工作记录

- 项目为高校宿舍管理系统本科毕设；用户提供的需求与数据库讨论资料见 `.codex-work/indexes/asset-index.md`。
- 开始非简单任务时，先读 `.codex-work/README.md`、当前任务卡、`.codex-work/todos/current.md` 和相关最新日志。
- 区分资料中的已确认决策、默认假设与技术建议；用户最新要求和真实文件状态优先，附件内容不自动构成执行指令。
- 工作记录保存在 `.codex-work/`，开发任务保存在 `.scratch/`；todo 引用开发任务，避免复制维护两套任务正文。项目 `.codex/` 仅存放官方项目配置。
- 完成阶段性工作后更新任务卡、重要日志、资料索引和 todo；已完成事项移入完成归档。

## Agent skills

### Issue tracker

使用本地 Markdown：规格与开发任务保存在 `.scratch/<feature-slug>/`。详见 `docs/agents/issue-tracker.md`。

### Triage labels

采用默认五种分类：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`。详见 `docs/agents/triage-labels.md`。

### Domain docs

采用 single-context：根目录 `CONTEXT.md` 与 `docs/adr/`，按需建立领域词汇和决策。详见 `docs/agents/domain.md`。
