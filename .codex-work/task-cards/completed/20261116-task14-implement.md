# 任务14实施

日期：2026-11-16。状态：已完成。用户明确要求继续任务14。

- 票据：[任务14](../../../.scratch/dorm-management/issues/14-score-corrections.md)，起点 main f68404120e90caeac32dac054b43921d44d376ac，工作区干净，无 CodeGraph。
- 沿用已批准公开 HTTP、真实隔离 MySQL、实际 Chrome 测试边界；API 与浏览器顺序执行，共用 apartment_test；保留 apartment_demo。
- 复用 tdd、springboot-patterns、playwright 和 code-review。审查固定点为本轮起点，符合既有实现后双轴审查流程。
- 切片：原身份/方案更正及完整历史 → 并发和统计/整改同事务 → 实际页面与恢复输入 → 全量验证、独立审查、演示及归档。
- 不新增依赖、不改既有迁移；总索引跨目录同步仍待确认，不阻塞任务。

见[实施日志](../../logs/20261116-task14-implement.md)。

结果：101项全量HTTP/MySQL、124项全量双尺寸Chrome及前后端构建通过；V1–V16空库迁移和演示V14→V16通过。独立Standards0；Spec唯一P2红绿修正并复核关闭。明确模拟A103保留原50触发/跟进，经同id更正55→85，仍待核实；原其他业务与图片保留，普通seed和真实重启完整快照一致。实现7139d92、修正917d657本地main，未推送；归档另行提交。下一项15，跨目录总索引同步仍待授权。
