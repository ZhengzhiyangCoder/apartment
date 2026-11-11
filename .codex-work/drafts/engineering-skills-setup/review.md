# 工程配置已批准草稿（留档）

日期：2026-11-11。用户已审阅并批准，选择创建 AGENTS.md；四份配置已原样应用并验证。以下为批准时的内容留档，当前有效文件以根目录和 docs/agents/ 为准。

已确认：本地 Markdown 任务跟踪；默认五种 triage 标签。
领域布局：按技能默认 single-context，不预先创建空 CONTEXT.md 或 ADR。
用户已选择：根目录 AGENTS.md。

已写入以下四份文件，并更新 `.codex-work/` 工作记录。本轮未初始化 Git、安装依赖或生成业务代码。

## 根目录 AGENTS.md

````markdown
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
````

## docs/agents/issue-tracker.md

````markdown
# Issue tracker: Local Markdown

本项目的功能规格与开发任务使用本地 Markdown 文件，存放在 `.scratch/`。

## 文件约定

- 每项功能一个目录：`.scratch/<feature-slug>/`。
- 功能规格：`.scratch/<feature-slug>/spec.md`。
- 实现任务：`.scratch/<feature-slug>/issues/<NN>-<slug>.md`，从 `01` 编号，一项任务一个文件，不合并成单一任务清单。
- 分类状态写在任务顶部的 `Status:` 行，取值见 `triage-labels.md`。
- 讨论与补充说明追加在文件末尾的 `## Comments` 下。
- `.codex-work/` 保存任务执行记录、日志和项目级待办；通过链接指向这里的开发任务，不重复维护正文。

## 技能要求“发布到 issue tracker”时

在 `.scratch/<feature-slug>/` 下创建对应文件；实现任务必须进入 `issues/` 子目录。

## 技能要求“读取相关任务”时

读取用户给出的路径或编号对应的任务文件。不同功能存在相同编号时，结合功能目录定位；仍有歧义才询问。

## Wayfinder 操作约定

使用 `/wayfinder` 时，工作地图与其子任务采用以下约定：

- 地图：`.scratch/<effort>/map.md`，包含 Notes、Decisions-so-far、Fog。
- 子任务：`.scratch/<effort>/issues/NN-<slug>.md`，从 `01` 编号，正文记录待解决问题。
- `Type:` 为 `research`、`prototype`、`grilling` 或 `task`。
- Wayfinder 的 `Status:` 使用流程状态 `open`、`claimed`、`resolved`，与普通实现任务的五种 triage 分类分别使用。
- 阻塞依赖：顶部写 `Blocked by: NN, NN`；所有依赖均为 `resolved` 后才解除阻塞。
- 下一项工作：按编号选择尚未解决、无阻塞且未认领的任务。
- 认领：开始工作前设置 `Status: claimed` 并保存。
- 解决：在 `## Answer` 下追加答案，设置 `Status: resolved`，再向地图的 Decisions-so-far 追加摘要与文件链接。
````

## docs/agents/triage-labels.md

````markdown
# Triage Labels

使用五种默认分类角色；本地任务通过顶部的 `Status:` 行记录对应字符串。

| 技能中的分类角色 | 本项目状态字符串 | 含义 |
|---|---|---|
| `needs-triage` | `needs-triage` | 待评估需求与处理方式 |
| `needs-info` | `needs-info` | 等待补充信息 |
| `ready-for-agent` | `ready-for-agent` | 规格充分，可由 AI 继续实现 |
| `ready-for-human` | `ready-for-human` | 需要人工实现或处理 |
| `wontfix` | `wontfix` | 明确不处理 |

技能提及分类角色时，使用本表对应字符串。后续如需改名，编辑第二列并保持任务文件一致。

这些分类不额外授予安装、发布、扩大权限或对外发送消息的权限。Wayfinder 子任务的流程状态见 `issue-tracker.md`。
````

## docs/agents/domain.md

````markdown
# Domain Docs

本项目采用 **single-context**，工程技能按以下规则读取领域资料。

## 探索前读取

- 根目录 `CONTEXT.md`：领域词汇、已明确的业务含义与边界。
- `docs/adr/`：与当前改动有关的架构或业务决策记录。
- 若未来出现根目录 `CONTEXT-MAP.md`，按其中映射读取相关上下文的 `CONTEXT.md`，并检查对应的领域 ADR。

以上文件不存在时，正常继续，不单独提示缺失，也不为凑齐结构创建空文档。由 `domain-modeling` 等工作在词汇或决策实际明确时按需建立。

## 文件布局

```text
/
├── CONTEXT.md          # 按需创建
└── docs/
    └── adr/           # 按需创建，0001-<decision>.md 等
```

## 使用领域词汇

任务标题、设计说明、测试名称等采用 `CONTEXT.md` 定义的术语，避免使用其明确排除的同义词。缺少所需术语时，先判断是否误造概念；确属缺口则记录给后续领域建模处理。

## 处理决策冲突

方案与现有 ADR 冲突时，明确指出具体 ADR、冲突内容与重新讨论的理由，不静默覆盖原有决策。

## 来源与执行记录

用户提供的业务资料通过 `.codex-work/indexes/asset-index.md` 查阅；保留原文中的确认层级。执行日志、当前 todo 和任务卡放在 `.codex-work/`，不混入领域词汇或 ADR。
````

