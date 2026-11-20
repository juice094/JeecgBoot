---
title: OA 邮件体系需求文档
docId: MAIL-REQ-001
status: draft
version: 1.1.0
owner: mail-squad
reviewers: 架构组 / 产品 / SRE
updated: 2026-11-17
---

# OA 邮件体系需求文档

## 1. 背景与目标

OA 中邮件相关诉求分为两类，必须分线设计、分线验收：

| 主线 | 本质 | 典型场景 | 验收口径 |
|---|---|---|---|
| A 系统发信 | 平台基础设施 | 验证码、审批通知、告警、周报投递 | 流程触发后员工邮箱收到，失败可查、可重试 |
| B 员工邮箱 | 办公工具 | 收件箱、写信、回复、转发为待办 | 员工在 OA 内可收发本人企业邮箱，列表流畅、正文按需加载 |

Agent 能力（周报生成、收件箱分诊）建立在两条主线之上：**读走 B 的本地索引，写走 A 的发信通道**，绝不直连 SMTP/IMAP。

**目标**

- 业务模块不感知 SMTP/IMAP 协议细节，只依赖契约层 `mail-api`。
- 系统发信与员工邮箱账号模型、数据表、验收标准完全隔离。
- Agent 只消费通道层 API，不持有任何协议凭证，发送强制人工确认。
- 全链路可通过 `traceId` 关联业务日志、应用日志与投递结果。

> 项目现状：工作邮箱页面为 mock，接口未立项（docs/16「后置 M2+，暂 mock，待立项」）。本文为立项后需求基线，立项需宿拍板；实现载体为 JeecgBoot 单体新模块（暂定 `jeecg-module-mail`），表走 Flyway V3.9.6_x 顺延脚本。

## 2. 范围界定

**本期包含**

- 系统发信：多账号配置与测连、模板管理与占位符校验、同步落日志 + 异步投递、失败重试与死信、日志查询与脱敏。
- 员工邮箱：绑定与双测连、规范文件夹映射、增量同步只写信封索引、详情按需拉取、写信/回复/转发、邮件转待办。
- Agent：周报草稿（带来源与置信度）、人工确认发送、分诊摘要与紧急度、工具调用审计。
- 可观测性：异步 MDC 透传、核心指标、失败率告警、日志脱敏。

**本期不包含**

- 自建 Postfix/Dovecot、邮件全文检索集群、自动回复规则引擎、多租户计费、IMAP IDLE 实时同步（列为二期可插拔）。
- Agent 能力为封存线（docs/11），本期不实施，解封后按 §5.3 原文执行。

## 3. 角色与权限

| 角色 | 权限 |
|---|---|
| 系统管理员 | 维护系统发信账号与模板；查看全量发送日志；维护文件夹别名映射 |
| 普通员工 | 绑定/解绑本人邮箱；收发邮件；将邮件转为待办 |
| Agent（系统身份） | 生成周报草稿、查询本地索引、请求确认发送；**无 SMTP/IMAP 凭证**（封存线，本期不实施） |
| 运维 | 查看指标看板；按 traceId 排查；处理死信队列 |

## 4. 体系设计约束（需求侧可验收项）

### 4.1 分层与依赖

```text
接入方：业务模块 / OA 前端 / Agent / 运维控制台
   ↓
契约层 mail-api：MailSendApi / MailAccountBindingApi / MailIndexQueryApi
   ↓
能力层：MailAgentTools / TriageService / WeeklyReportService（只读索引 + 受控发送）
   ↓
通道层 A 系统发信           通道层 B 员工邮箱
SystemMailService           ImapSmtpConnector / MailSyncScheduler
   ↓
外部：托管 SMTP / 企业邮箱 IMAP+SMTP
```

**必须满足的依赖规则**

