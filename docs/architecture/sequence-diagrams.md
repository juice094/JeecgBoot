# 业务设计时序图（工程文档）

> 位置约定：本文档是团队协作体系（审批/Issues/邮件）的**业务设计工程文档**，用 Mermaid 时序图
> 描述已闭环的核心链路。改动对应链路时必须同步更新本文（代码评审检查项）。
> 相关：施工/验证报告在各模块 docs/ 下（CLOSURE-WALKTHROUGH / B3-B5-B6-REPORT 等）。

## 1. 审批单全生命周期（v2 + 字段模板）

申请单号 OA-20261118-005/006 为实证样本。关键设计：后端不信任前端（校验器）、条件更新防并发、越权硬拒。

```mermaid
sequenceDiagram
    autonumber
    actor U as 申请人(李想)
    participant FE as RequestForm.vue
    participant API as TeamApprovalController
    participant VAL as ApprovalFormValidator
    participant DB as MySQL(team_approval*)

    U->>FE: 切换类别「费用报销」
    FE->>API: GET /template/schema?category=
    API-->>FE: fieldSchema（金额/费用类型/发票张数）
    FE-->>U: 按 schema 动态渲染表单
    U->>FE: 提交 formData{amount:3800,...}
    FE->>API: POST /submit {category,title,formData}
    API->>VAL: 按 field_schema 复核（必填/类型/范围/枚举）
    alt 缺必填（负路径，实证：「字段「金额」必填」）
        VAL-->>U: 500 校验失败，链路终止
    end
    VAL->>API: amount 提取为 BigDecimal
    API->>DB: 按模板生成步骤链（王凯→陈静）
    Note over API,DB: 校验「审批链不含申请人」
    API-->>FE: code=OA-..., status=pending

    actor M as 审批人(王凯/陈静)
    M->>API: POST /approve?id=&comment=(中文须编码)
    API->>DB: UPDATE step WHERE id=? AND action='wait'
    alt 影响行数=0（并发冲突/非当前审批人）
        API-->>M: 500「越权审批: 当前步骤审批人为 王凯，登录人为 李想」
    else 末步通过
        API->>DB: UPDATE main SET status=approved（条件更新）
        API-->>M: 已通过（终态）
    end
```

## 2. 员工邮箱绑定与增量同步（B-1/B-2）

```mermaid
sequenceDiagram
    autonumber
    actor U as 员工(周景潇)
    participant FE as 绑定界面
    participant API as OaMailController
    participant CONN as ImapSmtpConnector
    participant ENG as MailSyncEngine
    participant DB as MySQL(oa_mail_*)

    U->>API: POST /accounts（IMAP+SMTP 配置）
    API->>CONN: testBoth（真实握手）
    alt 任一侧失败
        CONN-->>U: 结构化失败体（imapOk/smtpOk/分类码），不落库
    end
    API->>DB: 密码 AES 密文落库（D-76）
    Note over API,DB: 之后：触发同步 / 调度器周期同步

    ENG->>DB: 读 folder_state 游标(last_uid) + 人工映射
    loop 每个远端文件夹
        ENG->>ENG: MailFolderMapping 归一（未识别→告警待映射，不静默跳过）
        alt 命名空间父目录等无法 SELECT（QQ「其他文件夹」）
            ENG->>ENG: 记 warn 跳过该文件夹，整轮继续（韧性口径）
        else 正常文件夹
            ENG->>ENG: UID 增量抓取（ENVELOPE+FLAGS）
            ENG->>DB: 唯一键 upsert 索引（account,folder,uid）
        end
    end
    ENG->>DB: 推进游标（error 不回退，下轮续跑）
```

## 3. 邮件详情与已读回写（B-3，正文不落库）

