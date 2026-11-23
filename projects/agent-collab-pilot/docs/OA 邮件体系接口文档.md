---
title: OA 邮件体系接口文档
docId: MAIL-API-001
status: draft
version: 1.1.0
owner: mail-squad
updated: 2026-11-17
---

# OA 邮件体系接口文档

> 本文档为立项后接口基线，未实现。工作邮箱现状 mock、待立项（docs/16）；接口路径暂定，立项时定模块归属（暂定 `jeecg-module-mail`）。

## 1. 通用约定

| 项 | 约定 |
|---|---|
| REST Base Path | `/jeecg-boot/team/mail`（暂定；挂 JeecgBoot 单体上下文） |
| 认证 | jeecg 登录态（`X-Access-Token` header，Shiro 校验） |
| 统一响应 | jeecg `Result<T>`：`{ "success": true, "code": 200, "message": "ok", "result": {}, "timestamp": ... }`；错误 `code=500` + `message` 业务文案 |
| 分页参数 | jeecg 惯例 `pageNo`（默认 1）、`pageSize`（默认 10，最大 100），返回 `IPage`（records/total/pages/current） |
| 时间格式 | `yyyy-MM-dd HH:mm:ss`（GMT+8） |
| 幂等 | 发送类接口支持 `Idempotency-Key` 请求头，24h 内同 key 返回同一 logId；参数不一致返回 `MAIL_IDEMPOTENT_CONFLICT`（应用层校验 `system_mail_log.idempotency_key` 唯一索引） |
| 链路 | 异步投递日志透传 `traceId`（MDC）；`system_mail_log.trace_id` 与之一致 |

## 2. 契约层 Java API（`mail-api`）

业务模块与 Agent **只依赖此层**，本层不暴露 Jakarta Mail、SMTP、IMAP 类型。

```java
public interface MailSendApi {
    /** 同步落日志并触发异步投递，返回 logId */
    Long sendSingleMail(MailSendSingleReqDTO req);
}

public interface MailAccountBindingApi {
    /** IMAP 与 SMTP 双测连通过才落库 */
    Long bind(MailAccountBindReqDTO req);
    void unbind(Long accountId);
    ConnectionTestResult testConnection(MailServerConfig cfg);
}

public interface MailIndexQueryApi {
    /** 供 Agent 只读使用，结果脱敏 */
    PageResult<MailIndexDTO> search(MailSearchReqDTO req);
    MailIndexDTO getById(Long id);
}
```

```java
@Data
public class MailSendSingleReqDTO {
    @NotBlank private String templateCode;
    @NotBlank private String to;            // 逗号分隔
    private String cc;
    private String bcc;
    @NotNull  private Map<String, Object> params;
    private String idempotencyKey;          // 可选
}

@Data
public class MailAccountBindReqDTO {
    @NotBlank private String email;
    @NotNull  private MailServerConfig imap;
    @NotNull  private MailServerConfig smtp;
}

@Data
public class MailServerConfig {
    @NotBlank private String host;
    @NotNull  private Integer port;
    private boolean ssl;
    @NotBlank private String username;
    @NotBlank private String password;
}

@Data
public class ConnectionTestResult {
    private boolean imapOk;
    private boolean smtpOk;
    private long latencyMs;
    private String errorCode;               // 失败侧分类
}
```

**兼容性约定**：新增字段只增不改；必填字段不得中途改为可选；方法签名变更走主版本号，旧方法 `@Deprecated` 保留一个大版本。

## 3. 系统发信 REST 接口

### 3.1 账号管理

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/system/accounts` | 新建发送账号 | 管理员 |
| PUT | `/system/accounts/{id}` | 修改 | 管理员 |
| GET | `/system/accounts` | 列表 | 管理员 |
| POST | `/system/accounts/{id}/test` | 测试 SMTP | 管理员 |
| DELETE | `/system/accounts/{id}` | 删除（被模板引用则拒绝） | 管理员 |

**新建请求体**

```json
{
  "host": "smtp.example.com",
  "port": 587,
  "username": "noreply@example.com",
  "password": "******",
  "sslEnable": false,
  "starttlsEnable": true,
  "fromAddress": "noreply@example.com",
  "fromName": "OA 系统通知"
}
```

**测试连接响应**

```json
{ "success": true, "code": 200, "result": { "smtpOk": true, "latencyMs": 86, "errorCode": null } }
```

### 3.2 模板管理

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/system/templates` | 新建（自动解析 `{key}` 写入 `params`） |
| PUT | `/system/templates/{id}` | 修改 |
| GET | `/system/templates` | 分页查询（`code`/`name`/`status`） |
| GET | `/system/templates/{code}` | 按 code 查详情 |
| POST | `/system/templates/{code}/preview` | 传入 params 预览渲染结果 |
| DELETE | `/system/templates/{id}` | 删除 |