1. 业务模块编译期不得出现 `JavaMailSender` 或 Jakarta Mail 类型，只允许依赖 `MailSendApi`。
2. 能力层不得依赖 `ImapSmtpConnector`；读邮件只允许查 `oa_mail_message_index`。
3. 通道层 B 的发送复用通道层 A 的投递器，不重复实现 SMTP 会话管理。
4. 任何模块不得直接读写非本模块所有权数据表，跨模块只经 API 或事件。

> pilot 无 MQ 等消息中间件：异步投递用应用内有界线程池承担，失败补偿用定时扫描任务，死信以状态落库 + 管理端可见代替 DLQ。

### 4.2 数据所有权

| 表 | 唯一写者 | 业务服务 | Agent | 读方式 |
|---|---|---|---|---|
| `system_mail_account` | mail 模块 | 禁止 | 禁止 | 管理 API |
| `system_mail_template` | mail 模块 | 禁止 | 禁止 | 模板 API |
| `system_mail_log` | mail 模块 | 禁止 | 禁止 | 日志 API |
| `oa_mail_account` | mail 模块 | 禁止 | 禁止 | 绑定 API |
| `oa_mail_message_index` | mail 模块 | 禁止 | **只读** | Agent 查询 API |
| `oa_mail_folder_state` | mail 模块 | 禁止 | 禁止 | 同步状态 API |
| `oa_mail_biz_link` | mail 模块 | 禁止 | 禁止 | 关联 API |

### 4.3 核心数据模型

**系统发信**

- `system_mail_account`：`host`、`port`、`username`、`password`（加密）、`ssl_enable`、`starttls_enable`、`from_address`、`from_name`、`status`。
- `system_mail_template`：`code`（唯一）、`name`、`account_id`、`title`、`content`（HTML）、`params`（JSON，保存时扫描 `{key}` 生成）、`status`。
- `system_mail_log`：`account_id`、`template_code`、`to_mails`、`cc_mails`、`bcc_mails`、`template_title`（快照）、`template_content`（快照）、`send_status`、`retry_count`、`error_code`、`error_msg`、`trace_id`、`idempotency_key`（唯一索引，可空）。

**员工邮箱**

- `oa_mail_account`：`email`、`imap_*`、`smtp_*`（加密）、`test_status`、`last_sync_time`。
- `oa_mail_message_index`：`account_id`、`canonical_folder`、`imap_uid`、`message_id`、`subject`、`from_name`、`from_address_hash`、`to_summary`、`received_time`、`read_flag`、`has_attachment`、`snippet`；唯一键 `(account_id, canonical_folder, imap_uid)`；**不存正文**。
- `oa_mail_folder_state`：`account_id`、`remote_name`、`canonical`、`last_uid`；`canonical` 为空表示未识别，需人工映射。
- `oa_mail_biz_link`：`message_index_id`、`biz_type`、`biz_id`、`create_by`。

**状态机**

- 发送：`0 待发送 → 1 发送中 → 2 成功`；失败 `→ 3 失败`；重试超限 `→ 4 死信`。合法迁移 `0→1`、`1→2/3`、`3→1`、`3→4`。
- 同步：`idle → running → idle/error`；`error` 不回退 `last_uid`，下次续跑。

### 4.4 DDL 约定

- 必备 `create_by/create_time/update_by/update_time`；多租户沿用 jeecg `sys_tenant_id` 惯例。
- 新表走 Flyway 脚本，编号在 V3.9.6_x 上顺延，立项时取号。
- 密码字段长度 ≥ 256，密文存储（应用层对称加密，密钥走配置中心，立项时定方案）；SMTP 需原文认证，禁止不可逆哈希。
- `system_mail_log` 索引：`(send_status, update_time)`、`(trace_id)`、`(template_code, create_time)`。
- `oa_mail_message_index` 索引：`(account_id, canonical_folder, received_time DESC)`。

## 5. 功能需求

### 5.1 A 系统发信

