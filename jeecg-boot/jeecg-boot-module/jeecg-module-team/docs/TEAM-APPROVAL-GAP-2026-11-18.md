# 团队协作-审批（申请表）功能与表结构差分报告（2026-11-18）

> 对照前序沟通确定的申请表框架（表单/模板/流程/协同/通知五域 + 六个流转方面），对当前 `team_approval` 单链路实现做完整差分，并给出 P0 补全动作。本报告为一次性文件，后续变更请另起文。

## 1. 现状判定

当前申请人链路只覆盖：**提交 → 客户端传入 steps → 按 stepNo 线性依次审批 → 全通过 approved / 任一拒绝终态 rejected**。仅命中框架中的四点：发起填单（部分）、依次审批、拒绝终止、留痕。

- 相关文件：`TeamApprovalController`、`TeamApprovalServiceImpl`、`ApprovalTodoMatcher`、`TeamApproval`/`TeamApprovalStep` 实体、`team_audit_log`。
- 工作区内未见 `src/main/resources`、SQL/DDL 脚本、邮件依赖与邮件实现，说明表结构仅由实体注解表达、通知体系完全缺位。

## 2. 表结构差分

### 2.1 表单与模板域（最大缺口）

| 应然 | 现状 | 差分 | 优先级 |
|---|---|---|---|
| `team_approval_template` | 无，`category` 是自由文本 | 无法按类别预置审批链 | P0 |
| `team_approval_template_step` | 无 | 同上 | P0 |
| `form_fields`/EAV 或 JSON 扩展列 | 无，仅 title/reason/startDate/endDate 四固定列 | 报销金额、出差地点无处存 | P1 |
| 类别字典 | 无，仅注释举例 | 统计口径不可控 | P1 |

### 2.2 流程实例域

| 应然 | 现状 | 差分 | 优先级 |
|---|---|---|---|
| 状态枚举类 | **无**（`TeamTaskStatus`/`TeamBugStatus` 都有，审批没有） | 字符串散落 ServiceImpl | P1 |
| 状态集合 | 仅 pending/approved/rejected | 缺 draft/returned/canceled | P0 |
| 审批链来源 | 客户端全量传 `steps` | 可被发起人伪造 | P0 |
| 条件分支表达式 | 无 | 分级审批做不了 | P2 |

### 2.3 审批动作域（`team_approval_step`）

| 应然 | 现状 | 差分 | 优先级 |
|---|---|---|---|
| `approver_id` | 只有 `approver_name` | 重名串单、改名失效、越权校验不可靠 | P0 |
| `sign_type`（会签/或签） | 无，硬编码依次 | 无法多人共审 | P1 |
| `step_type`（审批/抄送/办理） | 无 | 抄送无处承载 | P1 |
| 退回标记 | 无 | 只能 reject 终态 | P0 |

### 2.4 协同域

| 应然 | 现状 | 差分 | 优先级 |
|---|---|---|---|
| 抄送表 | 无 | 知会不可用 | P1 |
| 附件表 | 无 | 报销/出差硬伤 | P1 |
| 评论表 | 无（task 有 comment，approval 没有） | 审批沟通只能塞 comment 单字段 | P1 |
| 委托代审表 | 无 | 出差期间无人可审 | P2 |

### 2.5 通知域（邮件）

| 应然 | 现状 | 差分 | 优先级 |
|---|---|---|---|
| `spring-boot-starter-mail` | **无**（pom 仅 base-core + jaxb） | 发不了信 | P1 |
| `mail_template` | 无 | — | P1 |
| `mail_outbox`（bizType/bizId/收件人/状态/重试次数） | 无 | 无投递可观测性 | P1 |
| 异步发送器 + 事件钩子 | 无 | 无任何通知 | P1 |
| 入站审批（IMAP/Webhook + reply token + 白名单） | 无 | — | P2 |

## 3. 流程流转差分（六个方面）

| 方面 | 已有 | 缺失 |
|---|---|---|
| 发起与填单 | 标题非空、起止日期先后校验 | 草稿、暂存、类别-字段联动校验 |
| 节点分配 | 姓名写死 | 模板化（按 category / 角色 / 职级 / 上级）、`approver_id` |
| 多人决策 | 仅依次 | 会签、或签、比例会签、并行汇聚 |
| 路由分支 | 无 | 条件网关（金额/天数/部门）、并行网关 |
| 异常与干预 | reject 终态 | 撤回、退回重提、转办、加签、委托、催办、超时 |
| 权限与归档 | `team:approval:*` 按钮权限、审计留痕 | 字段级可见权限、抄送权限、归档回写 |

## 4. 具体缺陷（直接影响试点可信度）

1. **审批链可被发起人伪造（P0）**：`steps` 全量来自请求体，服务端既不查模板也不校验顺序；叠加“自审在 approve 阶段才拦”，会导致单据提交后卡死（如 steps=[张三, 自己]）。
2. **按姓名匹配审批人（P0）**：`todo` 与越权校验都走 `approverName.equals(realname)`，重名互串、改名后待办丢失。
3. **拒绝即终态（P0）**：现实里“材料不全退回补充”是最高频动作，当前只能走 reject 并重新建单，单号与审计链断裂。
4. **单号并发重号（P1）**：`count+1` 生成 `OA-YYYYMMDD-NNN`，并发提交必冲突。
5. **todo 内存分页（P1）**：先全量捞 `action=wait` 的步骤再内存切片，数据量上来后线性退化。
6. **并发双推（P1）**：`action=wait` 检查与 `updateById` 之间存在竞态，无 `version` 或条件更新兜底。
7. **无 DDL 入库（P1）**：工作区无 SQL 脚本，`idx_step_approval` 只存在于注释，多环境易漂移。
8. **状态无枚举（P2）**：与 task/bug 的枚举风格不一致，拼写错误只能运行时暴露。