**新建请求体**

```json
{
  "code": "APPROVAL_TIMEOUT",
  "name": "审批超时提醒",
  "accountId": 1,
  "title": "您有审批单即将超时：{title}",
  "content": "<p>尊敬的 {name}，单据 {title} 将于 {deadline} 超时。</p>",
  "status": 0
}
```

**响应**

```json
{ "success": true, "code": 200, "result": { "id": 10, "params": ["title", "name", "deadline"] } }
```

### 3.3 发送

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/system/send` | 单发（等价 `MailSendApi`） | 服务内部/管理员 |
| POST | `/system/send/batch` | 批量（≤ 500，逐条落日志） | 服务内部/管理员 |

**请求体**

```json
{
  "templateCode": "APPROVAL_TIMEOUT",
  "to": "zhangsan@example.com",
  "cc": "manager@example.com",
  "params": { "name": "张三", "title": "报销单-1123", "deadline": "2026-11-18 18:00" },
  "idempotencyKey": "approval-1123-timeout-1"
}
```

**响应**

```json
{ "success": true, "code": 200, "result": { "logId": 90001 } }
```

**错误**：业务错误走 `Result.error("模板不存在")` 等文案；结构化错误码写入日志与扩展字段，候选：`MAIL_TEMPLATE_NOT_FOUND`、`MAIL_SEND_TEMPLATE_PARAM_MISS`、`MAIL_ACCOUNT_DISABLED`、`MAIL_IDEMPOTENT_CONFLICT`。

### 3.4 日志查询与重试

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/system/logs` | 过滤：`status`/`templateCode`/`toHash`/`traceId`/`beginTime`/`endTime` |
| GET | `/system/logs/{id}` | 详情（含快照；收件人按权限脱敏） |
| POST | `/system/logs/{id}/retry` | 手动重试（仅失败且 `retry_count < 5`） |
| GET | `/system/logs/stat` | 按状态/模板聚合统计 |

**列表响应示例**

```json
{
  "success": true,
  "code": 200,
  "result": {
    "total": 128,
    "records": [{
      "id": 90001,
      "templateCode": "APPROVAL_TIMEOUT",
      "toHash": "a1b2c3d4",
      "sendStatus": 2,
      "retryCount": 0,
      "traceId": "4bf92f3577b34da6",
      "createTime": "2026-11-17 10:00:00"
    }]
  }
}
```

**发送状态枚举**：`0` 待发送、`1` 发送中、`2` 成功、`3` 失败、`4` 死信。

## 4. 员工邮箱客户端接口

### 4.1 绑定与测连

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/accounts` | 绑定本人邮箱（IMAP + SMTP 双测连通过才落库） |
| PUT | `/accounts/{id}` | 修改配置（触发重测） |
| POST | `/accounts/{id}/test` | 手动测连 |
| DELETE | `/accounts/{id}` | 解绑（索引保留策略可配置） |
| GET | `/accounts` | 本人账号列表及同步状态 |

**绑定请求体**

```json
{
  "email": "zhangsan@company.com",
  "imap": { "host": "imap.company.com", "port": 993, "ssl": true, "username": "zhangsan", "password": "******" },
  "smtp": { "host": "smtp.company.com", "port": 465, "ssl": true, "username": "zhangsan", "password": "******" }
}
```

**测连失败响应**

```json
{ "success": false, "code": 500, "message": "SMTP 连接失败(smtp_auth_failed)",
  "result": { "imapOk": true, "smtpOk": false, "smtpErrorCode": "smtp_auth_failed" } }
