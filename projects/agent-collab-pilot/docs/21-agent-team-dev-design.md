# 21 — 开发文档：智能体团队技术方案

> 2026-11-17，Kimi Code 起草，juice094 待审定。依赖 docs/19（需求）、docs/20（资产盘点与接入方案）。
> 状态：**设计稿，未立项**；评审通过后按 docs/19 §六里程碑启动。本文档随立项细化。

## 一、总体架构

```
┌─ jeecg-team-web（业务模块新增「智能体」页）────────────────┐
│  AgentWorkspace.vue（档案/运行记录/确认中心）              │
│  api/agent.ts → /team/agent/**                             │
└──────────────┬─────────────────────────────────────────────┘
               │ HTTP + X-Access-Token（Shiro 鉴权）
┌─ jeecg-module-team（后端，本方案主施工区）─────────────────┐
│  org.jeecg.modules.team.agent                              │
│  ├── controller  TeamAgentController  /team/agent/**       │
│  ├── orchestrate 业务线编排器（每线一个 Spring 组件）      │
│  │     ApprovalPreOrchestrator（S1 审批预审）              │
│  │     TaskAssistOrchestrator（S2 任务辅助）               │
│  │     BugTriageOrchestrator（S3 Bug 分流）                │
│  │     NoticeQaOrchestrator（S4 公告问答，只读）           │
│  ├── service     TeamAgentService / TeamAgentRunService    │
│  └── entity/mapper  team_agent / team_agent_flow /         │
│                     team_agent_run（Flyway V3.9.6_4 起）   │
└──────┬───────────────────────┬─────────────────────────────┘
       │ 同 JVM 直注服务        │ 触发：业务事件 / Quartz 定时
┌──────┴───────────┐   ┌───────┴───────────────────────────┐
│ jeecg-module-team│   │ 复用 airag（docs/20 §3.2 直接复用档）│
│ team_task/approval│  │ chatSimple 应用（app_id 绑定）       │
│ /bug（加 assignee_│  │ airag_prompts 模板（{{variables}}）  │
│  type，ALTER 排队）│  │ airag_model / 知识库 RAG            │
└──────────────────┘   │ IAiragFlowApi.runFlow（仅分支/循环必需）│
                       └───────────────────────────────────────┘
```

**模块归属决策**：MVP 放 `jeecg-module-team` 的 `agent` 子包（少一个模块、与 team 表同事务、联调简单）；若 P6/P7（渠道接入/对外嵌入）使代码量膨胀（>3k 行）再拆 `jeecg-module-agent` 独立模块，拆分不改变包语义。

## 二、数据模型（Flyway 新脚本，编号顺延）

> 纪律：表必备 id/create_time/update_time；禁外键；数据订正先查后改；ALTER 既有表须与业务流转线（bizflow-dev 在途工作）排队，由宿统一排期。

### V3.9.6_5__team_agent.sql（新表）

> 编号说明：V3.9.6_4 已由业务流转线占用（team_audit_log 审计表，bizflow-dev 缺口清单 §4），本线从 _5 起，互不冲突。

```sql
team_agent (
  id varchar(36) pk,
  name varchar(64) not null,
  code varchar(64) not null unique,        -- 如 agent-approval-pre
  role_tag varchar(64),                     -- 角色标签
  capability_tags varchar(255),             -- 能力标签（逗号分隔）
  app_id varchar(36) not null,              -- → airag_app（chatSimple 型）
  prompt_template_id varchar(36),           -- → airag_prompts（可空=用应用自带 prompt）
  sys_user_id varchar(36),                  -- 智能体身份（特殊 sys_user，方案 A）
  status tinyint default 1,                 -- 1 启用 0 停用
  remark varchar(500),
  sys_tenant_id ...                          -- 沿用 jeecg 租户字段惯例
)

team_agent_flow (                            -- 流程线编排模板（配置驱动，非画布）
  id varchar(36) pk,
  code varchar(32) not null unique,         -- approval_pre/task_assist/bug_triage/notice_qa
  name varchar(64) not null,
  trigger_event varchar(64),                -- 触发事件码；空=仅手动/定时
  pipeline_json json,                       -- 节点模板：取数→提示词变量映射→确认策略→写回字段映射
  confirm_policy varchar(16) default 'ALWAYS',  -- ALWAYS/NEVER（写回业务表前是否人工确认）
  enabled tinyint default 1
)

team_agent_run (                             -- 运行实例与留痕（治理层核心表）
  id varchar(36) pk,
  agent_id varchar(36) not null,
  flow_code varchar(32) not null,
  biz_type varchar(32),                     -- approval/task/bug/notice
  biz_id varchar(36),                       -- 业务单 id
  status varchar(16) not null,              -- running/waiting_confirm/completed/failed/cancelled
  input_json json, output_json json,
  token_usage int, cost_ms bigint,
  confirm_by varchar(36), confirm_result varchar(16), confirm_note varchar(500),
  error text,
  create_time datetime, update_time datetime
)
```

### V3.9.6_6__team_task_ai.sql（ALTER，**与 bizflow-dev 排队**）

```sql
ALTER TABLE team_task ADD assignee_type varchar(8) default 'human';  -- human/ai
-- S2 任务线"指派给智能体"依赖此字段；状态机语义不变
```

