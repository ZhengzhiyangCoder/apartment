# 实际数据库 — 任务 01–09

版本迁移是唯一结构执行来源：`backend/src/main/resources/db/migration/` 中 V1 身份表、V2 账号审计表与角色状态索引、V3 楼栋、V4 寝室床位、V5 学生资料、V6 住宿关系、V7 报修工单/图片/状态日志、V8 派单责任与处理结果、V9 评分方案与维度、V10 周批次与应检快照、V11 正式评分与明细、V12 个人评分草稿。当前不提前建立后续业务模块空表。原始 18 表设计为后续任务的建模输入，不是已经运行的数据库。

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
| target_type / target_id | ACCOUNT / BUILDING / ROOM / BED / STUDENT / RESIDENCY / REPAIR / SCHEME / BATCH / SCORE 与对应目标 ID |
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

## 评分方案与维度（任务07，V9）

| 表 | 字段与约束 |
|---|---|
| hygiene_scheme | id独立版本标识；name；threshold DECIMAL(5,2)默认60、0–100 CHECK；status DRAFT/ACTIVE/ARCHIVED CHECK；version并发计数；source_id自引用外键；created_at/updated_at/activated_at |
| hygiene_scheme_dimension | id；scheme_id外键；name；weight DECIMAL(5,2)、0–100 CHECK；sort_order为1–20 CHECK；同方案名称与顺序各有唯一约束 |

方案的active_slot为存储生成列：ACTIVE时为1，其余为NULL，UNIQUE约束使全库最多一个启用版本。列表按status/id索引；字符集沿用utf8mb4/utf8mb4_0900_as_cs。外键无级联删除，业务无硬删除入口；草稿编辑可在同一事务内替换其维度，已发布版本不能进入此路径。复制产生新方案和新维度ID，来源外键保留版本关系。

应用在MySQL存储前使用BigDecimal检查小数位及范围，启用时精确相加为100。创建、草稿编辑、复制、启用与归档复用AccountAccess管理员锁，锁后读取最新状态和version。启用还核对调用者确认的当前启用id；新旧状态和两条审计原子提交。只读事务不拿写锁或追加日志。

V9只新增两表，V1–V8不变，无演示数据或批次占位表。首次启用即冻结定义，任务08再以真实批次外键绑定版本，回归启用切换和已绑定历史不可变性。撤销不删除历史，需备份后追加前向修正迁移。测试清理先删除维度，再按id倒序删除方案（复制来源总是较早记录）；仅apartment_test允许清理，正常seed不清理方案。

## V10/V11 周检批次与正式评分

| 表 | 保存内容 | 关键约束 |
|---|---|---|
| V10 hygiene_batch | ISO年周、周一归属日、方案id/名称/阈值、OPEN/CLOSED/SUSPENDED、准备人和真实时间 | (iso_year,iso_week)及week_start各自唯一；方案/账号外键；周数与状态CHECK |
| V10 hygiene_batch_room | 应检批次、寝室、楼栋及楼栋名/房号快照 | (batch_id,room_id)主键，批次/寝室/楼栋外键，批次内位置排序索引 |
| V11 hygiene_score | 批次、寝室、精确总分、适用阈值、录分人和实际时间 | (batch_id,room_id)唯一且组合外键指向真实应检项；分数/阈值CHECK；账号外键 |
| V11 hygiene_score_dimension | 正式评分、原维度id、名称/权重/整数分值/顺序快照 | (score_id,dimension_id)主键，维度/评分外键；顺序唯一；分数/权重/顺序CHECK |

V10与V11随准备和正式录分两个功能切片依次新增，已应用迁移不回写。所有表使用utf8mb4_0900_as_cs、无级联删除；无硬删除API。V1–V9保持原字节。无自动DOWN；若撤销，先停写备份数据库及图片，使用后续前向修正，不能删除已评分历史。

准备、启用方案、入住流转、资料停用和正式录分复用同一事务业务锁。准备在锁内通过housing.InspectionHousingQuery获取当前启用且有人入住的寝室，hygiene只保存受控查询的快照；真实方案外键与已发布定义冻结共同保留标准。评分从绑定方案读取维度，在服务端完整校验与精确计算后写主记录、每项快照和统一审计；数据库唯一键兜底防重复。

进度由应检与正式评分实时聚合，不保存可漂移的已检计数或把缺检补零。零应检completionRate=null。batch.week_start负责周/月归属，score.submitted_at保存实际提交时间；Clock为服务端UTC时钟，计算ISO年周时显式转Asia/Shanghai，测试用受控Clock验证边界，生产无改时接口。

InspectionReferences只依赖本模块Mapper，由housing既有停用边界调用，避免反向应用服务循环。未评分且OPEN的应检项阻止楼栋/寝室停用；过去周未处理事项仍保护，10的显式关闭/停检/名单治理负责后续处理。床位及学生不属于应检对象。评分历史及已绑定方案不硬删除。

审计新增BATCH_PREPARED（批次、标准与名单快照）和SCORE_SUBMITTED（评分主记录与全部明细），target_type为BATCH/SCORE。只读列表不写业务或审计。测试清理在方案/住房之前依次删除评分明细、评分、应检项、批次；只清理固定apartment_test。正常演示seed不会重置批次、名单、分数或历史。


## V12 hygiene_score_draft

- 主键为(actor_id,batch_id,room_id)，本人每批次每寝室一份；三个独立外键保留账号、批次与寝室。没有依赖应检项的级联删除，给10移除未评分名单后保留草稿提供基础。
- building_name/room_no取应检快照；dimensions为经服务端限制维度标识、项数和字符串长度的JSON，允许部分输入，不是正式评分明细。
- version为正整数，首次1、成功更新递增；updated_at为服务端Clock时间。所有权、版本、状态及名单检查与更新处于同一事务和既有业务写锁，避免保存/发布/身份变化之间的竞争。
- (actor_id,updated_at,batch_id,room_id)索引服务本人列表。正式统计仍只读取hygiene_score，草稿不计进度/成绩。
- 正式评分成功与本人草稿删除同事务；其他录入人草稿保留供冲突核对。审计只保存寝室和版本元数据，不暴露个人草稿内容。

V12增量新增一表，不回写V1–V11，不混入模拟数据。无自动DOWN；如需撤销先停止写入、备份保留草稿，再采用前向修正迁移。正常seed不清理草稿；隔离测试重置先删草稿再删其父记录，只作用于固定apartment_test。
