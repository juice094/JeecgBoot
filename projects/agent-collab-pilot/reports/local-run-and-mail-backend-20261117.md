# 本地启动脚本 + 邮件后端骨架 交付报告（额外工作线）

> 日期：2026-11-17
> 执行：AI 编码助手（网关工具）
> 定位：额外工作线——原型与骨架，不可靠交付，不计入 docs/23 主合同施工与联调基准。
> 合同依据：docs/OA 邮件体系需求文档.md、docs/OA 邮件体系接口文档.md（v1.1.0）

## 1. 本次做了三件事

1. 本地快速启动脚本（后端 8081 + 前端 3200 + 冒烟自检）。
2. 尝试归档 jeecgboot-vue3 到 .archive/（失败，见 §3），并给出可复用代码评估。
3. 邮件体系后端骨架 jeecg-module-mail（可编译，不注册进运行时）。

## 2. 启动脚本

| 文件 | 说明 |
|---|---|
| scripts/start-team-backend.sh | 强制 JDK17 提示 + --server.port=8081 + 首次 -am |
| scripts/start-team-web.sh | 固定 127.0.0.1:3200，缺依赖先 install |
| scripts/smoke-team-stack.sh | doc.html / 4 个 team 接口 / 前端端口冒烟 |
| scripts/README.md | 用法与故障排查 |

关键口径：application-dev.yml 是 8080，vite 代理是 8081，脚本用命令行参数覆盖为 8081，不改配置文件。

## 3. jeecgboot-vue3 归档：失败与说明

操作：mv jeecgboot-vue3 .archive/jeecgboot-vue3

结果：mv: cannot move ... Device or resource busy

原因判断：目录内有进程占用（VSCode 打开、node/vite/electron 进程、文件监视句柄）。

当前状态：目录完整未动，git status 仍显示其 1997 个跟踪文件。.archive/ 已加入 .gitignore，但尚未完成移动。

重试步骤：

1. 关掉 VSCode 中打开的 jeecgboot-vue3 文件、停止其 dev server。
2. 确认无 node 占用（Windows）：tasklist | grep -i node。
3. 再执行：mkdir -p .archive && mv jeecgboot-vue3 .archive/jeecgboot-vue3。

注意：移动后 git status 会显示大量删除；若用 git mv 则保留历史。是否保留历史由你定：保留历史用 git mv，彻底剥离用 mv 加提交删除。

### 3.1 可复用代码评估（诚实结论：以参考为主，直接迁移成本高）

| jeecgboot-vue3 能力 | 可否迁入 team-web | 判断 |
|---|---|---|
| utils/http/axios（defHttp、MD5 签名、token 注入） | 不建议 | team-web 已有更轻的 utils/http.ts；defHttp 依赖 vben 请求体系与 /#/ 别名 |
| BasicTable / useTable / useModal | 不建议 | 依赖 vben 与 ant-design-vue，与 team-web 极简栈冲突 |
| JUpload / JUploadButton | 立项后评估 | 邮件附件需要，但依赖 ant-design-vue 与 Form 容器；短期用原生 input file 加后端 /sys/common/upload |
| JDictSelectTag / JSearchSelect | 参考 | 收件人选择走 admin 组人员接口即可，不必迁组件 |
| tinymce 富文本（@tinymce/tinymce-vue） | 立项后评估 | 邮件正文 HTML 编辑需要；注意体积与 license，短期用 textarea |
| @vueuse/core / dayjs | 可按需 | 轻量工具库，按需引入不引入体系 |
| 路由/权限（后端驱动动态路由） | 不建议 | team-web 是静态路由加简单 auth store，架构不同 |
| sys/user、sys/upload API 实现 | 参考 | 接口路径与参数口径可参考，代码需按 team-web 的 http 改写 |

结论：jeecgboot-vue3 对 team-web 的价值主要是接口口径参考（登录、上传、人员查询），不是可直接搬的组件库。直接迁 JUpload / tinymce 会引入 ant-design-vue 整套依赖，与当前轻前端定位冲突。建议归档后按需查阅，不做批量迁移。