| 编号 | 需求 | 优先级 | 验收标准 |
|---|---|---|---|
| A-1 | 支持多发送账号配置（host/port/SSL/STARTTLS/账号密码/发件人），保存前可测连 | P0 | 「测试连接」SMTP 握手成功才允许保存；被模板引用的账号删除请求被拒绝 |
| A-2 | 模板管理：`code` 全局唯一，保存时自动扫描 `{key}` 生成 `params` | P0 | 缺参数发送返回 `MAIL_SEND_TEMPLATE_PARAM_MISS` 并列出缺失 key |
| A-3 | 业务模块仅通过 `MailSendApi.sendSingleMail` 发信 | P0 | 业务代码静态检查无 Jakarta Mail 依赖 |
| A-4 | 发送前同步落日志（含渲染快照与 traceId），异步投递，接口立即返回 logId | P0 | 日志存在且状态为待发送；接口快速返回（不含投递） |
| A-5 | 失败重试：指数退避，最多 5 次，超限进死信 | P1 | 重试受 `retry_count` 与 `update_time` 双约束，5 分钟内不重复重试；超限状态置为死信 |
| A-6 | 日志查询：按状态/模板/traceId/收件人 hash/时间过滤 | P0 | 分页单页 ≤ 100 |
| A-7 | 收件人按权限脱敏 | P0 | 非管理员仅见 `to_hash`；应用日志不含完整地址 |

### 5.2 B 员工邮箱

| 编号 | 需求 | 优先级 | 验收标准 |
|---|---|---|---|
| B-1 | 绑定企业邮箱，**IMAP 与 SMTP 均测连通过**才允许落库 | P0 | 单侧失败明确返回失败侧与错误分类，不允许「能看不能回」 |
| B-2 | 规范文件夹映射（Sent/Sent Items/Sent Mail → `Sent` 等），支持别名维护 | P0 | 未识别文件夹进入待映射列表，不静默跳过 |
| B-3 | 定时增量同步，基于 `last_uid` 游标，只写信封索引 | P0 | 列表查询不触发 IMAP LIST；重复同步不产生重复记录（唯一键保证） |
| B-4 | 邮件详情按需 IMAP FETCH 正文与附件元信息 | P0 | 详情按需加载，不在列表拉正文 |
| B-5 | 写信、回复、回复全部、转发；发送走 A 通道或账号 SMTP | P0 | 发送后「已发送」文件夹同步可见 |
| B-6 | 邮件转待办/发起流程，落 `oa_mail_biz_link` | P1 | 关联记录可反查邮件与业务单 |
| B-7 | IMAP IDLE 实时同步作为可插拔策略 | P2 | 测连探测 CAPABILITY 后再启用；断线重连与游标回退可控 |

### 5.3 C 能力层 Agent（封存线，本期不实施）

> Agent 线为封存线（docs/11），本期不实施；解封后按下表执行，「读走本地索引、写走受控发送」的约束不变。

| 编号 | 需求 | 优先级 | 验收标准 |
|---|---|---|---|
| C-1 | 周报 Agent 从业务系统拉数生成一页纸（3 赢/3 风险/3 决策项），**每条带来源与置信度** | P1 | 数据缺失显式标注「数据缺失」，禁止推测填充 |
| C-2 | 发送类工具强制人工确认（`requiresConfirmation` + 一次性 `confirmToken` 双保险） | P0 | 未确认调用无 SMTP 投递；重复使用令牌返回 `MAIL_AGENT_TOKEN_EXPIRED` |
| C-3 | 收件箱分诊：摘要、建议动作、紧急度评分 | P1 | 评分可解释，输出评分因子，非纯黑盒 |
| C-4 | Agent 读邮件只查本地索引，不直连 IMAP | P0 | Agent 模块依赖树无 Jakarta Mail |
| C-5 | 工具调用可审计：traceId、入参、出参、token 用量 | P0 | 看板可按会话下钻 |

### 5.4 D 可观测性（需求验收口径）

