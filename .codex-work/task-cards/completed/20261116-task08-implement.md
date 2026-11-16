# 任务08实施

日期：2026-11-16。状态：已完成。用户在07完成后要求继续开发。

- 范围：[08当前周批次与正式评分](../../../.scratch/dorm-management/issues/08-weekly-first-score.md)，前置04/07完成。基点main `b51bdd2`，工作区干净，无CodeGraph。
- 复用tdd、springboot-patterns、database-migrations、playwright及既有会话、统一事务业务锁、审计、样式和测试运行器。沿用已批准公开HTTP/真实MySQL/实际浏览器验证边界；API与浏览器顺序运行。
- 切片：只读当前周和显式准备/固定名单与方案 → 精确正式录分与进度 → 时间/并发/回滚/停用保护 → 页面 → 全量验证、演示持久化、独立审查和归档。
- 时间通过服务端Clock确定Asia/Shanghai ISO年周，周一归属日与实际提交时间分开保存。方案和名单真实外键绑定；旧周普通提交拒绝，显式名单/状态管理留10，连续评分和草稿留09，整改留13。
- 总索引跨目录写入仍待同步，不阻塞本地任务。

结果：61项真实HTTP/MySQL、66项全量双尺寸Chrome和前后端构建通过，空库完整V1–V11，演示V9→V11升级及重复seed/实际重启快照一致。固定b51bdd2...b7fcc8b独立Standards/Spec各0发现，未推送。08八条准则在本票分工内闭环，09未启动。详见[实施](../../logs/20261116-task08-implement.md)、[审查](../../logs/20261116-task08-review.md)、[测试](../../../docs/testing.md)。