## 4. 邮件后端骨架 jeecg-module-mail

### 4.1 产出清单

| 层 | 文件 |
|---|---|
| 模块 | jeecg-boot/jeecg-boot-module/jeecg-module-mail/pom.xml（只依赖 base-core） |
| 枚举 | MailSendStatus（0-4 状态机）、MailCanonicalFolder、MailSendMode |
| 实体 | SystemMailAccount、SystemMailTemplate、SystemMailLog、OaMailAccount、OaMailMessageIndex |
| Mapper | 5 个 BaseMapper（org.jeecg.**.mapper* 自动扫描） |
| 契约层 | MailSendApi 加 MailSendSingleReqDTO（业务模块只依赖此层） |
| Service | ISystemMailService/Impl、IOaMailService/Impl |
| Controller | SystemMailController（/team/mail/system/send）、OaMailController（/team/mail/accounts、/team/mail/messages） |
| DDL | jeecg-module-mail/src/main/resources/db/skeleton/oa_mail_skeleton.sql（不在 Flyway 扫描目录） |

### 4.2 已实现 vs 未实现（如实标注）

| 能力 | 状态 |
|---|---|
| 入参必填校验 | 已实现 |
| 幂等键查重（只查不写） | 已实现 |
| 索引列表分页（只查索引表） | 已实现 |
| 绑定必填校验 | 已实现 |
| 模板服务 | 未接入，发送在此明确失败，不产生已发送假象 |
| 落日志 / 异步投递 | 未实现（persistPendingLog 留结构未被调用） |
| SMTP/IMAP 双测连、同步、详情 FETCH | 未实现 |
| 失败重试 / 死信 / traceId 透传 | 未实现 |

### 4.3 隔离设计

- 不注册进 jeecg-system-start：模块可独立编译，但运行时不存在 /team/mail/** 端点。
- DDL 不进 Flyway 扫描目录：放模块 resources/db/skeleton/，避免启动即建表。
- Flyway 号段：_4 已占用，_5 在 hold/ 隔离件中未入库；骨架不取号，立项时再取，避免与 hold 件冲突。
- 复用而非重造：jeecg-boot-base-core 已含 spring-boot-starter-mail，模块不重复声明；现有 EmailSendMsgHandle 可作为立项后投递实现参考，但其线程池用 SynchronousQueue 加无界 max，不建议照搬，需改有界队列。

### 4.4 验证

- 已做：mvn -DskipTests compile -pl jeecg-boot-module/jeecg-module-mail -am 通过（MVN_EXIT=0）。
- 已做：移动 DDL 后重新确认 Flyway 目录无 _6 脚本。
- 未做：未跑运行时（模块未注册，端点不生效）；无集成/端到端验证。
- 未做：未写单元测试。

## 5. 未完成与残余风险

1. 归档未完成：jeecgboot-vue3 仍占工作区，需关闭占用进程后重试。
2. 骨架与合同漂移：接口文档 v1.1.0 为草案，立项后路径/字段可能调整。
3. 误用风险：他人可能以为 /team/mail/** 已可用；已用不注册加 DDL 不在 Flyway 双隔离，并在类注释标注。
4. 持久化边界：若未来把 mail 模块加入 spring-start，@Service/@RestController 会被 org.jeecg 组件扫描拾取，端点会立即暴露——加入依赖前必须先补 F-09 权限。
5. 无测试：骨架无单测，状态机枚举可后续补纯单元测试（不依赖 Spring）。

## 6. 下一步建议

1. 关闭占用进程，完成 jeecgboot-vue3 归档（git mv 保留历史）。
2. 先按 scripts/README.md 跑通 8081 加 3200 加冒烟，确认现有 team 业务可用。
3. 邮件立项拍板后：补模板 Service，接 MailSendApi，有界线程池投递，重试/死信，traceId，F-09 权限，最后才加入 spring-start 依赖。
4. 骨架的单测优先补 MailSendStatus.canTransitTo 与 MailCanonicalFolder.fromCode，纯枚举逻辑、零依赖。
