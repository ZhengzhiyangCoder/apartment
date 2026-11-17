# GitHub 项目同步

日期：2026-11-17（Asia/Shanghai）。用户明确要求将项目全部推送至现有 GitHub 仓库。

目标：`https://github.com/ZhengzhiyangCoder/apartment`，分支 `main`。同步前本地 HEAD 为 `7bf5d64`，相对已刷新的 origin/main 领先 38 个提交、落后 0 个，包含任务 01–16 的现有项目实现与记录。本次沿用 Git 和 gh 凭据，不安装工具、不更改全局配置、不强制推送。

发现唯一未提交业务文件改动为 `backend/src/main/resources/application.yml` 中增加本机数据库密码默认值。该改动保留在用户本地工作区，不加入提交或上传；远端版本继续使用 `APARTMENT_DB_PASSWORD` 环境变量。真实密码不写入本记录。`deploy/.env`、数据库、依赖、构建及运行产物继续按原有忽略规则留在本地。新增 `.idea/` 忽略规则，保留用户本机 IDEA 配置文件。

本次仅追加同步记录及忽略规则，不修改业务实现，不重复执行历史功能测试。推送后以 `git ls-remote origin refs/heads/main` 与本地 HEAD 一致核验完成情况；工作区预期仅保留上述本机密码配置改动。

既有任务17及总索引待同步事项保持不变；全局项目入口仍不在本轮可写范围，沿用 current.md 已有待同步记录，不建立新入口。
