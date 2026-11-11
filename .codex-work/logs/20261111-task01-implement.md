# 正式任务发布与任务 01 实现

日期：2026-11-11（Asia/Shanghai）。状态：任务 01 已完成。

## 授权与发布

用户认可全部拆分和依赖，明确从 01 开始 implement。18 份草稿已迁入 `.scratch/dorm-management/issues/`，原 task-drafts 空目录已移除；均为 ready-for-agent，01 的 Execution 为进行中，其余待开始。拆分任务已归档，总览、上下文、导航和 todo 已更新。

## 测试边界与方法

复用 implement、tdd、springboot-patterns、springboot-security；按 Spec 已批准的公开 HTTP API、真实隔离 MySQL、实际浏览器边界执行，不重新询问既定测试边界。逐个行为红绿验证，最后作代码审查。工程尚无 Git，使用实现前文件摘要作为基准，不擅自初始化仓库；无当前分支可提交。

## 环境与版本

Java 17.0.12、Maven 3.6.3、Node 24.18.0、npm 11.16.0、Docker CLI 29.6.2 / Compose v5.3.1 已复核。3307/8080/5173 属于 CRM，未操作其资源。本项目使用隔离 Compose 名称 apartment-local、数据卷、3308/8081/5174；测试库 apartment_test 与演示库 apartment_demo 分离。

Docker socket 和网络代理受沙箱限制，已通过自动审批的提权只读查询确认引擎与 MySQL 8.4 镜像可用；不是自动审批拒绝。镜像锁定已存在的官方 RepoDigest，未使用 CRM 数据。

已核验官方 [Spring Boot 3.5 Java/Maven 要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[MyBatis-Plus Boot3 安装入口](https://baomidou.com/en/getting-started/install/)、[Vite Node 要求](https://vite.dev/guide/)，并通过官方 Maven/npm 元数据确认发布版本。采用 Boot 3.5.16、MyBatis-Plus 3.5.18；前端版本随 package-lock 固定。

## 当前验证

以下记录首次进入实现时的边界；最终完成结果见下节。

## 完成结果与验证证据

已交付真实身份表迁移、四角色账号认证、本人资料、改密与退出、角色入口权限、Session/CSRF、前端第一版风格的实际页面及本地脚本。新密码提交通过事务与版本检查，旧会话撤销；输入失败可恢复，后续业务不填充假数据。

实际红绿循环：登录 CSRF 入口原 401 → 通过；角色入口原 404 → 通过；改密入口原 404 → 通过；浏览器原缺登录/改密表单及根地址角色误判 → 修复后通过。6 项 API/MySQL测试和最终14项浏览器场景全过，前后端构建成功；截图补等待后另复测两项改密，功能代码未变。

实际停止/重新启动本项目 MySQL 与后端：账号身份、修改后的密码保留；重复显式加载模拟账号不重置密码。完成验证后恢复初始演示密码。未操作 CRM 容器、数据库或端口。

按 code-review 技能并行作 Standards / Spec 两轴只读审查，分别发现1项和2项，均修复并复核。固定测试数据源改用命令行参数，消除环境覆盖；真实停库复现并修正认证服务故障错误归类；补齐四角色改密验证。详见[审查记录](20261111-task01-review.md)。

[任务01](../../.scratch/dorm-management/issues/01-local-login.md) 7条验收已勾选，Execution 为已完成；Status保留项目 triage 分类ready-for-agent，不能与执行状态混淆。已归档任务卡，当前todo仅保留后续任务及总索引同步，详见[测试说明](../../docs/testing.md)。

## 运行状态与限制

演示前端5174、后端8081及MySQL3308保留运行，用户可直接预览；浏览器测试自行启动的5175/8082服务已停止。测试库与演示库独立，测试重置只指向apartment_test；API和浏览器测试应顺序运行，避免共用测试样本相互干扰。

项目无Git仓库/当前分支，未生成commit，也未自动初始化Git；Spec、工程结构与第一版HTML保留。Windows及其他浏览器/部署环境未验证；性能与后续业务仍待02–18，不宣称系统全部完成。

总索引入口可读，projects/README.md与拟建apartment.md不在当前可写范围，未更新；既有待同步项已更新为当前项目状态。复用现有技能及官方工具，本轮未注册新全局资产。

## 最终归档核验

60 份 Markdown 的 UTF-8/LF 与 308 个本地链接通过；18 项正式票编号/分类正确，01 的 7 条准则已勾选，其余保持未完成。8 张当前应用截图存在；Spec、工程结构和第一版 HTML 的摘要与实现前一致。脚本解释器/执行权限及 bash 语法检查通过，私有 `.env` 为 0600。

最终经 5174 的真实 `/api` 代理逐一登录四类演示账号成功；3308/8081/5174 正在监听，测试端口 8082/5175 未保留监听。四类账号均保留文档中的初始模拟密码供用户查看。详细机器结果在 `.runtime/task01-final-checks.json`，稳定结论已同步本日志与 docs/testing.md。
