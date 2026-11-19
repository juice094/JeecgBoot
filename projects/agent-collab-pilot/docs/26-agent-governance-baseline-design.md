# 26 · Agent 治理基座设计稿（GOVERNANCE-BASELINE）

> 状态：设计稿 v1.1（2026-11-19，v1.1 修正：身份底座统一，agent 增量收敛为四件）
> v1 → v1.1 变更：M4 由"agent 确权镜像（并排第二套）"修正为"同一身份底座 + agent 四增量"；
> 依据：宿问"人类身份治理面能否基于已有成熟体系承载"——能，Jeecg IAM 即在役承载层（service account 模式）。
> 上游：`21-agent-team-dev-design.md`（团队机制）、`23-business-base-contract.md`（业务契约）
> 问题来源：2026-11-19 K2.8/K3 委派漂移事件 + workspace-kimicode 经验库卫生审查实证

## 一、定位

**控制面治理子系统**，横切 agent 编排面与 Jeecg 业务面，与两者解耦：

```
┌─────────────────────────── 治理基座（本设计） ───────────────────────────┐
│ ① 规则分层单源   ② 能力目录   ③ 派工契约   ④ agent 确权   ⑤ 漂移检测      │
│            身份 → 角色 → 确权 → 审计（与 /team/** 同一套词汇）              │
└──────────────┬──────────────────────────────┬───────────────────────────┘
               │ 注入/校验                     │ 同一词汇映射
┌──────────────▼───────────────┐  ┌───────────▼────────────┐
│ agent 编排面（Kimi Code/dsh） │  │ Jeecg 业务面（在役）      │
│ 成员池 / 子代理 / 派工       │  │ Shiro 按钮权限(授权)      │
│                              │  │ OwnershipGuard(确权)    │
│                              │  │ team_audit_log(审计)    │
└──────────────────────────────┘  └────────────────────────┘
```

设计约束（军规级）：零新依赖 · file-based · 单人可运维 · 每层单一权威源。

**身份底座唯一原则（v1.1 新增）**：人类与 agent 共用 Jeecg IAM（sys_user 体系）作为唯一身份底座，
principal 分「人类 / agent」两类，**agent 不拥有独立身份栈**。agent 侧只新增四类增量（见 §3.3），
其余认证/授权/确权/租户/审计/生命周期全部白嫖在役机制。模式学名：service account / bot account
（NHI 产品挂 IdP 而非替换 IdP；GitHub App = org 内 bot 类型 actor，同一道理）。

## 二、模块划分与成熟度决策

| 模块 | 经典模式 | 实现决策 | 理由 |
|---|---|---|---|
| M1 规则分层 | Constitution / Layered Config | **自建**：T0 用户级 / T1 项目级 / T2 工作区级，注入点登记 | AGENTS.md 已是社区事实标准；无产品可买 |
| M2 能力目录 | Service Registry | **自建**：`capability-catalog` 从 config.toml 扫描生成 | 防 K2.8/K3 类"政策引用目录外名字"漂移；Backstage 过重 |
| M3 派工契约 | 能力衰减令牌（Biscuit 思想） | **自建 yaml 头**：角色/模型/资源范围/验证分级/红线/确认门 | 无成熟标准；借鉴衰减思想=契约权限只减不增 |
| M4 Principal 统一与 agent 增量 | Service account 模式（同一底座，分类型 principal） | **半自建**：承载=Jeecg IAM（sys_user 类型判别 + AK/SK 凭证位）；增量=委托链字段/范围衰减/确认门/凭证轮换，四件清单见 §3.3 | 身份栈零复制；人类面机制全部自动继承（角色 UI/租户/审计/冻结吊销） |
| M5 审计 | Append-only | **复用**：workspace 台账/memory + Jeecg audit_log 双留痕，格式对齐 | 不新建第三套 |
| M6 漂移检测 | Policy-as-Code | **复用推广**：health-check 模式泛化（政策↔目录、索引↔文件） | 已在役实证（collab-health-check PASS） |
| M7 经验沉淀 | Reflexion / Memory Hierarchy | **维持现状**：手工精修 + 编号体系，不引入 MemGPT/mem0 | 论文多于产品，替换成本>收益 |

明确不做：不上 SPIFFE/SPIRE（单机无 workload 面）、不上 OPA（Rego 学习税）、不接 A2A/MCP 标准（单人无互操作诉求）、不做经验自动捕获（噪音>价值）。

## 三、核心设计

### 3.1 M2 能力目录（治 K2.8/K3 类漂移）

```yaml
# capability-catalog.yaml —— 由 capability-catalog.py 从 config.toml 生成，禁手改
generated: 2026-11-19
models:
  - id: kimi-code/kimi-for-coding        # 政策只允许引用 id，禁止引用 display_name
    display: K2.8 Preview
    provider: managed:kimi-code
    context: 1048576
  - id: deepseek/deepseek-flash
    display: DeepSeek-V4.1-Flash
    ...
```

**漂移即缺陷**：政策文件中出现目录外的模型名 = checker P1（同"孤儿报告"一个级别）。今天 K2.8/K3 事件的根因就是政策写在工作区层而目录没同步到委派工具——目录生成后此面闭合。