```

### 4.2 文件夹与同步

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/accounts/{id}/folders` | IMAP 文件夹列表 + 规范映射结果 |
| PUT | `/accounts/{id}/folders/mapping` | 维护别名映射（未识别文件夹） |
| POST | `/accounts/{id}/sync` | 手动触发增量同步（异步，返回 taskId） |
| GET | `/accounts/{id}/sync/status` | 同步状态（`lastUid`、`lastSyncTime`、`running`） |

**文件夹响应**

```json
{
  "success": true,
  "code": 200,
  "result": {
    "folders": [
      { "remoteName": "INBOX", "canonical": "Inbox", "lastUid": 10231 },
      { "remoteName": "已发送", "canonical": "Sent", "lastUid": 880 },
      { "remoteName": "自定义归档", "canonical": null }
    ]
  }
}
```

> `canonical` 为 `null` 表示未识别，需通过映射接口维护，同步不会静默跳过。

### 4.3 列表与详情

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/messages` | 列表（`accountId`、`canonicalFolder`、`keyword`、`unreadOnly`、分页），**只查索引表** |
| GET | `/messages/{id}` | 详情（按需 IMAP FETCH 正文/附件元信息） |
| PATCH | `/messages/{id}/read` | 标记已读/未读 |
| DELETE | `/messages/{id}` | 删除（移动到 Trash 并同步 IMAP） |

**列表项**

```json
{
  "id": 5001,
  "accountId": 7,
  "canonicalFolder": "Inbox",
  "subject": "Q4 交付计划确认",
  "fromName": "李四",
  "fromAddressHash": "e5f6a7b8",
  "receivedTime": "2026-11-17 09:12:00",
  "read": false,
  "hasAttachment": true,
  "snippet": "请确认附件中的交付节点……"
}
```

**详情响应**（正文按需拉取）

```json
{
  "success": true,
  "code": 200,
  "result": {
    "id": 5001,
    "subject": "Q4 交付计划确认",
    "html": "<p>...</p>",
    "text": "...",
    "attachments": [{ "name": "plan.xlsx", "size": 20480, "mime": "application/vnd..." }],
    "fetchedFromImap": true,
    "fetchLatencyMs": 420
  }
}
```

### 4.4 发送与业务关联

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/messages/send` | 写信/回复/转发（`mode`: new/reply/replyAll/forward） |
| POST | `/messages/{id}/to-todo` | 转待办，写 `oa_mail_biz_link` |
| GET | `/messages/{id}/biz-links` | 查询关联业务 |

**发送请求体**

```json
{
  "accountId": 7,
  "mode": "reply",
  "replyToMessageId": 5001,
  "to": "lisi@company.com",
  "cc": [],
  "subject": "Re: Q4 交付计划确认",
  "html": "<p>收到，周五前回复。</p>",
  "attachments": ["fileKey-001"],
  "idempotencyKey": "reply-5001-1"
}
```

## 5. Agent 受控接口（封存线，本期不实施）

> Agent 线为封存线（docs/11），以下接口解封后启用。约束不变：**不持有 SMTP/IMAP 凭证，写仅经 confirm 端点，读仅经索引查询端点**。

| 方法 | 路径 | 说明 | 约束 |
|---|---|---|---|
| POST | `/agent/weekly-report/draft` | 生成周报草稿（不发送） | 返回内容 + 来源 + 置信度 |
| POST | `/agent/weekly-report/confirm` | 人工确认后发送 | 必带 `draftId` + `confirmToken` |
| GET | `/agent/messages/search` | 查本地索引（分诊/汇总用） | 只读，结果脱敏 |
| POST | `/agent/triage` | 对指定邮件生成摘要/建议/紧急度 | 输出含评分因子 |

**草稿请求**

```json
{ "period": "2026-W46", "scope": ["sales", "delivery"], "recipients": ["ceo@company.com"] }
```

**草稿响应**

```json
{
  "success": true,
  "code": 200,
  "result": {
    "draftId": "dr-20261117-01",
    "subject": "2026-W46 周报",
    "html": "<h3>三个赢</h3>...",
    "items": [
      { "type": "risk", "text": "华东交付延迟概率 72%",
        "source": "supply_db:order_1123", "confidence": 0.82 }
    ],
    "confirmToken": "ct-9f2a...",
    "expiresAt": "2026-11-17 18:00:00"
  }
}
```

