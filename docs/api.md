# 接口说明 — 任务 01–02

所有路径从前端同源入口的 `/api` 访问。Session Cookie 名称为 `APARTMENTSESSION`，不与同机其他项目的默认 Cookie 混用。JSON 查询不返回密码、密码哈希或内部会话字段。

| 方法与路径 | 作用 | 权限与输入 |
|---|---|---|
| GET /api/auth/csrf | 获取 CSRF token 与 headerName | 可匿名；安全会话初始化，不写业务资料 |
| POST /api/auth/login | 登录 | URL encoded username/password；需 CSRF |
| GET /api/auth/me | 查看本人账号资料 | 已登录；忽略客户端身份参数，不提供查询他人入口 |
| POST /api/auth/password | 修改本人密码 | JSON currentPassword/newPassword；需当前会话与 CSRF |
| POST /api/auth/logout | 退出并使会话失效 | 需 CSRF；成功 204 |
| GET /api/identity/workspaces/{role} | 角色入口的授权信息 | 仅本人角色；不返回虚构业务指标 |

角色值：SYSTEM_ADMIN、DORM_MANAGER、STUDENT、MAINTAINER。账号角色来源为服务端数据，登录请求不允许自行选择角色。

登录成功后重新取 CSRF token，再发后续写请求；token 放在响应指定的请求头（X-CSRF-TOKEN）。前端不把 Session 或 CSRF token 存到 localStorage。登录由 Spring Security 表单认证处理，返回 JSON，不重定向到默认登录页。

错误结构为 `{ "code": "...", "message": "..." }`。未登录 401、无权限/CSRF 403、输入错误 400、业务冲突 409、服务器失败 500；响应不得包含堆栈、SQL、凭据。新密码 8–64 字符且 UTF-8 不超过 BCrypt 的 72 字节上限，须区别于原密码；成功后重新登录。

## 账号管理（仅系统管理员）

| 方法与路径 | 作用 | 输入及结果 |
|---|---|---|
| GET /api/identity/accounts | 查询账号 | q、role、enabled、page、size；返回 items/total/page/size |
| POST /api/identity/accounts | 创建账号 | username、displayName、role、password；成功 201 |
| POST /api/identity/accounts/{id}/role | 变更唯一角色 | role、version；成功 200 |
| POST /api/identity/accounts/{id}/reset-password | 重置密码 | newPassword、version；成功 200 |
| POST /api/identity/accounts/{id}/enabled | 启用或禁用 | enabled、version；成功 200 |

所有账号管理写入需 CSRF。匿名查询/写入不得通过；宿舍管理员、学生和维修人员均无此管理权限。无匿名注册或硬删除接口。

账号对象仅有 `id/username/displayName/role/enabled/version`，不含凭据；version 是并发修改版本。写操作传入列表返回的 version，过期时返回 409 ACCOUNT_CHANGED，刷新列表再决定是否重试。角色/启停未发生变化的请求不追加日志、不递增版本。

查询 q 为账号或姓名的文字包含匹配（区分大小写，不把 %、_ 当通配符），最长 64 字；role 只接受四种枚举值，enabled 可省略或为 true/false。page 从 1 开始，size 默认 10，允许 1–100；固定按 id 倒序，不接受客户端 SQL 排序表达式。

登录账号为 3–64 位字母、数字、下划线、点或短横线，唯一且创建后固定。姓名必填、最多 64 字；初始/重置密码使用同一 8–64 字符和 UTF-8 72 字节上限。管理员通过受控方式告知成员新密码，列表和日志不回显密码。重复用户名返回 409 USERNAME_EXISTS；目标不存在返回 404 ACCOUNT_NOT_FOUND。

角色、密码和启停实际改变后增加版本，旧会话在下一次请求被拒绝并需重新登录；新登录按当前角色授权。禁止停用或降级最后一个启用的系统管理员，返回 409 LAST_ADMINISTRATOR 并提示先创建或启用另一管理员。并发冲突不得留下部分写入。

账号操作与审计快照同事务。当前没有住宿/报修/卫生业务表，历史审计引用不会阻止停用，但不会删除身份；后续任务在引入当前入住/未完业务时补齐关联检查。审计查询页面与通用查询 API 归任务 15。

实际测试证据见 [testing.md](testing.md)。
