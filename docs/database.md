# 实际数据库 — 任务 01–03

版本迁移是唯一结构执行来源：`backend/src/main/resources/db/migration/` 中 V1 身份表、V2 账号审计表与角色状态索引、V3 楼栋、V4 寝室床位、V5 学生资料。当前不提前建立后续业务模块空表。原始 18 表设计为后续任务的建模输入，不是已经运行的数据库。

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

Flyway 另外维护自身迁移历史表。账号与学生资料的分工保持既定工程规则，学生学籍归 student_profile，入住与历史归任务 04；当前未创建入住记录。

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
| target_type / target_id | ACCOUNT / BUILDING / ROOM / BED / STUDENT 与对应目标 ID |
| before_data / after_data | 白名单账号或住宿资料快照 JSON；创建的 before_data 为 null；不存密码或哈希 |
| created_at | 数据库生成的实际操作时间，微秒精度 |

目标与时间有查询索引，为任务 15 的查询做数据准备，本轮不提供审计查询页面。账号的姓名与角色可通过历史快照追溯。

## 写入一致性

账号管理应用服务开启事务，AuditWriter 必须加入已有事务；审计写入失败时账号操作回滚。修改前按固定顺序锁定启用的系统管理员，再校验操作者最新状态、锁定目标账号并检查 version；并发降级/禁用不能移除最后一位管理员。普通查询不执行锁定或写入。

锁定读取与事务回滚机制分别核对了 [MySQL 8.4 官方说明](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html) 和 [Spring 事务回滚说明](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)。是否满足本项目行为以实际接口、并发和故障注入测试为准。

测试重置仅清理 apartment_test：审计、床位、寝室、楼栋、学生资料、账号按引用顺序删除；正常演示 seed 只补充模拟数据，不清空日志或重置既有资料。

## 住宿基础表

| 迁移与表 | 业务字段 | 约束 |
|---|---|---|
| V3 dorm_building | name | 名称唯一 |
| V4 dorm_room | building_id / room_no / floor | 楼栋外键，(building_id, room_no) 唯一，floor 1–100 CHECK |
| V4 dorm_bed | room_id / bed_no | 寝室外键，(room_id, bed_no) 唯一 |
| V5 student_profile | user_id / student_no / full_name / college / major / class_name | 账号外键，user_id 与 student_no 分别唯一 |

四表都有 id、enabled、version、created_at、updated_at；字符集/排序规则沿用 utf8mb4/utf8mb4_0900_as_cs。外键禁止删除仍被引用的记录，无级联删除；应用不提供硬删除。床位数量由真实床位行表达，不维护另一份可失同步容量。楼栋层数不预先限制房间，用实际楼层字段表达。

全部资料写入通过 identity AccountAccess 按已有次序锁定启用系统管理员、重新校验操作者，再锁定目标资料/上级。同账号管理共用锁顺序，使角色变化与学生绑定不会并发产生不一致。新建学生资料使用受控账号校验；账号改角色通过 housing StudentProfileReferences 验证绑定，服务依赖无循环。普通列表使用只读查询，不写审计或隐藏初始化数据。列表账号信息为一次批量查询，不逐学生请求或复制密码/角色。

这几项管理操作选择串行写锁，适合当前本地基础维护；任务 17 才进行已约定规模的性能实测，不以本轮通过功能测试推断性能达标。04 的入住分配必须核对整条楼栋—寝室—床位启用链及学生/账号状态，再加入当前入住唯一约束与停用保护。

迁移遵循新增版本，V1/V2 不改。V3–V5 为新增表，不混入演示数据；只有显式 seed 加载模拟资料。保留数据优先，当前没有自动 DOWN：如需撤销已应用结构，停写后备份数据，使用后续前向修正迁移，不自动删除表或重建演示库。
