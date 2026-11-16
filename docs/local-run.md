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

任务06的V8升级保留V7工单及图片；应用重启后自动新增负责人、处理结果和版本字段。已有待分配工单可直接派单，历史取消单仍为终态。维修人员通过5174“我的维修任务”访问，图片继续由同一授权接口提供，无需新增公开目录或服务。

任务07启动时追加V9两张评分方案表，保留已有账号、住宿和报修数据。系统管理员及宿管从“评分方案”进入；首次安装无启用标准，需要管理员显式创建草稿并启用。正常seed和重启不会重置标准或历史。测试重置额外清理方案维度/方案，只作用于apartment_test。


任务08启动追加V10/V11四张批次与评分表，保留原方案与历史。两类管理角色从“本周卫生检查”显式准备；只有宿管可以录分。首次准备前先办理入住并启用评分方案，准备后名单不随入住变化。系统使用Asia/Shanghai当前ISO年周，正常重启和seed不重新准备、不覆盖名单或评分；下一周需再次显式准备。

浏览器batches.spec.ts每个场景重置固定apartment_test，验证同周唯一准备及空状态；不得与API测试并行。所有重置新增评分明细→评分→应检项→批次的引用清理顺序，演示库不受影响。


任务09启动时追加V12个人草稿表。宿管从“本周卫生检查”暂存/恢复个人草稿，列表包括历史批次；正常seed和重启保留草稿，正式提交成功仅清除本人对应草稿。“保存并下一间”按已应用楼栋/房号查询范围连续录分，无需额外后台服务。测试重置把草稿排在批次/寝室/账号之前清理。
