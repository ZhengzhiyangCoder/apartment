# 接口说明 — 任务 01–07

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

账号操作与审计快照同事务。已关联学生资料的账号不能改为非学生角色，返回 409 STUDENT_PROFILE_LINKED；停用保留关联资料与身份。已有当前入住时禁止停用账号；未完报修限制已在05接入；卫生未完业务的检查随对应任务接入。审计查询页面与通用查询 API 归任务 15。

实际测试证据见 [testing.md](testing.md)。

## 住宿基础资料（任务 03）

| 集合路径 | 查询 | 创建 JSON | 编辑 JSON |
|---|---|---|---|
| /api/housing/buildings | q 匹配楼栋名；enabled/page/size | name | name/version |
| /api/housing/rooms | q 匹配房号/楼栋名；buildingId/enabled/page/size | buildingId/roomNo/floor | roomNo/floor/version |
| /api/housing/beds | q 匹配床号/房号/楼栋名；buildingId/roomId/enabled/page/size | roomId/bedNo | bedNo/version |
| /api/housing/students | q 匹配学号/姓名/学院/班级；enabled/page/size | userId/studentNo/fullName/college/major/className | studentNo/fullName/college/major/className/version |

每个集合：GET 为分页列表，POST 创建成功 201，POST `/{id}` 编辑成功 200，POST `/{id}/enabled` 接收 `{enabled,version}` 启停成功 200。所有写入仅系统管理员，必须有 CSRF；楼栋/寝室/床位 GET 还允许宿舍管理员只读，学生列表 GET 仅系统管理员。学生、维修人员无全体住宿或学籍查询权限；学生本人住宿由下节任务 04 接口提供。没有 DELETE。

分页默认 page=1/size=10，最多 100；q 最多 64 字，字面包含匹配且区分大小写，固定 id 倒序。不接受动态 SQL 排序或调用者身份。筛选 enabled 指资料自身状态；寝室/床位另返回 buildingEnabled/roomEnabled 说明父级状态。传入的查询 ID 必须为正整数；不把浮点数截断成 ID/楼层/version，角色不接受数字枚举序号。

必填字段均不可空白：楼栋名、学生姓名、学院、专业、班级最多 64 字；房号、床位号、学号最多 32 字；楼层为 1–100 整数。保存去除首尾空白。楼栋名全局唯一，同楼栋房号、同寝室床号、学号、学生账号绑定分别唯一；重复返回 409 HOUSING_DUPLICATE。父级和账号绑定创建后固定，编辑请求只消费上表列出的可写字段。学生姓名为学籍姓名，账号 displayName 仍是身份模块展示名，不以学籍编辑静默改写账号。

目标不存在返回 404 HOUSING_NOT_FOUND；父级不存在同样返回 404。新建/启用寝室需楼栋启用，新建/启用床位需寝室与楼栋均启用，否则 409 PARENT_DISABLED。停用父级保留子记录及其自身状态，不级联删除或自动改启停。当前入住的楼栋、寝室、床位以及学生资料均禁止停用；05已增加未完工单限制，08/13引入卫生业务时继续扩展检查。

学生资料只能关联一个已启用 STUDENT 账号，重复绑定拒绝；账号不存在返回 404 ACCOUNT_NOT_FOUND，角色/状态不符返回 409 STUDENT_ACCOUNT_REQUIRED。资料停用不等于禁用登录账号；重新启用资料需账号为已启用学生。列表通过 identity 受控批量查询补充 username/accountEnabled，不保存第二套角色或密码。不提供账号与学籍联合创建；先创建账号，再建立资料，两步有独立明确结果。

编辑与启停使用列表返回的 version；过期返回 409 HOUSING_CHANGED，刷新再重试。状态未变不追加操作；其他成功变更递增版本。所有资料变更与审计同一事务，审计失败不会留下部分资料。审计 action 前缀为 BUILDING/ROOM/BED/STUDENT，后缀 CREATED/UPDATED/ENABLED/DISABLED，操作者由当前 Session 确定。


## 入住流转（任务 04）

