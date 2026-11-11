# 工程技能配置

- 日期：2026-11-11（Asia/Shanghai）
- 状态：已完成（工程配置已写入并验证）；全局项目入口待同步，见当前 todo。
- 用户目标：为宿舍管理系统毕设项目执行 setup-matt-pocock-skills，参考两份 GPT 讨论附件。
- 技能：`/Users/iknow/.agents/skills/setup-matt-pocock-skills/SKILL.md`

## 已确认的本机事实

- 开始时项目目录为空，不是 Git 仓库，没有远程地址。
- 没有 AGENTS.md、CLAUDE.md、CONTEXT.md、CONTEXT-MAP.md、docs/agents/ 或历史任务记录。
- 没有 .codegraph/ 或 monorepo 信号。
- `/Users/iknow/.agents/skills/triage/SKILL.md` 存在；仅确认安装，未执行 triage。
- 已读取总索引 README.md、route-map.md、执行细则及项目索引；没有匹配 apartment/宿舍的既有入口。
- 两份附件可按 UTF-8 读取，已完整阅读。

## 附件信息（来源陈述，不等于本轮新确认）

- PRD V1.2：电脑端 Web，四类角色，住宿管理、报修、每周卫生评分与统计；跨楼栋连续评分、学生限当前楼栋排行。
- 技术栈为建议：Spring Boot、MyBatis-Plus、Vue 3、TypeScript、Element Plus、MySQL 8、ECharts；本轮未选定或安装。
- 数据库设计 V1.0：18 张表，含应检快照、评分版本与历史住宿；本轮未验证数据库。
- 文档提及配套 SQL，但用户本次只提供两份 Markdown；未读取该 SQL。
- PRD 同时含“已确认决策”和默认业务假设，后续应保留来源与确认层级。

## 已确认配置与交付

1. 用户选择本地 Markdown：规格与开发任务使用 `.scratch/<feature-slug>/`。
2. 用户确认默认五种 triage 标签。
3. 按技能默认使用 single-context，CONTEXT.md 与 ADR 在内容明确后按需创建。
4. 用户已审阅完整草稿，并明确选择“确认草稿，创建 AGENTS.md（推荐）”。
5. 已写入根目录 `AGENTS.md` 以及 `docs/agents/issue-tracker.md`、`triage-labels.md`、`domain.md`。
6. 已核对四份配置与批准草稿逐字一致、UTF-8/LF、Agent skills 段唯一、配置与工作记录引用可达、五种分类映射正确。

## 范围与后续事项

- 本轮只完成工程技能的项目配置，未安装技能、初始化 Git、安装依赖、生成业务代码或验证数据库。
- 两份附件仍保留在原微信临时目录；位置见资料索引。
- 记录中的单领域布局不等于已完成业务领域建模；技术建议与业务默认假设未被本轮静默升级为已确认决策。
- 总索引已读取，但全局项目入口位于当前可写范围外，尚未新增；已在当前 todo 记录后续同步与解除条件。
- 现有技能和模板已满足需求，无需新增全局复用资产。

相关记录：[操作日志](../../logs/20261111-engineering-skills-setup.md)、[已批准草稿](../../drafts/engineering-skills-setup/review.md)。
