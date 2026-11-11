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
