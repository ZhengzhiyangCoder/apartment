# 任务14实施

日期：2026-11-16。状态：进行中。用户明确要求继续任务14。

- 票据：[任务14](../../../.scratch/dorm-management/issues/14-score-corrections.md)，起点 main f68404120e90caeac32dac054b43921d44d376ac，工作区干净，无 CodeGraph。
- 沿用已批准公开 HTTP、真实隔离 MySQL、实际 Chrome 测试边界；API 与浏览器顺序执行，共用 apartment_test；保留 apartment_demo。
- 复用 tdd、springboot-patterns、playwright 和 code-review。审查固定点为本轮起点，符合既有实现后双轴审查流程。
- 切片：原身份/方案更正及完整历史 → 并发和统计/整改同事务 → 实际页面与恢复输入 → 全量验证、独立审查、演示及归档。
- 不新增依赖、不改既有迁移；总索引跨目录同步仍待确认，不阻塞任务。

见[实施日志](../../logs/20261116-task14-implement.md)。