| 方法与路径 | 作用 | 权限、输入及结果 |
|---|---|---|
| GET /api/housing/residencies | 当前及历史住宿分页 | 仅系统管理员；q/studentId/current/page/size；items/total/page/size |
| GET /api/housing/vacant-beds | 有效空床位分页 | 仅系统管理员；q/page/size；只返回楼栋、寝室、床位均启用且没有当前入住的床位 |
| POST /api/housing/residencies | 入住 | 仅系统管理员；studentId/bedId，正整数；成功 201 |
| POST /api/housing/residencies/{id}/transfer | 原子调寝 | 仅系统管理员；bedId/note；成功 200，返回新住宿 |
| POST /api/housing/residencies/{id}/checkout | 退宿 | 仅系统管理员；note；成功 200，返回已结束住宿 |
| GET /api/housing/my-residency | 本人当前住宿 | 仅学生；无身份参数；返回 `{current: null}` 或本人住宿对象 |

所有写入需 CSRF。q 最长 64 字，按学号、姓名、楼栋或房号文字包含查询；空床位按楼栋、房号或床号查询。current 省略为全部、true 为当前、false 为历史。studentId 是管理员列表的筛选，必须为正整数。page 从 1 开始，size 默认 10、上限 100，固定 id 倒序。

住宿对象包含 id/studentId/studentNo/fullName、buildingId/buildingName、roomId/roomNo、bedId/bedNo、startedAt/endedAt/endReason/endNote。结束原因为 TRANSFER 或 CHECKOUT，当前住宿的结束字段为 null。时间由服务端数据库生成，响应为带 UTC 标识的 ISO 时间，页面按本机时区显示。当前位置名称取最新资料；历史保留办理入住时的位置名称快照，不随后来资料改名变化。

入住同时校验学生资料、学生账号及完整位置链均启用；一名学生与一个床位各只能有一条当前关系。重复分配、相同床位调寝、目标不可用、旧住宿已结束等返回 409 RESIDENCY_CONFLICT；记录不存在返回 404 HOUSING_NOT_FOUND。note 必填、非空白、最多 200 字，保存去除首尾空白。目标床位列表只是查询结果，提交时再次校验。

调寝使用列表中的原住宿 id；先结束该关系，再创建新关系，连同审计整体提交。旧 id 重放被拒绝，不能误操作新住宿；退宿同样保留原记录。任一步失败回滚全部写入。没有修改既有历史或硬删除的接口。

有当前入住时，账号/学生资料/楼栋/寝室/床位停用均返回 409 CURRENT_RESIDENCY_EXISTS，提示先办理调寝或退宿；拒绝后版本与审计不变。历史引用不阻止停用，也不会被删除。05已增加未完工单限制；卫生业务规则由08/13承接。

学生查询始终从 Session 取账号；传入 userId/studentId/roomId 不改变范围。housing 的 `CurrentResidencyQuery.findForStudent(AccountPrincipal)` 提供当前关系或 null；`requireForStudent` 在未入住时抛出 NOT_RESIDENT。后续报修和成绩服务应使用该受控边界推导位置；报修写入还需在自己的事务中处理与调寝并发的取值时点。任务 04 没有开放成绩或报修接口，不能视作相关跨业务权限已验收。

审计动作：RESIDENCY_CHECKED_IN、RESIDENCY_TRANSFERRED、RESIDENCY_CHECKED_OUT；target_type 为 RESIDENCY。调寝日志 target_id 指原关系，before 为原住宿，after 包含 previous（已结束）与 current（新住宿）；操作者和时间由服务端生成。

## 学生图文报修（任务 05）

| 方法与路径 | 权限与输入 | 结果 |
|---|---|---|
| POST /api/repairs | 仅已入住学生；multipart/form-data：category、description、requestId、可选重复 images 字段；需 CSRF | 201，工单详情 |
| GET /api/repairs | 学生仅本人；维修人员仅当前负责；宿管与系统管理员全部；q/status/page/size | items/total/page/size |
| GET /api/repairs/{id} | 同工单查询范围 | 工单、images、logs |
| GET /api/repairs/{id}/images/{imageId} | 每次请求重新核验账号及工单范围 | image/png，Cache-Control: no-store |
| POST /api/repairs/{id}/cancel | 学生本人、宿管、系统管理员；JSON reason 非空白、最多500字；需 CSRF | 200，取消后的详情 |

