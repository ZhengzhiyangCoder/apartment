# 栖居 · 高校宿舍管理系统

中文电脑端高校宿舍管理系统，本科毕设项目。按[Spec V1.0](.scratch/dorm-management/spec.md)和[工程结构](docs/engineering-structure.md)实现；[18 项正式任务](.scratch/dorm-management/task-breakdown.md)已批准，任务01–10（本地运行、身份与账号管理、住宿基础资料、入住流转、报修维修闭环、评分方案与版本、本周检查与正式评分、跨楼栋连续评分与个人草稿、批次治理与历史补录）已完成并验证。学生成绩、统计与整改等后续业务尚未交付。

私有仓库：[ZhengzhiyangCoder/apartment](https://github.com/ZhengzhiyangCoder/apartment)，默认分支 `main`。真实环境配置与运行产物不纳入版本控制，首次运行请生成本机配置。

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
- [批次治理任务10](.scratch/dorm-management/issues/10-batch-governance.md)、[当前工作记录](.codex-work/README.md)。
- [第一版界面原型](prototypes/dorm-management/index.html)：视觉参考，不是实际业务应用。