**确认发送请求**

```json
{ "draftId": "dr-20261117-01", "confirmToken": "ct-9f2a...", "confirmedBy": "u1001" }
```

**确认发送响应**

```json
{ "success": true, "code": 200, "result": { "logId": 90088 } }
```

**工具侧约束**

- `@Tool(requiresConfirmation = true)` 与一次性 `confirmToken` 形成双保险。
- 带确认的工具必须通过 `chatResponse()` 与 `ToolCallDecisions` 走决策流程，不能只取 `content()`。
- 拒绝时写回对话历史，不中断会话。
- `confirmToken` 一次性，绑定 `draftId` + 操作人 + 过期时间；未确认调用返回 `MAIL_AGENT_CONFIRM_REQUIRED`。

## 6. 投递与补偿

pilot 无 MQ 等消息中间件，采用应用内方案：

- 异步投递：应用内有界线程池（`ThreadPoolExecutor`，禁 `Executors`），任务从 `system_mail_log` 状态 0 取件执行。
- 补偿扫描：定时任务（如每 5 分钟）将 `send_status IN (0,1) AND update_time < now()-5min AND retry_count < 5` 的记录重新入队。
- 死信：重试超限置 `send_status=4`，管理端可见并支持人工重试；接正式告警列二期。

## 7. 错误码

业务错误优先走 jeecg `Result.error` 文案；下表码表用于日志、指标标签与扩展字段。

### 7.1 业务错误码

| code | 常量 | 含义 |
|---|---|---|
| OK | 成功（HTTP 200） |
| MAIL_TEMPLATE_NOT_FOUND | 模板不存在 |
| MAIL_SEND_TEMPLATE_PARAM_MISS | 模板参数缺失 |
| MAIL_ACCOUNT_DISABLED | 发送账号不可用 |
| MAIL_IMAP_CONNECT_FAILED | IMAP 连接失败 |
| MAIL_SMTP_CONNECT_FAILED | SMTP 连接失败 |
| MAIL_FOLDER_MAPPING_MISS | 文件夹未映射 |
| MAIL_AGENT_CONFIRM_REQUIRED | Agent 发送未确认（封存线） |
| MAIL_AGENT_TOKEN_EXPIRED | 确认令牌过期/已用（封存线） |
| MAIL_IDEMPOTENT_CONFLICT | 幂等键冲突（参数不一致） |
| MAIL_RETRY_LIMIT_EXCEEDED | 超过重试上限 |
| MAIL_INTERNAL_ERROR | 内部错误 |

### 7.2 SMTP 错误分类（写入指标 `error_code` 标签）

| 分类 | 含义 | 处置 |
|---|---|---|
| `smtp_connection` | 连不上/被拒 | 查网络与端口策略 |
| `smtp_auth_failed` | 认证失败 | 查账号密码/授权码 |
| `smtp_timeout` | 超时 | 服务商限流或网络抖动 |
| `recipient_refused` | 收件人被拒 | 校验地址、退信策略 |
| `content_rejected` | 内容被拒 | 查反垃圾规则 |
| `unknown` | 未归类 | 补分类规则 |

## 8. 与可观测性的对接约定

- 异步投递日志透传 `traceId`，`system_mail_log.trace_id` 与之一致。
- 发送类接口在应用日志中输出：`logId`、`templateCode`、`to_hash`（SHA-256 前 8 位）、`duration_ms`、`result`、`error_code`；**不得输出完整收件人、邮件正文、SMTP 密码**。
- 指标 `email_send_total{result,error_code,template}` 等：pilot 以应用内计数 + 日志输出代替 Prometheus，接 metrics 后端列二期。
- Agent 工具调用由 Spring AI 自动产生 `execute_tool` span；`draft` 与 `confirm` 端点额外记录 `draftId`、`confirmedBy`（封存线解封后适用）。

## 9. 关联文档

- 需求文档：`OA 邮件体系需求文档.md`（同目录）
- 页面↔后端映射（现状定位）：`docs/16-backend-module-design.md`（工作邮箱：后置 M2+ 暂 mock 待立项）
- 排期依据：`ROADMAP.md`；封存依据：`docs/11`；协作规矩：`docs/13`
