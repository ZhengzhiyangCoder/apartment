# 任务15实施

日期：2026-11-16。状态：进行中。用户明确要求继续任务15。

- 票据：[任务15](../../../.scratch/dorm-management/issues/15-audit-query.md)，起点 main 6c0f0c3169bbda433d2d72bf5608d8784d326736，工作区干净，无 CodeGraph。
- 已读取总索引与项目现有记录。附件只作证据，最新票据与真实代码优先。
- 复用 tdd、springboot-patterns、playwright、code-review；沿用公开HTTP、真实隔离MySQL和Chrome边界，后端/浏览器顺序运行。
- 先实现审计授权与最小化投影，再筛选分页/来源定位及跨模块集中回归；实现后固定起点双轴审查。
- 不新增依赖，保留演示业务；总索引跨目录同步仍待确认，不阻塞。

见[实施日志](../../logs/20261116-task15-implement.md)。
