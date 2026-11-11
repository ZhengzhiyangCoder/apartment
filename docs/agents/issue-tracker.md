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