新工单 PENDING_ASSIGNMENT（待分配）。任务06已接入完整状态流程，见下节；05当时仅提供待分配取消。

问题类型为 PLUMBING 给排水、ELECTRICAL 电器照明、DOORS 门窗、FURNITURE 家具设施、OTHER 其他问题（当前实现的表单分类）。描述必填1–2000字；requestId为UUID，同一账号相同标识且相同内容重试返回原工单，不重复创建图片/日志。修改内容必须换新标识，复用旧标识返回409 REQUEST_REUSED。页面保留失败输入和重试标识，内容修改时生成新标识。

提交人、学生、住宿、楼栋/寝室/床位及名称全由服务端认证身份和当前住宿确定，客户端传入相关标识不参与写入。与调寝/退宿/停用共用事务锁，在获取锁之后读取当前住宿；工单保留该时点的位置快照。未入住返回409 NOT_RESIDENT；本人旧单及图片在调寝、退宿后继续可读。

图片每单0–5张，每张1字节至5×1024×1024字节。扩展名、MIME及实际内容须匹配JPG/JPEG、PNG、WebP；完整解码校验，最多1600万像素以限制解码资源，统一重新编码为PNG并去除原始元数据。常规无效文件400 INVALID_IMAGE；超出传输上限413 IMAGE_TOO_LARGE。HTTP总上传限制26MB，单文件传输上限6MB，业务上限仍为5MB。响应不包含磁盘路径、存储键或原文件名。

q最长64字，文字包含查询描述、楼栋名、房号或提交人姓名；status只接受列出的枚举；page从1开始，size默认10、最大100，按id倒序。客户端提交人筛选不能扩大学生范围。无权工单或不存在的图片均404 REPAIR_NOT_FOUND；未登录401、无角色权限403。不存在公开静态图片目录或硬删除接口。

取消需原因；重复取消和过时状态返回409 REPAIR_CHANGED，失败不追加状态日志。工单写入、图片关联、状态日志与 REPAIR_SUBMITTED / REPAIR_CANCELLED 审计同事务。磁盘写入失败会回滚；事务失败删除本次新文件。关联账号、学生资料和楼栋/寝室/床位有未完工单时，停用返回409 UNFINISHED_REPAIRS；当前入住限制仍先检查，终态历史保留且不阻止停用。

## 派单与维修闭环（任务06）

| 方法与路径 | 权限与JSON输入 | 结果 |
|---|---|---|
| GET /api/repairs/maintainers | 系统管理员、宿舍管理员；q/page/size | 有效且启用的维修人员分页，items仅含id/username/displayName |
| POST /api/repairs/{id}/assign | 两类管理角色；assigneeId/version | 待分配→待处理 |
| POST /api/repairs/{id}/reassign | 两类管理角色；assigneeId/version/reason | 仅待处理；换另一位维修人员，状态不变 |
| POST /api/repairs/{id}/start | 当前负责人且为维修角色；version | 待处理→处理中 |
| POST /api/repairs/{id}/complete | 当前负责人且为维修角色；version/completionNote | 处理中→已完成 |
| POST /api/repairs/{id}/cancel | 本人学生、两类管理角色；reason | 待分配或待处理→已取消 |

所有写入需CSRF，成功200返回详情。assigneeId必须是正整数；新增四个工作流命令的version必填且为非负整数，来自最新详情。旧版本或非法状态409 REPAIR_CHANGED，不能跳过处理中、重开终态或改写完成结果。重分配原因和处理说明均必填、不可空白、最多500字，保存去除首尾空白。重分配相同负责人409 SAME_ASSIGNEE；无效、非维修或停用账号409 MAINTAINER_REQUIRED，人员选项不是写入授权。

列表/详情新增assigneeId/assigneeName、assignedBy/assignedByName/assignedAt（最近一次派单）、startedAt/completedAt/completionNote、version。未发生的字段为null，旧单version初始0；每次变化递增。时间与操作者由服务端生成。状态日志新增previousAssigneeId/previousAssigneeName/assigneeId/assigneeName，保存责任人变化的姓名快照；重分配为PENDING→PENDING，不覆盖旧日志。

