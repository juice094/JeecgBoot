# jeecg-module-mail 骨架报告（未立项）

> 日期：2026-11-17
> 执行：AI 编码助手（网关工具）
> 定位：额外工作线的原型与骨架，不可靠交付；不计入 docs/23 主合同施工与联调基准。
> 合同依据：projects/agent-collab-pilot/docs/OA 邮件体系需求文档.md、OA 邮件体系接口文档.md（v1.1.0）

## 1. 模块定位

OA 邮件体系的后端骨架。当前状态是“可编译、不运行”：模块能被 Maven 独立构建，但没有注册进 jeecg-system-start，所以运行时不存在 /team/mail/** 端点。

这样设计的原因：邮件体系尚未立项（docs/16 口径：后置 M2+，暂 mock，待立项）。骨架先占住分层与接口形状，方便立项后按图施工，同时不让未完成能力在运行时暴露。

## 2. 两条隔离线

1. 不注册进 jeecg-system-start：jeecg-boot-module/pom.xml 只把本模块加进 modules 参与编译，没有在 jeecg-system-start/pom.xml 里加依赖。
2. DDL 不进 Flyway 扫描目录：建表脚本在 src/main/resources/db/skeleton/oa_mail_skeleton.sql，不在 flyway/sql/mysql/ 下，后端启动不会建表。本模块也不取 Flyway 号段（_4 已被 team_audit_log 占用；_5 在 hold/ 隔离件中未入库），立项时再取号，避免冲突。

## 3. 文件清单

| 层 | 路径 |
|---|---|
| 模块 | pom.xml（只依赖 jeecg-boot-base-core） |
| 枚举 | enums/MailSendStatus.java、MailCanonicalFolder.java、MailSendMode.java |
| 实体 | entity/SystemMailAccount.java、SystemMailTemplate.java、SystemMailLog.java、OaMailAccount.java、OaMailMessageIndex.java |
| Mapper | mapper/ 下 5 个 BaseMapper（由 org.jeecg.**.mapper* 自动扫描） |
| 契约层 | api/MailSendApi.java、dto/MailSendSingleReqDTO.java |
| Service | service/ISystemMailService.java、IOaMailService.java；service/impl/ 下两个实现 |
| Controller | controller/SystemMailController.java、OaMailController.java |
| DDL | src/main/resources/db/skeleton/oa_mail_skeleton.sql |
| 本报告 | REPORT.md |

## 4. 已实现与未实现

已实现：入参必填校验；幂等键查重（只查不写）；员工邮箱索引列表分页（只查索引表）；绑定必填校验。

未实现：模板服务未接入，因此任何发送都会在 SystemMailServiceImpl 里明确抛 MAIL_TEMPLATE_NOT_FOUND，不会产生“已发送”假象；落日志、异步投递、SMTP/IMAP 双测连、文件夹映射、增量同步、详情 FETCH、失败重试与死信、traceId 透传均未实现。

## 5. 验证

已做：mvn -DskipTests compile -pl jeecg-boot-module/jeecg-module-mail -am 通过（MVN_EXIT=0）。
已做：确认 Flyway 扫描目录下没有本模块脚本。
未做：未跑运行时（模块未注册，端点不生效）；无集成、端到端验证；无单元测试。

## 6. 复用说明

jeecg-boot-base-core 已传递 spring-boot-starter-mail，本模块不重复声明依赖。现有 org.jeecg.modules.message.handle.impl.EmailSendMsgHandle 可作为立项后投递实现参考，但它用 SynchronousQueue 加无界 max 线程池，不建议照搬，应改有界队列加拒绝策略。

## 7. 立项后施工顺序（重要）

1. 补模板 Service，接 MailSendApi，做模板参数完整性校验。
2. 落日志加有界线程池异步投递，按 MailSendStatus 状态机推进（0→1→2/3，3→1 或 3→4）。
3. 补失败重试与死信、traceId 透传。
4. 补 F-09 最小权限：本模块当前所有 Controller 都没有 @RequiresPermissions。
5. 最后才把本模块加进 jeecg-system-start/pom.xml 依赖。

第 4 步必须在第 5 步之前。一旦加入 spring-start 依赖，org.jeecg 组件扫描会立刻拾取本模块的 @RestController，/team/mail/** 端点直接暴露；若权限还没补，就是裸奔接口。

## 8. 残余风险

- 合同漂移：接口文档 v1.1.0 为草案，立项后路径与字段可能调整，届时以立项版为准重对，不要修补旧骨架。
- 误用风险：他人可能误以为 /team/mail/** 已可用。已用“不注册 + DDL 不在 Flyway”双隔离并在类注释标注，但不能完全消除。
- 无测试：状态机枚举适合补纯单元测试（不依赖 Spring），建议优先覆盖 MailSendStatus.canTransitTo 与 MailCanonicalFolder.fromCode。
