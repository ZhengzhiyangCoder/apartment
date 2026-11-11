# 接口说明 — 任务 01

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

此文档随任务 01 实现更新，实际测试证据见 [testing.md](testing.md)。账号管理和其他业务接口由后续票增加。