维修人员范围从Session身份确定：只能读取当前分配给自己的工单、历史状态和图片，传入assigneeId/owner等参数不能扩大范围。重分配后旧负责人后续列表不含该单，详情、图片及开始/完成请求均404；最终负责人仍可查看已完成/已取消记录。其他角色的本人/全校范围不变。401会话失效、403角色/CSRF拒绝、404工单不存在或无权限，均不泄露存储路径。

派单、重分配、开始、完成、取消和账号停用/角色变更共用事务写锁，在锁内重新校验有效身份、最新负责人及状态。操作、责任人/时间/结果、状态日志及审计整体提交；失败不留下部分变化。审计新增REPAIR_ASSIGNED、REPAIR_REASSIGNED、REPAIR_STARTED、REPAIR_COMPLETED，target_type=REPAIR，保存前后快照。当前负责人有未完工单时，停用或改换角色均409 UNFINISHED_REPAIRS；终态历史不阻止停用，记录和外键继续保留。

## 评分方案与版本（任务07）

| 方法与路径 | 输入 | 权限与结果 |
|---|---|---|
| GET /api/hygiene/schemes | q/status/page/size | 系统管理员全部；宿管仅已启用/历史；items/total/page/size/active |
| GET /api/hygiene/schemes/{id} | 正整数id | 同上；方案详情与按顺序排列的dimensions |
| POST /api/hygiene/schemes | name/threshold/dimensions | 仅系统管理员；创建草稿，201 |
| POST /api/hygiene/schemes/{id} | definition/version | 仅系统管理员；编辑草稿，200 |
| POST /api/hygiene/schemes/{id}/copy | version | 仅系统管理员；复制为新草稿，201 |
| POST /api/hygiene/schemes/{id}/activate | version/activeId | 仅系统管理员；启用，200 |

全部写入需CSRF。学生与维修人员无此管理接口权限；宿管查草稿或未知id返回404 SCHEME_NOT_FOUND。没有删除接口。学生查看成绩绑定标准由11提供。

定义结构为`{name, threshold, dimensions: [{name, weight}]}`；方案名称及维度名称必填、不可空白、最多64字，保存去除首尾空白；同一方案维度名称不可重复，1–20项，数组顺序即展示顺序。weight必填，threshold省略/null默认60；均为0–100十进制数、最多两位小数，不接受舍入后再保存。草稿总权重可暂不等于100，启用时精确合计必须等于100，否则400 WEIGHT_TOTAL。精度/范围错误400 INVALID_WEIGHT或INVALID_THRESHOLD，维度重名400 DUPLICATE_DIMENSION。

方案返回id/name/threshold/status/version/sourceId/createdAt/updatedAt/activatedAt/totalWeight/dimensionCount；详情另含dimensions，其元素为id/name/weight/sortOrder。id标识独立方案版本，version是并发编辑计数；sourceId记录复制来源，独立创建时为空。时间由数据库生成并以UTC ISO格式返回。状态为DRAFT（草稿）、ACTIVE（已启用）、ARCHIVED（历史）。新草稿不自动启用。首次启用后定义固定，旧启用版本自动归档；修改或重新采用历史标准时复制新草稿，原定义和首次启用时间保留。

写入version为非负整数，必须来自最新对象；过期409 SCHEME_CHANGED。编辑/启用已发布版本返回409 SCHEME_FROZEN。启用额外提交当前`active.id`，暂无启用时activeId=0；比较不符返回409 ACTIVE_SCHEME_CHANGED，需重新读取并确认切换。成功切换同时归档旧版本、启用新版本与写审计；任何一步失败全部回滚，并发旧窗口不能覆盖先成功的选择。

q为最多64字的名称字面包含匹配，区分大小写；status为上述枚举，宿管筛DRAFT只得到空列表。page从1开始，size默认10、上限100，固定id倒序。active独立于列表筛选，返回当前启用摘要或null；只读查询不创建或启用方案。审计动作SCHEME_CREATED/UPDATED/COPIED/ARCHIVED/ACTIVATED，target_type=SCHEME，包含前后定义快照，操作者来自Session。

本任务冻结所有已启用/历史定义，未建立检查批次或伪造引用。08创建批次时需在同一写锁内绑定当时的ACTIVE方案及维度，保存真实外键与名单；用真实批次回归切换方案不改变已绑定标准。