## 三、接口设计（REST，`/team/agent/**`，Knife4j 注解随码同步）

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/team/agent/list` | 智能体档案分页 | 登录 |
| POST | `/team/agent/add` | 新建（同步创建/绑定特殊 sys_user） | 管理员 |
| PUT | `/team/agent/edit` | 编辑 | 管理员 |
| DELETE | `/team/agent/delete` | 删除（逻辑删） | 管理员 |
| POST | `/team/agent/{id}/toggle` | 启停 | 管理员 |
| GET | `/team/agent/run/list` | 运行记录分页（按 agent/flow/biz/status 过滤） | 登录 |
| GET | `/team/agent/run/{id}` | 运行详情（含 input/output 全文） | 登录 |
| POST | `/team/agent/run/{id}/confirm` | 人工确认（approve/reject + note）→ 继续/终止流程线 | 流程线对应角色 |
| POST | `/team/agent/flow/test` | 试点调试：手动触发某流程线 | 管理员 |

**不走 REST 的通路**（重要，避免接口面膨胀）：
- 业务事件触发 = 后端服务内直接调编排器（审批提交 Service 内发事件/直调），不暴露 HTTP 端点
- 定时场景（S5 日报）走 jeecg Quartz（JDBC 存储），任务回调编排器
- AI 执行 = 同 JVM 直注 airag 服务（单体模式），不走 `/airag/chat/send` HTTP 自环；微服务模式切换时改经 `IAiragFlowApi` Feign

## 四、编排实现（MVP：模板表 + 代码编排）

- 接口 `BizLineOrchestrator`：`boolean supports(String flowCode)` + `RunContext execute(RunContext ctx)`
- 每条业务线一个实现类，节点顺序固定为 docs/20 §3.3 数据流的六步；`team_agent_flow.pipeline_json` 只配置**变量映射、确认策略、写回字段映射**，不存可执行图——规避闭源引擎依赖（docs/20 §3.1）
- 运行实例状态机：`running → waiting_confirm → completed / failed / cancelled`；`reject` 不删数据，终态留痕
- 确认超时：Quartz 每分钟扫 `waiting_confirm` 超时单（默认 24h）→ `cancelled` + sys_message 提醒发起人

## 五、与既有模块的关系

| 对象 | 关系 | 说明 |
|---|---|---|
| airag | 复用 | app/prompts/model/knowledge 直引；chatFlow 仅包装；**不进闭源引擎** |
| team 三表 | 扩展 | 只加字段不改语义；S1 预审摘要写 team_approval 扩展列（V3.9.6_7 随做）；业务侧审计快照走业务流转线的 team_audit_log（V3.9.6_4），与本线 run 表互补不重复 |
| sys_user | 复用 | 智能体身份 = 特殊账号（禁登录标记、禁前台菜单、密码随机） |
| sys_message | 复用 | 确认请求/催办/失败通知的出口 |
| Quartz | 复用 | S5 日报 + 确认超时清扫 |
| M3 审计日志 | 衔接 | run 表即 AI 侧留痕；操作审计动作类型扩展 `agent_run/confirm` |

## 六、安全设计

1. Shiro：`/team/agent/**` 全部鉴权（admin 操作走按钮级权限 `team:agent:*`）；匿名 shareToken 机制不复用到本模块
2. 智能体身份：特殊 sys_user 打标（如 user_type='AGENT' 或 remark 约定 + 登录拦截），**禁人工登录**；其数据权限 = 角色权限，运行身份仍记真实触发人
3. 提示词注入：业务数据注入前做长度截断/转义；AI 输出写回前校验枚举合法性（如 Bug 分类必须在字典内）
4. 密钥：airag_model.credential 明文债列入 M4-α 前处置项（docs/20 §3.5）
5. 成本护栏：`team_agent_flow` 预留单线日预算字段（MVP 只记 token_usage，超限告警不熔断）

## 七、前端落点（team-web）

- 按前端盘点结论的最小路径：`src/router/index.ts` 加一条路由（tools 分组，如 `agent/workspace`），侧栏自动派生，`MENU_ICONS` 补 Phosphor 图标
- 视图：`src/modules/business/views/AgentWorkspace.vue`（档案列表 + 运行记录 tab + 确认中心 tab）
- API：`src/api/agent.ts`，复用 `utils/http.ts` 契约
- 聊天式交互（P6）后置；MVP 只做管理页，问答入口复用 airag 现有 chat 页 iframe 嵌入

## 八、测试与验收

- 单测（`mvn test -DskipTests=false -pl jeecg-boot/jeecg-boot-module/jeecg-module-team`）：run 状态机迁移、确认分支、变量映射、写回校验；编排器 mock airag 层（验证边界：mock 的是 airag 服务接口，真实 langchain4j 调用链归集成验证）
- 集成验证（宿主侧）：chatSimple 应用真实调用一次 S4 问答（需 airag_model 配真实 key，本机 8081）
- 验收三问（docs/13）：文档齐（Knife4j+Apifox 重导）/ VM 可复现 / 团队会用

## 九、风险与协调项

| 风险 | 处置 |
|---|---|
| 闭源流程引擎卡需求 | MVP 不依赖引擎（§四）；确需画布编排时评审自研或等上游 |
| team 表 ALTER 与 bizflow-dev 在途工作冲突 | V3.9.6_5/6 排队，宿统一排期；编排代码只依赖新字段存在性 |
| token 成本 | run 表先记账；月度复盘后再定预算熔断 |
| 智能体身份方案 A 的登录面风险 | 登录拦截 + Shiro 排除路径审计，纳入验收 |

---

*文档变更记录：2026-11-17 首版。*
