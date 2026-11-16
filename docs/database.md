# 实际数据库 — 任务 01–06

版本迁移是唯一结构执行来源：`backend/src/main/resources/db/migration/` 中 V1 身份表、V2 账号审计表与角色状态索引、V3 楼栋、V4 寝室床位、V5 学生资料、V6 住宿关系、V7 报修工单/图片/状态日志、V8 派单责任与处理结果。当前不提前建立后续业务模块空表。原始 18 表设计为后续任务的建模输入，不是已经运行的数据库。

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

Flyway 另外维护自身迁移历史表。账号与学生资料的分工保持既定工程规则，学生学籍归 student_profile，入住与历史归 V6 housing_residency。

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
| target_type / target_id | ACCOUNT / BUILDING / ROOM / BED / STUDENT / RESIDENCY / REPAIR 与对应目标 ID |
| before_data / after_data | 白名单账号或住宿资料快照 JSON；创建的 before_data 为 null；不存密码或哈希 |
| created_at | 数据库生成的实际操作时间，微秒精度 |

目标与时间有查询索引，为任务 15 的查询做数据准备，本轮不提供审计查询页面。账号的姓名与角色可通过历史快照追溯。

## 写入一致性

账号管理应用服务开启事务，AuditWriter 必须加入已有事务；审计写入失败时账号操作回滚。修改前按固定顺序锁定启用的系统管理员，再校验操作者最新状态、锁定目标账号并检查 version；并发降级/禁用不能移除最后一位管理员。普通查询不执行锁定或写入。

锁定读取与事务回滚机制分别核对了 [MySQL 8.4 官方说明](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html) 和 [Spring 事务回滚说明](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)。是否满足本项目行为以实际接口、并发和故障注入测试为准。

测试重置仅清理 apartment_test：审计、住宿关系、床位、寝室、楼栋、学生资料、账号按引用顺序删除；正常演示 seed 只补充模拟数据，不清空日志或重置既有资料。

## 住宿基础表

| 迁移与表 | 业务字段 | 约束 |
|---|---|---|
| V3 dorm_building | name | 名称唯一 |
| V4 dorm_room | building_id / room_no / floor | 楼栋外键，(building_id, room_no) 唯一，floor 1–100 CHECK |
| V4 dorm_bed | room_id / bed_no | 寝室外键，(room_id, bed_no) 唯一 |
| V5 student_profile | user_id / student_no / full_name / college / major / class_name | 账号外键，user_id 与 student_no 分别唯一 |

四表都有 id、enabled、version、created_at、updated_at；字符集/排序规则沿用 utf8mb4/utf8mb4_0900_as_cs。外键禁止删除仍被引用的记录，无级联删除；应用不提供硬删除。床位数量由真实床位行表达，不维护另一份可失同步容量。楼栋层数不预先限制房间，用实际楼层字段表达。

全部资料写入通过 identity AccountAccess 按已有次序锁定启用系统管理员、重新校验操作者，再锁定目标资料/上级。同账号管理共用锁顺序，使角色变化与学生绑定不会并发产生不一致。新建学生资料使用受控账号校验；账号改角色通过 housing StudentProfileReferences 验证绑定，服务依赖无循环。普通列表使用只读查询，不写审计或隐藏初始化数据。列表账号信息为一次批量查询，不逐学生请求或复制密码/角色。

这几项管理操作选择串行写锁，适合当前本地基础维护；任务 17 才进行已约定规模的性能实测，不以本轮通过功能测试推断性能达标。04 已加入整条启用链校验、当前入住唯一约束及关联停用保护，见下节。

迁移遵循新增版本，V1/V2 不改。V3–V5 为新增表，不混入演示数据；只有显式 seed 加载模拟资料。保留数据优先，当前没有自动 DOWN：如需撤销已应用结构，停写后备份数据，使用后续前向修正迁移，不自动删除表或重建演示库。


## V6 housing_residency

| 字段 | 用途与约束 |
|---|---|
| id / student_id / bed_id | 住宿主键与学生资料、床位外键；无级联删除 |
| building_name / room_no / bed_no | 入住时的位置快照，保留历史标识 |
| started_at / started_by | 实际入住时间、操作者账号外键 |
| ended_at / ended_by / end_reason / end_note | 结束时间、操作者、TRANSFER 或 CHECKOUT、办理说明 |
| active_student_id / active_bed_id | 虚拟生成列：未结束时取 student_id / bed_id，已结束时为 NULL；分别 UNIQUE |

结束字段有完整性 CHECK：当前住宿的全部结束字段为空；已结束时操作者、原因、说明非空，结束时间不早于入住时间。按学生和 id 建历史索引，按 ended_at/id 支持状态列表；原父级外键保持归属链。位置名称不复制成另一份可修改资料，当前视图联查最新名称、历史视图读取原快照。

