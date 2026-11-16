# 任务13实施

日期：2026-11-16。状态：进行中。用户在12完成后要求继续开发。

- 票据：[任务13](../../../.scratch/dorm-management/issues/13-rectification.md)，起点 main 32a47884ec9c692b4e9b5632f69692a6cf4f409f，工作区干净，无CodeGraph。
- 复用tdd、springboot-patterns、playwright；公开HTTP、真实隔离MySQL与实际Chrome为已批准测试边界，后端与浏览器顺序执行。
- 切片：低分触发及固定依据 → 跟进/核实/关闭与并发、事务、停用保护 → 管理页面 → 全量验证、独立审查、演示和归档。
- 默认实现：OPEN/CLOSED事件，独立核实记录后填写说明关闭；新增低分复用事件并要求重新核实，全部原记录保留；后续合格/批次停检不自动关闭。
- V14仅前向追加；原演示数据保留。总索引可读但跨目录写入仍待同步。

见[实施日志](../../logs/20261116-task13-implement.md)。