## 5. 补全分期与本次落地点

**P0（本次落地）**
- 新增 `TeamApprovalStatus` 枚举：`draft/pending/returned/approved/rejected/canceled`，含 `isTerminal()` 与 `canTransitTo()`。
- 新增 `team_approval_template` / `team_approval_template_step` 两张模板表 + 实体 + Mapper + Service；`submit` 改为模板优先（按 `category` 取），仅当模板缺失时回退到客户端 steps（兼容既有调用）。
- `team_approval_step` 增 `approver_id`；越权校验与 `todo` 查询优先按 ID，缺失时姓名兜底。
- 新增端点：`/team/approval/cancel`（申请人撤回，仅 pending 且未被操作）、`/team/approval/return`（审批人退回至申请人，状态 returned）、`/team/approval/resubmit`（申请人重提，重置步骤链，状态回 pending）。
- `submit` 校验：审批链中不得包含申请人本人（按 ID，缺失按姓名）。
- 关键写操作改**条件更新**（`WHERE action='wait'` / `WHERE status=... AND current_step=...`），消除并发双推。
- 落盘 DDL：`docs/sql/V3.9.6_10__team_approval_v2.draft.sql`（DRAFT，待 lead 取号后移入 `jeecg-system-start/src/main/resources/flyway/sql/mysql/`）。

**P1（下一阶段）**
- 会签/或签（`sign_type`）、抄送表、附件表、审批评论表
- 转办/加签、催办与超时策略字段
- 邮件**出站**：依赖 + `mail_template` + `mail_outbox` + 异步发送器，挂到 submit/approve/reject/催办钩子
- 单号改 Redis 发号或数据库号段
- `todo` 下沉到 SQL

**P2（完备性）**
- 并行网关、条件分支、委托代审、字段级权限、EAV/JSON 扩展字段、入站邮件审批（reply token + 发件人白名单 + 原文归档）

## 6. 验证与残余风险

> 契约层面的差异与升版口径，见 `docs/TEAM-APPROVAL-PATCH-2026-11-18.md`。

### 已验证

- 编译：在模块目录执行 `mvn -o -q test-compile`，exit 0。
  （注：在仓库根执行 `-pl jeecg-module-team` 会失败——工作区根不是 Maven reactor 根，必须在模块目录内直接编译。）
- 纯 Java 自测（带完整依赖类路径）：
  - `TeamApprovalStatusSelfTest`：状态迁移矩阵、终态判定、`fromCode` 空值安全，全部通过。
  - `ApprovalTodoMatcherSelfTest`（既有）：7 条断言通过，未被本次改动破坏。
- 自测运行方式（Windows）：`java -cp "target/classes;target/test-classes;$(cat target/cp.txt)" <主类>`。
  直接用 `target/classes:target/test-classes` 会因缺 `JeecgEntity` 报 `NoClassDefFoundError`——这是类路径问题，不是代码问题。

### 未覆盖

- **真实数据库下的 DDL 执行**：`docs/sql/V3.9.6_10__team_approval_v2.draft.sql` 未在 MySQL 上跑过，`ALTER TABLE` 与索引创建的成功性未验证。
- ~~`sys_org_code` 列待核实~~ **已核实**：`JeecgEntity` 基类确实不含 `sysOrgCode`，但**模块 DDL 约定所有 team 表都带 `sys_org_code` / `sys_tenant_id` 两列**（依据 `jeecg-team-web/docs/sql-schema.md` 对 `team_task`、`team_audit_log` 的记载）。实体类不声明这两列是既有约定（`TeamTask` 同样不声明），由平台侧填充。**我此前据基类字段删除该列是误判，已恢复**，见 `docs/sql/V3.9.6_10__team_approval_v2.draft.sql`。
- **实体不映射平台列**：`TeamApprovalTemplate` / `TeamApprovalTemplateStep` 不声明 `sysOrgCode` / `sysTenantId`，与 `TeamTask`、`TeamApproval` 的既有写法一致；DDL 有列、实体不映射，不影响读写。
- **Spring 上下文集成**：新增的 `TeamApprovalTemplateServiceImpl`、两个 Mapper 未在真实 Spring 容器内启动验证；按 `@MapperScan("org.jeecg.modules.team.mapper")` 的既有扫描范围推断可被注册，但未实测。
- **HTTP 层鉴权与序列化**：新增 `/return`、`/cancel`、`/resubmit` 三个端点的权限注解复用了既有 `team:approval:approve` / `team:approval:submit`，未跑通请求。
- **邮件链路**：本次未引入任何邮件依赖或实现。

### 兼容性与未处理项

- `team_approval_step.approver_id` 可空：历史行按 `approver_name` 兜底，新旧数据可共存。
- `submit` 在模板缺失时仍接受客户端 `steps`，既有前端调用不会立即中断。
- 未处理（P1）：`team_approval.code` 单号并发生成；`todo` 内存分页；`sign_type` 仅落库未生效（本期一律按 SEQ 依次执行）。