MySQL 允许可空唯一索引含多个 NULL，因此任意多条历史可保留，同时一名学生/床位最多一个当前关系。已核对 [MySQL 8.4 生成列索引说明](https://dev.mysql.com/doc/refman/8.4/en/create-table-secondary-indexes.html) 与 [NULL 唯一索引说明](https://dev.mysql.com/doc/refman/8.4/en/show-columns.html)；本项目另外通过真实 MySQL 直接违反两项唯一约束，验证数据库实际拒绝。

住宿写入与账号/资料变更沿用同一管理员锁次序。停用检查通过 ResidencyReferences，只读跨模块边界无反向服务循环；学生当前范围通过 CurrentResidencyQuery 从认证身份推导。调寝结束旧关系、创建新关系并写审计均在一个 Spring 事务内；没有独立“床位已占用”布尔状态需要同步。

V6 为增量新增表，V1–V5 保持原字节；不混入演示数据。无自动 DOWN，撤销需备份并采用前向修正，不能删除既有住宿历史。最小演示继续复用已有有效学生和六张床位，由实际页面办理产生入住及审计，不在 seed 中伪造办理操作；重复 seed 也不会重新入住已退宿学生。

## V7 学生报修

- repair_order：提交人、原住宿及五类资料外键，提交时学生姓名与楼栋/寝室/床位快照，问题分类/描述/状态、时间、请求UUID和内容摘要。`(submitted_by, request_id)`唯一约束防网络重试重复生成；状态与分类CHECK，按本人/id和状态/id查询。
- repair_image：工单外键、仅内部使用的随机文件键、规范化PNG字节数；文件键唯一。最多5张由同一串行写事务校验，不提供独立无主附件上传。
- repair_status_log：工单、前后状态、操作者外键/姓名快照、时间和备注；按工单/id排序。与工单变更、统一operation_log一起提交。

V7只新增表，V1–V6保持不变。没有级联删除或删除API；历史引用始终保留。撤销使用后续前向修正迁移，不自动删除已提交工单或清空演示库。

AccountAccess.lockBusinessActor复用原管理员全局锁后核验最新身份，按角色允许学生提交及三类角色取消；住宿办理、资料启停、账号变更维持同一锁顺序。RepairReferences只依赖mapper，由已有ResidencyReferences边界调用，不与RepairService形成服务循环。05检查提交人及原住宿资料的未完工单；06已增加分配维修人员的停用与改换角色约束。

本地文件先解码并重新编码，再在业务事务内写到私有目录；事务同步回调负责失败清理。只有已提交的工单关联可通过授权接口读取，不暴露文件路径。进程被强制终止可能留下无数据库关联的孤立文件，它们无法经接口访问；不要把此目录挂到静态Web服务。正常服务重启和seed不删除任何已关联图片，数据库与文件目录应一并保留。

测试重置增加先删除repair_status_log/repair_image/repair_order，随后删除住宿与账号资料；仍仅允许apartment_test。演示、API测试和浏览器测试使用独立图片目录。WebP解码依赖锁定TwelveMonkeys imageio-webp 3.15.3，采用[官方ImageIO插件方式](https://github.com/haraldk/TwelveMonkeys)，构件见[Maven Central](https://central.sonatype.com/artifact/com.twelvemonkeys.imageio/imageio-webp/3.15.3)。


## 维修责任与结果（任务06，V8）

V8为新增字段/索引/外键的前向迁移，不改V1–V7，不删除或回填改写已有工单。repair_order新增可空assignee_id/name、assigned_by/name、assigned_at、started_at、completed_at、completion_note（最多500字），以及version BIGINT默认0。负责人和派单人关联sys_user，姓名取当时受控身份查询结果保留快照；idx_repair_assignee(assignee_id,status,id)用于本人任务检索。

repair_status_log新增可空previous_assignee_id/name、assignee_id/name，人员ID均有外键。V7已有日志的新增字段为null，之后每次工作流变化保留前后责任人；同状态重分配也新增一条日志。处理结果同时保存于工单与完成日志，已结束记录没有编辑或删除接口。

合法主链为PENDING_ASSIGNMENT→PENDING→IN_PROGRESS→COMPLETED；取消仅从前两态进入CANCELLED。共享事务锁与带version的条件更新防止旧命令覆盖责任人，账号启停/角色变化遵循相同锁序。日志或审计插入失败时，责任人、状态、时间、处理说明与版本全部回滚。

回退不通过删除新列或倒退Flyway实现；若需修正，新增前向迁移并保留既有引用。测试清理仍按已确认的固定apartment_test白名单执行，演示库原地升级。全量/升级验证结果见[测试说明](testing.md)。