| 编号 | 需求 | 优先级 | 验收标准 |
|---|---|---|---|
| D-1 | 异步线程透传 MDC，`finally` 清理 | P0 | 异步发送日志含正确 traceId，无串号 |
| D-2 | 核心指标：`email_send_total{result,error_code,template}`、`email_send_duration_seconds`、`email_send_active`、`email_retry_attempts_total` | P2 | pilot 以应用内计数 + 日志输出验收；接 Prometheus/Grafana 列二期 |
| D-3 | 告警：5 分钟失败率 > 5% 持续 10 分钟 | P2 | pilot 以死信状态 + 管理端可见验收；接正式告警列二期 |
| D-4 | 应用日志脱敏：不含完整收件人、正文、密码、附件内容 | P0 | 日志抽样检查通过 |

**实现参考（不作为需求验收项）**：异步上下文传递用 `ContextPropagatingTaskDecorator` 或等价方案；生产采样 10% 起步、错误请求强制保留；日志输出 ECS 结构化 JSON（采集方案立项时定）；Agent 工具由 Spring AI 自动产生 `execute_tool` span，仅补草稿生成与确认发送两条业务语义日志。

## 6. 非功能需求

| 类别 | 要求 |
|---|---|
| 性能 | 发信接口快速返回（不含投递）；索引列表 P95 ≤ 500ms；详情按需拉取 |
| 可靠性 | 发信日志不丢；重启后 `send_status IN (0,1) AND update_time < now()-5min AND retry_count < 5` 可补偿；死信可告警 |
| 安全 | SMTP/IMAP 密码加密存储；Agent 无凭证访问权；发送默认需人工确认；日志脱敏 |
| 兼容 | JeecgBoot 单体：Java 17 + Spring Boot 4.1 + MyBatis-Plus（jakarta 命名空间）；GreenMail 测试版本与 Jakarta Mail 对齐 |
| 隔离 | `system_mail_account` 与 `oa_mail_account` 分表，修改系统发信密码不影响员工 IMAP |

## 7. 迭代计划

| 期 | 交付内容 | 前置 |
|---|---|---|
| 现状 | 工作邮箱页 mock，接口未立项（docs/16） | — |
| 一期 | A-1~A-4、A-6、B-1、C-2、D-1（系统发信闭环 + 双测连 + traceId + 确认发送） | 宿拍板立项 + Flyway 取号 |
| 二期 | B-2~B-5、A-5、A-7（员工邮箱收发 + 重试 + 脱敏） | 一期验收 |
| 三期 | C-1、C-3、B-6、D-2~D-4（Agent 能力 + 指标告警完善，封存线解封后启用） | 二期验收 + 解封拍板 |
| 四期 | B-7（IDLE）、全文检索（按需） | — |

## 8. 验收清单

1. 业务流程触发通知 → 员工邮箱收到；`system_mail_log` 状态为成功且含 `trace_id`，可在日志中按 traceId 检索到对应应用日志。
2. 模板缺参 → 返回 `MAIL_SEND_TEMPLATE_PARAM_MISS` 并列出缺失 key，无半截邮件发出。
3. 绑定邮箱仅 IMAP 通 → 保存被拒，明确提示 SMTP 失败侧与错误分类。
4. 同步 1000 封邮件 → 列表秒开；打开详情才触发 IMAP FETCH；重复同步无重复记录。
5. Agent 未确认 → 无投递；确认后 → 返回 logId 且有审计记录与 traceId（封存线解封后适用）。
6. 人为断开 SMTP → 失败率指标上升、告警触发、重试有上限并最终进死信。
7. 应用日志抽样不含完整收件人地址与邮件正文。

## 9. 关联文档

- 接口文档：`OA 邮件体系接口文档.md`（同目录）
- 页面↔后端映射（现状定位）：`docs/16-backend-module-design.md`（工作邮箱：后置 M2+ 暂 mock 待立项）
- 排期依据：`ROADMAP.md`；封存依据：`docs/11`；协作规矩：`docs/13`
- 架构决策：`docs/adr/ADR-001-dual-line-mail.md`、`ADR-002-agent-no-credentials.md`、`ADR-003-async-persistence.md`（立项时补建）
