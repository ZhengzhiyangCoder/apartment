# 实际数据库 — 任务 01–02

版本迁移是唯一结构执行来源：`backend/src/main/resources/db/migration/` 中 V1 身份表、V2 账号审计表与角色状态索引。当前不提前建立后续业务模块空表。原始 18 表设计为后续任务的建模输入，不是已经运行的数据库。

## sys_user

| 字段 | 含义与约束 |
|---|---|
| id | 自增主键 |
| username | 64 字符内，唯一，区分大小写 |
| password_hash | BCrypt 哈希，不通过 API 返回 |
| display_name | 展示姓名；模拟数据明确标注 |
| role | 四角色之一，数据库 CHECK 约束 |
| enabled | 是否允许账号使用 |
| auth_version | 密码、角色或启停变化时递增，同时作为编辑版本与旧会话失效依据 |
| created_at / updated_at | 创建与变更时间 |

Flyway 另外维护自身迁移历史表。账号与学生资料的分工保持既定工程规则，当前不创建学生学籍与住宿事实。

## 隔离与保留

- Compose 仅启动 `apartment-local` 专用 MySQL，数据卷为 `apartment-local_mysql-data`。
- `apartment_demo` 为本地演示；`apartment_test` 为可清理的自动化测试库。测试在清理数据前核对库名及实际 MySQL 产品。
- `.env` 保存随机生成的项目数据库凭据，不提交或在日志打印；应用使用非 root 的 apartment_app。
- 模拟账号数据显式加载，不放进结构迁移；重复加载不能重置已改密码。停止容器不删除卷。


## operation_log

| 字段 | 含义与约束 |
|---|---|
| id | 自增主键 |
| actor_id | 操作者 sys_user 外键，不允许级联删除身份 |
| action | ACCOUNT_CREATED / ACCOUNT_ROLE_CHANGED / ACCOUNT_PASSWORD_RESET / ACCOUNT_DISABLED / ACCOUNT_ENABLED |
| target_type / target_id | 当前为 ACCOUNT 与被管理账号 ID；支持后续业务接入同一审计能力 |
| before_data / after_data | 白名单公开账号快照 JSON；创建的 before_data 为 null；不存密码或哈希 |
| created_at | 数据库生成的实际操作时间，微秒精度 |

目标与时间有查询索引，为任务 15 的查询做数据准备，本轮不提供审计查询页面。账号的姓名与角色可通过历史快照追溯。

## 写入一致性

账号管理应用服务开启事务，AuditWriter 必须加入已有事务；审计写入失败时账号操作回滚。修改前按固定顺序锁定启用的系统管理员，再校验操作者最新状态、锁定目标账号并检查 version；并发降级/禁用不能移除最后一位管理员。普通查询不执行锁定或写入。

锁定读取与事务回滚机制分别核对了 [MySQL 8.4 官方说明](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html) 和 [Spring 事务回滚说明](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)。是否满足本项目行为以实际接口、并发和故障注入测试为准。

测试重置先清除 apartment_test 的 operation_log，再清除该库账号；正常演示 seed 不清空日志或重置既有账号。
