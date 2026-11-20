# 日志系统与埋点规划（2026-11-18 起草）

> 状态：规划稿，P0 可与任何开发波并行。
> 现状：logback 文件日志（tmp/jeecg-8081.log）+ jeecg @AutoLog 操作日志入库；**无 traceId 贯通、
> 业务事件无结构化埋点**——排查跨接口问题（如「同步失败」）要靠翻日志人肉串行。

## 0. 目标分层

| 层 | 内容 | 消费方式 |
|---|---|---|
| L1 追踪 | 每次请求生成 traceId，HTTP→Service→Mapper→外部协议(IMAP/SMTP) 全链路透传 | 日志检索 `grep traceId`；排障时间从分钟级降到秒级 |
| L2 事件 | 关键业务事件结构化落日志（JSON）：审批状态迁移、邮件发送/同步指标、绑定/解绑 | 个人报表页数据源；运营统计 |
| L3 指标 | 聚合数字：同步成功率/耗时、IMAP 操作延迟分布、审批平均停留时长 | 监控告警（试点先人工看，不接告警渠道） |

## 1. 埋点清单（P0 范围，按已闭环系统）

### 审批线（team）
| 事件 | 字段 | 触发点 |
|---|---|---|
| approval.submitted | code, category, amount, applicant, stepCount | submit 成功 |
| approval.transition | code, from→to, operator, action(approve/reject/return/cancel/resubmit) | 每次状态迁移（含并发冲突拒绝） |
| approval.validation_failed | category, reason | 校验器拒绝（负路径也要埋，看前端绕过尝试） |

### 邮件线（mail，已有先例字段）
| 事件 | 字段 | 触发点 |
|---|---|---|
| mail.bind / unbind | accountId, email(脱敏 hash), result | 双测连落库/解绑 |
| mail.sync | accountId, durationMs, folders, indexed, failedFolders | 每轮同步结束（韧性修复后的跳过数） |
| mail.detail_fetched | accountId, latencyMs, truncated | 已有 fetchLatencyMs 先例，补上日志 |
| mail.sent | accountId, toHash, subjectHash, smtpLatencyMs, archivedToSent(bool) | 发送完成 |
| mail.deleted | accountId, mode(trash/permanent), folder | 删除完成 |

### 系统面
| 事件 | 说明 |
|---|---|
| login.success / login.failed | jeecg 已有登录日志，补 traceId 关联 |
| permission.denied | @RequiresPermissions 拒绝（403 面），安全审计用 |

## 2. 技术方案（禁新增依赖约束下）

1. **traceId**：`OncePerRequestFilter`（jeecg 已有 Shiro 过滤器链，插入自定义 Filter）生成 UUID 放 MDC；HTTP 客户端侧（未来）经 header 透传；IMAP/SMTP 操作在 MailMessageOperator/MailSyncEngine 入口 `MDC.put("traceId", …)`。logback pattern 加 `%X{traceId}`。
2. **事件埋点**：不引日志门面新依赖，沿用 slf4j；约定专用 logger（`MAIL_EVENT`/`TEAM_EVENT`）输出单行 JSON（FastJSON2 已在依赖内）。新增工具类 `EventLoggers`（静态 logger 持有者 + 统一字段规范）。
3. **审计双轨**：TeamAuditLogger（入库）保留给人事/合规查询；事件日志给技术/运营面——两者不合并。
4. **指标**：P1 再做。先用事件日志跑数（`grep | jq` 口径足够试点）；如要图形化，个人报表页直接查事件（试点量级无需时序库）。

## 3. 波次编排

| 波 | 内容 | 依赖 |
|---|---|---|
| P0（≈1 波） | traceId 过滤器 + logback pattern + EventLoggers 工具类 + 审批/邮件核心事件接入 + 单测（MDC 断言） | 无，可随时插波 |
| P1 | 同步/发送指标聚合视图（个人报表页接事件查询）；权限拒绝审计 | P0 |
| P2 | 跨服务 header 透传（随微服务线 P1 首拆 mail 时启用） | 微服务线触发 |

## 4. 验收口径

- 任取一次审批/邮件操作，用其 traceId 在日志中能串起该请求全部行（含 IMAP 命令）；
- 事件日志单行可解析（`jq` 通过），字段命名遵循清单；
- 零新依赖、零表变更（事件日志不进库，文件/SQL 查询双通道够用）。

## 5. 明确不做

- 不接 ELK/Loki（试点单机，grep 足够）；
- 不改 jeecg 平台自带日志体系（只加过滤器与 pattern）；
- 不在前端埋点（后端事件已够试点决策）。
