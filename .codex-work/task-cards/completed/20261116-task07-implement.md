# 任务07实施

日期：2026-11-16。状态：已完成。用户在06完成后要求“继续开发”，按当前todo推进07。

- 范围：[07评分方案](../../../.scratch/dorm-management/issues/07-scoring-schemes.md)，前置02已完成；基点main `9fe0a8a`，工作区干净，无CodeGraph。
- 复用已读tdd / springboot-patterns / database-migrations / playwright技能与现有Session、业务锁、审计、页面样式和测试脚本；公开HTTP/真实MySQL/Chrome测试授权沿用，API与浏览器顺序执行。
- 流程：创建/编辑草稿 → 精确权重校验与唯一启用 → 复制新版本和历史只读 → 页面/权限/失败恢复 → 回归与归档。
- 实现约定：启用后定义固定，新调整复制为新草稿；启用需当前启用版本标识和草稿版本，避免旧窗口覆盖。无批次表、伪造引用或used标志；08绑定真实批次时继续回归冻结规则。
- 总索引跨目录写入沿用既有待同步事项，不影响本地任务。

结果：50项真实HTTP/MySQL、58项全量双尺寸Chrome、前后端构建通过；空测试库V1–V9完整迁移，演示V8→V9及重复seed/实际重启保持版本和原业务数据。独立Standards/Spec各0发现，本地实现6a14939，未推送。下一项08未启动，真实绑定冻结回归由该票承接。

详见[实施日志](../../logs/20261116-task07-implement.md)、[审查](../../logs/20261116-task07-review.md)和[测试](../../../docs/testing.md)。
