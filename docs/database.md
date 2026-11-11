# 实际数据库 — 任务 01

版本迁移是唯一结构执行来源：`backend/src/main/resources/db/migration/V1__identity.sql`。当前只创建身份表，不提前建立后续模块空表。原始 18 表设计为后续任务的建模输入，不是已经运行的数据库。

## sys_user

| 字段 | 含义与约束 |
|---|---|
| id | 自增主键 |
| username | 64 字符内，唯一，区分大小写 |
| password_hash | BCrypt 哈希，不通过 API 返回 |
| display_name | 展示姓名；模拟数据明确标注 |
| role | 四角色之一，数据库 CHECK 约束 |
| enabled | 是否允许账号使用 |
| auth_version | 凭据变更时递增，旧会话失效依据 |
| created_at / updated_at | 创建与变更时间 |

Flyway 另外维护自身迁移历史表。账号与学生资料的分工保持既定工程规则，当前不创建学生学籍与住宿事实。

## 隔离与保留

- Compose 仅启动 `apartment-local` 专用 MySQL，数据卷为 `apartment-local_mysql-data`。
- `apartment_demo` 为本地演示；`apartment_test` 为可清理的自动化测试库。测试在清理数据前核对库名及实际 MySQL 产品。
- `.env` 保存随机生成的项目数据库凭据，不提交或在日志打印；应用使用非 root 的 apartment_app。
- 模拟账号数据显式加载，不放进结构迁移；重复加载不能重置已改密码。停止容器不删除卷。