### 3.2 M3 派工契约（治规则退化/不可复用）

```yaml
# dispatch-contract.yaml —— 每次派工的头，成员池成员持长期契约
contract: "AGT-2026-0001"
member: k2.8                    # 池成员=长期身份，resume 优先于新开
model: kimi-code/kimi-for-coding # 必须 ∈ capability-catalog
scope:                          # 委派范围，只减不增（Biscuit 衰减思想）
  repos: [jeecg-team-web]
  write: [src/**, docs/**]
  deny: [".env", "src-tauri/target/**"]
verification: L1-typecheck-L2-build   # 验证分级，报告什么级说什么级
confirm-gate: [git-push, credential-touch]  # 高敏操作 → AGENT_CONFIRM_REQUIRED
red-lines: [no-rm-rf, recycle-bin, no-force-push]
carry: [T0, T1]                  # 本契约注入哪些规则层
```

**承载层语义**：规则跟着契约走，不跟着会话走。子代理冷启动拿到的不是散文 prompt 而是这份契约 + 对应规则层。

### 3.3 统一底座 + agent 四增量（v1.1 重写）

**承载层（人类面权威，零改动）**：Jeecg IAM——sys_user/sys_role/Shiro/多租户/
数据权限/team_audit_log/HrManage 生命周期。agent 注册为 sys_user 的一个类型
（类型判别字段 + AK/SK 凭证位，替代密码），走**同一个角色管理界面**授 `team_agent` 角色。

| 治理面 | 承载（白嫖） | agent 增量（新建，仅四件） |
|---|---|---|
| 认证 | X-Access-Token 机制 | ① 凭证形态：AK/SK 或短时效 token，挂 jeecg 既有 OpenAPI 签名机制 |
| 授权 | 角色勾选按钮权限（同一 UI） | 无增量——`team_agent` 最小权限矩阵照勾选 |
| 确权 | OwnershipGuard（单据归属） | 无增量——agent 作为 assignee 被现有闸门约束 |
| 委托链 | — | ② 审计字段：`operator=agent 主体 + on_behalf_of=人类`（on-behalf-of 语义，只需字段不上 OAuth） |
| 范围 | 角色粒度 | ③ 衰减：编排侧契约 scope 只减不增（§3.2），服务端以角色粒度对齐 |
| 高敏确认 | （预留）AGENT_CONFIRM_REQUIRED | ④ 确认门启用：契约 confirm-gate 触发人类一字确认 |
| 审计/生命周期 | team_audit_log / HrManage 冻结删除 | 无增量——吊销=冻结 sys_user，成熟机制直接复用 |

**验收口径**：任一派工可追溯"谁（member）·凭什么（scope）·动了什么（write 面）·谁批的（confirm-gate）"；
任一 agent 操作可追溯"哪个 agent 主体 · 代表谁 · 经哪道确认门"。

### 3.4 M6 漂移检测（health-check 泛化）

现有 collab-health-check 已实证模式（C1 登记/C2 编号/门禁三层）。泛化两条：

- **政策↔目录**：扫描所有政策文件（AGENTS.md/契约/计划），引用的模型/工具/路径必须在 capability-catalog 或存在性校验通过；
- **注入点↔分层**：每个 agent 会话类型（主会话/子代理/池成员）声明加载哪些层，未声明=漂移。

## 四、与既有资产的关系

- **workspace-kimicode**：本设计的在役试验场（经验库/health-check/台账已运转）；M6 直接复用其 checker 骨架。
- **jeecg-module-team**：确权词汇的权威源；agent 注册进 sys_user 时角色矩阵照抄 §7.3 已拍板口径。
- **dsh-experimental-agent-team-profile**：本基座的远期载体——成员池落地后，profile 里装的是契约 + 目录，不是散文。

## 五、施工序（一周）

| 日 | 内容 | 验收 |
|---|---|---|
| D1 | capability-catalog.py（扫 config.toml 生成目录）+ 政策文件首次扫描 | K2.8/K3 类引用全部可解析 |
| D2-3 | 派工契约模板 + 成员池登记（白名单两成员）+ 一个真实派工走契约 | 契约承载规则，子代理可复用 |
| D4 | 漂移 checker 两条新规（政策↔目录、注入点↔分层）挂 health-check | 故意制造漂移能被抓住 |
| D5 | agent 注册字段设计（sys_user 类型判别 + AK/SK 凭证位 DDL 草案）+ 四增量清单落册 + 设计稿复审 | 答辩/中期可直接引用本文档；DDL 草案可进 flyway 取号队列 |

## 六、风险与开放问题

1. **契约粒度**：scope 按路径还是按操作动词？先用路径（实现 1 天）演进时再加动词（借鉴 ABAC）。
2. **目录唯一源**：config.toml 是 Kimi Code 私有格式，dsh 侧模型从哪扫？——待 dsh 接入时再定，目录 schema 先留 provider 字段。
3. **单人偏置**：所有"成员"都是同一人的分身，确权的社会意义弱于团队场景——矩阵先按团队规格设计，防将来返工。