```mermaid
sequenceDiagram
    autonumber
    actor U as 员工
    participant FE as WorkEmail.vue
    participant API as OaMailServiceImpl
    participant OP as MailMessageOperator
    participant EXT as MailBodyExtractor
    participant IMAP as QQ IMAP

    U->>FE: 点开邮件
    FE->>API: GET /messages/{id}
    API->>DB: 读索引行（folder, imap_uid, read_flag）
    API->>OP: resolveRemoteFolder（canonical→remoteName）
    alt 文件夹未映射
        API-->>U: 500「文件夹未映射…维护映射后再试」
    end
    OP->>IMAP: SELECT + UID FETCH（懒加载正文）
    alt UID 失效
        API-->>U: 500「远端邮件不存在…请同步后重试」
    end
    OP-->>API: MimeMessage
    API->>EXT: extract（text/plain 优先 → html 兜底 → 附件元数据）
    EXT-->>API: text/html/attachments（256K 截断标记）
    opt 原 read_flag=false（打开即已读，Outlook 口径）
        API->>IMAP: STORE +FLAGS \Seen
        API->>DB: read_flag=1
    end
    API-->>FE: 详情 + fetchLatencyMs（如实上报，实证 ~2s）
    Note over FE: html-only 邮件本地剥标签展示（不引入 v-html，防邮件 XSS）
```

## 4. 写信发送（B-6）

```mermaid
sequenceDiagram
    autonumber
    actor U as 员工
    participant API as OaMailServiceImpl
    participant SMTP as QQ SMTP
    participant IMAP as QQ IMAP
    participant DB as 索引

    U->>API: POST /messages/send {accountId,to,subject,html}
    alt to/正文为空
        API-->>U: 500 参数校验失败
    end
    API->>API: 解密绑定凭据，构建 MimeMessage（From=绑定邮箱）
    API->>SMTP: 直发（connect 显式传凭据）
    SMTP-->>API: 250 OK
    par best-effort 归档（失败不阻断发送结果）
        API->>IMAP: APPEND 到「已发送」+\Seen
        alt QQ 已自动归档
            IMAP-->>API: NO "already saved to Sent" → 降级 INFO（预期行为）
        else 无 Sent 映射
            API->>API: 记 warn「不入归档」
        end
    end
    API-->>U: 发送成功
    Note over SMTP,DB: 收件箱副本与 Sent 副本均靠下次增量同步入索引（游标兜底）
```

## 5. 邮件删除（B-5，双路径）

```mermaid
sequenceDiagram
    autonumber
    participant API as OaMailServiceImpl
    participant OP as MailMessageOperator
    participant IMAP as QQ IMAP
    participant DB as 索引

    API->>OP: delete(account, sourceFolder, uid)
    alt 有 Trash 映射 且 当前不在 Trash（实证映射：Deleted Messages）
        OP->>IMAP: COPY 到 Trash
        OP->>IMAP: 源文件夹 \Deleted + EXPUNGE
        Note over API,DB: Trash 内副本由下次同步按新 UID 入索引（软删）
    else 无映射或已在 Trash
        OP->>IMAP: 源文件夹 \Deleted + EXPUNGE（永久删除）
    end
    IMAP-->>OP: OK
    API->>DB: 删除索引行（远端失败则不删，可重试）
```

## 6. 任务评论闭环（Issues 线，循环依赖解法）

```mermaid
sequenceDiagram
    autonumber
    actor U as 成员
    participant C as TeamTaskCommentServiceImpl
    participant M as TeamTaskMapper
    participant A as TeamAuditLogger

    U->>C: 评论（限长 1 万字符校验）
    Note over C: 经验 140：曾注入 ITeamTaskService 形成<br/>TeamService↔CommentService 循环依赖（从未启动过）
    C->>M: 直注 Mapper.selectById（解环，只读依赖）
    alt 任务不存在/超长
        C-->>U: JeecgBootException
    end
    C->>DB: insert comment
    C->>A: 审计留痕（CREATE/comment 动作）
    C-->>U: 成功 → 时间线混排展示（TaskDetail）
```
