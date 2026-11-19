# 改动后流程图 —— 2026-11-19 晚（W1 + 观测性 + 治理基座落地后）

> 姊妹篇：本文与 `DEFENSE-业务编排与答辩-2026-11-19.md` 同体例（mermaid 图 + 要点 + 文件相对路径）。
> **路径基准：JeecgBoot 仓库根**（`C:\Users\22414\dev\JeecgBoot\`）；治理件在 `projects/agent-collab-pilot/` 下另标。
> 全部端点/ID 已与真实代码逐字核对（非凭记忆）。

---

## 1. 改动后单体全景

```mermaid
graph TB
    subgraph Client
        U[浏览器/工具<br/>127.0.0.1:3200 vite 代理]
    end
    U -->|/jeecg-boot/**| GW[单体 jeecg-system-start<br/>--server.port=8081 在役<br/>验证用 8083]
    subgraph Backend[jeecg-boot/（Java 17 + Boot 4.1）]
        GW --> F1[TraceIdFilter<br/>base-core：MDC 注入+白名单校验]
        F1 --> M1[jeecg-module-system<br/>用户/权限/租户]
        F1 --> M2[jeecg-module-team<br/>任务/审批/缺陷/审计]
        F1 --> M3[jeecg-module-mail<br/>系统发信/员工邮箱]
        F1 --> M4[jeecg-module-flow ★新<br/>Flowable 8.0.0 引擎]
        F1 --> M5[jeecg-module-demo / airag]
        M2 & M3 --> EL[EventLoggers<br/>base-core：TEAM/MAIL_EVENT 单行 JSON]
    end
    subgraph Store
        DB[(MySQL jeecg-boot<br/>业务表 + act_* 39 张★新<br/>flowable 引擎自管)]
        LOG[(日志文件<br/>pattern 含 %X{traceId} ★新)]
    end
    M1 & M2 & M3 & M4 --> DB
    EL --> LOG
```

要点：
- **★新 = 今日改动**。flow 模块已注册进 system-start（下次启动引擎随单体起，39 表幂等自管）。
- 观测件（TraceIdFilter/EventLoggers）在 base-core，所有业务模块共享，不动在役逻辑。
- 文件：`jeecg-boot/jeecg-module-system/jeecg-system-start/pom.xml`（flow 接线行）、`jeecg-boot/jeecg-boot-base-core/src/main/java/org/jeecg/config/filter/TraceIdFilter.java`、`jeecg-boot/jeecg-boot-base-core/src/main/java/org/jeecg/common/log/EventLoggers.java`、`jeecg-boot/jeecg-module-system/jeecg-system-start/src/main/resources/logback-spring.xml`

## 2. W1 请假流程：REST 端到端（已走通，实例可复验）

```mermaid
sequenceDiagram
    participant C as 调用方(curl/前端)
    participant CTL as FlowDemoController<br/>/flow/demo/**
    participant SVC as FlowLeaveService
    participant ENG as Flowable 引擎(8.0.0)
    participant DB as act_* 表(39张)
    C->>CTL: POST /flow/demo/leave/start<br/>?applicant&days&reason
    CTL->>SVC: startLeaveProcess(applicant, days, reason)
    SVC->>ENG: runtimeService.startProcessInstanceByKey<br/>("leaveProcess", variables)
    ENG->>DB: act_ru_execution + act_hi_procinst 落行
    ENG-->>C: processInstanceId（如 e980c5cb-…）
    C->>CTL: POST /flow/demo/leave/approve<br/>?processInstanceId&approved
    CTL->>SVC: approve(processInstanceId, approved)
    SVC->>ENG: completeTask(supervisorApprove, 办理人=主管)
    SVC->>ENG: completeTask(archiveTask, 办理人=归档)
    ENG->>DB: 任务完成，实例走向 endEvent
    C->>CTL: GET /flow/demo/leave/status?processInstanceId
    CTL-->>C: finished=true, openTasks=[]
```

要点：
- 三个端点与代码逐字一致；BPMN id：`leaveProcess / supervisorApprove / archiveTask / startEvent / endEvent`。
- 流程定义**编程式部署**（嵌套 jar 下 starter 的 classpath* 扫描失效的实证绕法），启动时幂等（已部署则跳过并打 version 日志）。
- 冒烟口径：在役回归 `/team/task/board` 匿名 401（非 5xx）= 通过。
- 文件：`jeecg-boot/jeecg-boot-module/jeecg-module-flow/src/main/java/org/jeecg/modules/flow/controller/FlowDemoController.java`（`:26` @RequestMapping("/flow/demo")，`:34/44/53` 三端点）、`.../flow/service/FlowLeaveService.java`（`:62` startLeaveProcess，`:85` approve）、`.../flow/config/FlowDeploymentConfig.java`（`:23` 资源路径，部署幂等）、`.../flow/config/FlowModuleConfig.java`（`:34` HistoryLevel.AUDIT，`:35` 异步执行器开）、`.../flow/src/main/resources/processes/leave-process.bpmn20.xml`

## 3. W3/W4 演进位置（设计已定，未施工——虚线为未来）

```mermaid
graph LR
    subgraph 今天[W1 现状]
        T[TeamApproval 手写二级链<br/>team 模块状态机] --> TA[team_audit_log]
    end
    subgraph W3[波次 3 审批链迁移]
        T2[TeamApproval 单据语义保留] -->|流转驱动移交| F[Flowable 流程定义<br/>步骤=userTask 链]
        F --> TA2[audit_log 双写保持<br/>时间线口径不变]
    end
    subgraph W4[波次 4 agent 门]
        F2[userTask 候选组=成员池角色] -->|命中 confirm-gate| H[人工确认节点<br/>AGENT_CONFIRM_REQUIRED]
        A2[agent=sys_user 特殊类型<br/>作为 assignee/candidate] --> F2
    end
```

要点：
- W3 并行期双写零差异再切流；W4 复用治理基座"身份底座唯一"原则（agent 不建独立身份栈）。
- 文件（届时动）：`jeecg-boot/jeecg-boot-module/jeecg-module-team/src/main/java/org/jeecg/modules/team/service/impl/TeamApprovalServiceImpl.java`（现状锚点，`:submit` 落单号与步骤链处）；设计：`projects/agent-collab-pilot/docs/27-flowable-bpm-project-charter.md` §三

## 4. 观测性：traceId 全链 + 业务事件（今日落地）

```mermaid
sequenceDiagram
    participant R as HTTP 请求
    participant TF as TraceIdFilter<br/>(HIGHEST_PRECEDENCE+10)
    participant M as MDC
    participant SVC as team/mail Service
    participant EV as EventLoggers
    participant LB as logback STDOUT/FILE
    R->>TF: 无 X-Trace-Id 头 → 生成 UUID<br/>有 → 白名单校验(^[A-Za-z0-9_-]{1,64}$)
    TF->>M: put(traceId)；finally 仅清理自设值
    TF-->>R: 响应头回写 X-Trace-Id
    SVC->>EV: 业务事件(审批迁移/邮件收发删同步)
    EV->>M: 读 traceId 自动附加
    EV->>LB: 单行 JSON：ts/event/fields
    LB-->>LB: pattern [%X{traceId}] 前缀
```

事件清单（触发点=真实代码位置）：
- `approval.submitted / transition / validation_failed`：`jeecg-boot/jeecg-boot-module/jeecg-module-team/src/main/java/org/jeecg/modules/team/service/impl/TeamApprovalServiceImpl.java`（transition 含 8 处并发冲突拒绝负路径，action 复用 `TeamAuditAction.getCode()`）
- `mail.bind/unbind/sent/deleted`：`jeecg-boot/jeecg-boot-module/jeecg-module-mail/src/main/java/org/jeecg/modules/mail/service/impl/OaMailServiceImpl.java`（additive 单行事件，业务逻辑零改）
- `mail.sync`：`.../mail/sync/MailSyncEngine.java`（每轮同步结束；异步线程 traceId 串链待 tracker Redis 化后透传——P1 跟进）
- mail 发送异步 MDC 透传先例：`.../mail/service/impl/SystemMailServiceImpl.java`
- 审计双轨：`TeamAuditLogger` 入库轨一行未动，事件日志是技术/运营面。

## 5. 治理基座：派工闭环与漂移防线（今日 dogfooding 实证）

```mermaid
flowchart LR
    subgraph Policy[政策面（file-based 单源）]
        POOL[members/pool.yaml<br/>白名单唯一权威源]
        CAT[capability-catalog.yaml<br/>生成物·禁手改]
        CON[派工契约 yaml<br/>规则承载层]
    end
    subgraph Gate[门]
        LINT[contract_lint R1-R6]
        DRIFT[drift_check D1-D3]
    end
    subgraph Exec[执行面]
        SM["[secondary_model] 池<br/>=工具枚举权威源"]
        SP[成员 spawn<br/>k2.8 / k2.8-low / flash]
    end
    POOL --> LINT
    CON --> LINT
    CAT --> DRIFT
    SM --> DRIFT
    LINT -->|exit 0| SP
    DRIFT -->|max 默认档/目录外引用<br/>=报警| POOL
    SP -->|收工报告+台账+决策册| POOL
```

今日三案例（防线回写实录）：
1. K2.8/K3：政策引用池外别名 → 池收敛为白名单两项，工具层物理强制；
2. 思考档 max：`default_effort=max` 烧 token → 目录增思考档键 + D3 规则（max 禁默认档，**含 config 被其他运行时回退的防线**）；
3. display_name 直引 → R2 回归测试永久钉住。
- 文件：`projects/agent-collab-pilot/governance/src/capability_catalog.py`、`.../src/contract_lint.py`（R6 池绑定 `:277`）、`.../src/drift_check.py`（D3 `:74`）、`.../members/pool.yaml`、`.../contracts/_template.yaml`

## 6. 未变业务流指针（图见 DEFENSE 笔记，此处只留代码锚点）

- 任务状态机（六流转）+ 两道闸门：`jeecg-boot/jeecg-boot-module/jeecg-module-team/src/main/java/org/jeecg/modules/team/service/impl/TeamTaskServiceImpl.java`（changeStatus 链式校验）/ `.../service/impl/TeamOwnershipGuard.java`
- 审批二级链：`.../team/service/impl/TeamApprovalServiceImpl.java` + `.../service/impl/ApprovalFormValidator.java`
- 邮件发送状态机（PENDING→SENDING→SUCCESS/FAILED→DEAD）：`.../jeecg-module-mail/src/main/java/org/jeecg/modules/mail/enums/MailSendStatus.java`；同步引擎游标：`.../mail/sync/MailSyncEngine.java`
- 图：`DEFENSE-业务编排与答辩-2026-11-19.md` §2/§3（mermaid 状态机与时序原图）
