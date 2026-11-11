# Git 与 GitHub 私有仓库建立记录

日期：2026-11-11（Asia/Shanghai）。状态：已完成。

用户明确要求初始化当前项目 Git 并创建 GitHub 私有仓库，包含初始提交及推送。复用现有 Git 2.50.1 与 gh 2.96.0，不安装额外工具，不修改全局 Git 配置。

已读取总索引入口、路由及执行细则，项目当前记录与真实文件。初始状态没有 Git 仓库；GitHub 连接工具不提供创建仓库接口，因此使用本机 gh。沙箱内无法取得有效认证状态，获准访问系统凭据和网络后确认 keyring 登录有效，无需重新登录。

目标为当前认证用户 ZhengzhiyangCoder 的 apartment 私有仓库，已核验不存在同名仓库。现有全局 Git 作者名与邮箱未配置，采用该 GitHub 用户名和账号 ID 对应的 noreply 邮箱，仅写入本仓库配置。

已扩展 .gitignore：各层级 .env 和 .env.* 默认忽略，保留 .env.example；延用既有依赖、构建、截图与运行产物排除规则。

初始暂存 124 个文件；检查暂存区内容未发现常见私钥/令牌格式、带凭据 URL 或当前 deploy/.env 中的两项真实密码；依赖、构建和运行输出未进入暂存，示例配置正常纳入。Maven Wrapper 与 5 个项目脚本的可执行位均保留。

全量初始 diff 的空白检查提示官方 mvnw.cmd 的 CRLF、原始 Markdown 附件的行尾双空格及一份历史草稿末尾空行。这些均为已有资料或平台脚本格式，本轮保留原件，不为初始化 Git 改写业务证据或上游脚本。

## 完成结果

- 本地已初始化 main；初始提交 `0e6cb852f794e9d0ed9d92e42e7a6618d6d3e58a`，包含任务 01 工程、规格、18 项正式票及项目记录。
- GitHub 仓库：[ZhengzhiyangCoder/apartment](https://github.com/ZhengzhiyangCoder/apartment)；在首次推送前通过 GitHub 查询确认 `isPrivate=true`。
- origin 为 `https://github.com/ZhengzhiyangCoder/apartment.git`；main 已跟踪 origin/main，GitHub 默认分支为 main。
- 推送后本地 HEAD 与远端 refs/heads/main 都为上述 SHA，工作区干净。完成记录与导航更新另行文档提交并同步。
- 使用 GitHub noreply 身份，仅配置本仓库；推送按次复用 gh 凭据，不记录令牌或修改全局 credential helper。
- 未修改业务源码，无需重跑任务 01 功能测试；原有 Spec、工程结构、原型和附件保持原字节。
- CodeGraph 未初始化。下一开发任务仍为 02，未在本轮启动。

已更新项目上下文、导航、资料索引与完成归档。跨项目 `projects/README.md` 和拟建 `projects/apartment.md` 仍不在本轮可写范围，继续由 current.md 的既有待同步项承接；未建立第二个入口，也未注册新全局复用资产。
