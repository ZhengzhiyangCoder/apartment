# 栖居 · 高校宿舍管理系统

中文电脑端高校宿舍管理系统，本科毕设项目。按[Spec V1.0](.scratch/dorm-management/spec.md)和[工程结构](docs/engineering-structure.md)实现；[18 项正式任务](.scratch/dorm-management/task-breakdown.md)已批准，任务 01（本地运行、四角色登录、个人资料与改密）已完成并验证。后续业务尚未交付。

## 本地启动

环境：Java 17、Node 22.12+、Docker Compose、Python 3。后端使用 Maven Wrapper；本项目只在本机回环地址运行。

```bash
./scripts/setup-local.sh
docker compose --env-file deploy/.env -f deploy/compose.yaml up -d --wait
cd frontend
npm ci
cd ..
./scripts/backend.sh
# 另开终端
./scripts/frontend.sh
```

前端默认 `http://127.0.0.1:5174`，后端 `127.0.0.1:8081`，MySQL `127.0.0.1:3308`。浏览器 API 请求统一由前端 `/api` 代理转发。

首次启动后显式加载模拟账号，步骤见[演示说明](docs/demo.md)。环境、停止与排查见[本地运行](docs/local-run.md)；验证结果见[测试说明](docs/testing.md)。

## 项目导航

- [接口说明](docs/api.md)、[数据库](docs/database.md)。
- [正式任务 01](.scratch/dorm-management/issues/01-local-login.md)、[当前工作记录](.codex-work/README.md)。
- [第一版界面原型](prototypes/dorm-management/index.html)：视觉参考，不是实际业务应用。
