# 本地运行与排查

前端：本机 Node/npm；后端：Java 17 / Maven Wrapper；Compose 仅运行项目专用 MySQL 8.4。无需 Nginx 或云服务器。

## 首次准备

```bash
./scripts/setup-local.sh
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d --wait
cd frontend
npm ci
cd ..
```

setup-local 只在 `.env` 不存在时生成随机项目数据库密码，权限 0600，不覆盖已有值。Docker 资源名带 apartment-local；演示库 apartment_demo、测试库 apartment_test，与同机其他项目隔离。

终端一：`./scripts/backend.sh`。终端二：`./scripts/frontend.sh`。首次后端启动会在目标库执行 Flyway 迁移。待启动完成，执行 `./scripts/seed-demo.sh` 显式加载模拟账号。

默认前端 5174、后端 8081、数据库 3308，全都监听 127.0.0.1。可在 `deploy/.env` 改端口后重启本项目服务，前端脚本会读取后端代理端口。前端 HTTP 请求统一使用 `/api`，不要绕过代理从另一个地址登录。

## 停止与再次运行

- 前后端终端分别 Ctrl+C。
- `docker compose --env-file deploy/.env -f deploy/compose.yaml stop` 停止本项目 MySQL。
- 再次执行 up/start 与前后端命令；数据库卷及修改后的密码保留。正常操作不使用 down -v。
- API 测试用随机 HTTP 端口；浏览器测试使用 8082、5175 并自动启停自己启动的服务，遇占用就失败，不接管未知进程。

## 验证入口

```bash
./scripts/backend.sh test
cd frontend
npm run build
npm run test:e2e
```

浏览器测试使用本机 Chrome（不另外安装浏览器）。它显式重置 apartment_test 的模拟账号，不触碰 apartment_demo；测试重置脚本仅接受固定测试库，不接受任意数据库名。测试服务以应用命令行属性固定 apartment_test，不能被 `.env` 的 APARTMENT_DB_URL 覆盖。不要同时运行 API 和浏览器测试，两者共享测试库；演示库不受测试重置影响。

## 常见问题

- Docker 未启动或 socket 权限不足：先确认本机 Docker 可用及当前权限，不改用其他项目的数据库。
- 端口占用：检查归属后修改本项目端口；不停止 CRM 或其他项目进程。
- 数据库拒绝访问：确认 deploy/.env 与首次初始化卷时一致。改 `.env` 不会自动修改已存在数据库用户密码，不通过删卷处理。
- 下载失败：Maven/npm 需要网络和本机缓存写权限；保持锁定版本并按当前代理环境重试，不关闭证书验证。
- 登录失败：先确认后端已迁移并显式加载模拟账号；已改过密码的账号需用新密码。
- 会话失效：退出或改密后需重新登录，CSRF 失效时页面刷新后重试。

本机 HTTP 场景 Cookie 为 HttpOnly / SameSite=Strict，Secure=false；仅适用于回环地址。实际 `.env`、运行时数据和构建目录不进入版本管理。Windows 等其他环境尚未验证。

## 报修图片存储

后端默认运行目录为backend，图片默认保存在项目`.runtime/uploads/demo`；可通过`APARTMENT_STORAGE_ROOT`指定绝对路径。API测试固定`.runtime/uploads/test-api`，浏览器测试通过命令行固定`.runtime/uploads/test-browser`，不会使用演示目录。文件不会进入Git或frontend/public；仅通过`/api/repairs/{id}/images/{imageId}`读取。

启动账号应有目录读写权限。不要把上传目录作为静态目录公开；迁移或备份需同时保留MySQL与演示图片。正常重启和重复seed不清空图片。若磁盘不可写，提交显示服务器错误并保留表单，恢复目录权限后重试；图片缺失返回安全503，不泄漏磁盘路径。强制中断进程可能产生无法通过API访问的无关联文件，清理前须先核对数据库关联，不能直接清空整个上传目录。
