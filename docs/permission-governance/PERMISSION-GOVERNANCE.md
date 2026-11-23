# 权限确权治理工程文档

> 版本：v0.2（设计稿，待评审）
> 基于：v0.1（deepseek 落点稿）+ 2026-11-19 代码复核修订
> 适用范围：`jeecg-boot` 后端（`jeecg-boot-base-core`、`jeecg-module-system`、`jeecg-boot-module/*`）
> 依据：本文所有"现状"结论均来自当前分支 `pilot/w1-trim` 的实际代码，引用处以 `路径:行号` 标注；v0.1 的 §12 待确认清单已在 §12 逐条核实并给出结论。

---

## 0. v0.2 修订摘要

| # | 修订 | 性质 |
|---|---|---|
| R1 | §8 鉴权读路径落点由"改 `ShiroRealm`"改为"改 `commonApi.queryUserAuths` 的实现" | 收窄改动面：base-core 零改动，单体/微服务两模式同时生效 |
| R2 | §6.2 副作用补入**授权缓存失效**，§14 增加对应验收项 | 补漏：只改装载路径而不清缓存，回收在缓存过期前不生效 |
| R3 | §12 四条待确认已核实，回填结论；Flyway 下一取号为 `V3.9.6_12` | 消歧 |
| R4 | §9 方案 A 的拦截器挂载点更正为 `JeecgTenantParser`（原猜 `TenantHandler` 不存在） | 纠错 |

---

## 1. 文档定位

本文不讨论"要不要做权限"，而是回答一个更窄的问题：

**当权限本身成为可以被申请、被审批、被临时授予、被自动回收、被追溯的对象时，它在现有 JeecgBoot 代码结构里应该落在哪一层、以什么数据形态存在、从哪几个入口接入。**

对应四条已识别的缺口：

| 编号 | 缺口 | 本质 |
|---|---|---|
| G1 | 授权即生效，无确认环节 | 授权关系没有生命周期，写入即事实 |
| G2 | 无权限时效 | 授权关系没有时间维度 |
| G3 | 数据权限靠注解自觉 | 默认姿态是 fail-open |
| G4 | 审计在记录但不在闭环 | 变更是日志，不是事件 |

---

## 2. 术语校正

- **认证**：你是谁。由 Shiro + JWT 承担。
- **授权**：你被授予了什么。落在 `sys_role_permission`、`sys_permission`、`sys_permission_data_rule`。
- **鉴权**：这次请求放不放行。由 Shiro 的 Realm/Authorizer 链路承担。
- **确权**：对"谁有权授予、依据什么、多长时间、什么范围、何时收回"的持续确认。**当前完全没有这一层。**

> 框架定位：本仓库鉴权框架是 **Apache Shiro 3.0.0**（`jakarta` 命名空间，Redis 会话），非 Spring Security。Shiro 的 `Authorizer` 是一次性权限集合匹配，**不具备授权生命周期能力**——需要在其之上补治理层，而非替换框架。

---

## 3. 现状与代码证据（v0.2 复核通过）

### G1 授权即生效

```
jeecg-module-system/jeecg-system-biz/.../controller/SysPermissionController.java:602
    this.sysRolePermissionService.saveRolePermission(roleId, permissionIds, lastPermissionIds);
```

该处仅追加 `addLog` 业务日志，无审批、无状态。实体 `SysRolePermission` 字段为 `id / roleId / permissionId / dataRuleIds / operateDate / operateIp`，**无状态字段**。

物理删除路径：`SysRoleMapper.java:68` 的 `@Delete("delete from sys_role_permission where role_id = #{roleId}")`——删除即消失，无留痕、无通知。

### G2 无权限时效

同上字段清单无 `valid_from` / `valid_until`。`operate_date` 是操作时间，不是有效期。

### G3 数据权限 fail-open

切点（`PermissionDataAspect.java:45`）：`@Pointcut("@annotation(...PermissionData)")`——未标注的方法不进切面。
切面体（`PermissionDataAspect.java:73`）：`if(dataRules != null && dataRules.size() > 0)`——规则为空不安装条件。

**双重 fail-open**：忘加注解 → 裸奔；加了注解但未配规则 → 同样裸奔。

### G4 审计不成闭环

- 操作日志：`AutoLogAspect` → `sys_log`，记录"发生了什么"。
- 团队审计：`TeamAuditAction`（9 种动作）、`TeamAuditBizType`（仅 TASK/APPROVAL/BUG）。
- 权限变更不属于任何已有业务类型，不进审计链路；亦无"通知 → 申诉 → 回收"回路。

---

## 4. 目标架构

```
┌─────────────────────────────────────────────┐
│ 接入层    Shiro Filter / JWT / 会话          │  不改
├─────────────────────────────────────────────┤
│ 鉴权层    ShiroRealm → queryUserAuths        │  改读路径（§8，改实现不改 Realm）
├─────────────────────────────────────────────┤
│ 治理层    sys_permission_grant 生命周期       │  ★ 新增（§5-§7）
│           申请/审批/生效/过期/回收/通知       │
├─────────────────────────────────────────────┤
│ 数据层    数据权限拦截（fail-closed）         │  改默认姿态（§9）
└─────────────────────────────────────────────┘
```

### 4.1 关键架构决策

**D1：枚举与状态机抽象放 `jeecg-boot-base-core`，实体/Mapper/Service/Controller 放 `jeecg-system-biz`，team 模块不参与。**

依据：依赖方向为 `team → base-core`（`TeamApprovalServiceImpl` 导入 `org.jeecg.common.*`）。若治理层放 team 则 system 反向依赖业务模块，形成环。

**D2：复用语义，不复用表。**

`TeamApprovalStatus` 表达业务单据状态，权限授予是治理对象，终态语义不同（`approved` 之后还有 `active` → `expired` / `revoked`）。新建独立枚举 `PermissionGrantStatus`，继承同一套流转纪律（显式迁移表、终态不可变、CAS 条件更新），不共用表与枚举。

**D3：CAS 更新模式照搬 team 范式。**

以 `TeamApprovalServiceImpl.java:242-254` 为范式：`eq(旧状态) + 影响行数为 0 即抛 JeecgBootException`，禁止 `updateById` 盲写。

---

## 5. 数据模型

### 5.1 授权关系主表

```sql
CREATE TABLE sys_permission_grant (
  id              varchar(32)   NOT NULL COMMENT '主键(雪花)',
  grant_no        varchar(64)   NOT NULL COMMENT '授权单号',
  subject_type    varchar(16)   NOT NULL COMMENT '主体类型: USER/ROLE/DEPT',
  subject_id      varchar(32)   NOT NULL COMMENT '主体ID',
  permission_id   varchar(32)   NOT NULL COMMENT '权限ID(sys_permission)',
  data_rule_ids   varchar(1000) DEFAULT NULL COMMENT '数据规则ID串',
  scope_type      varchar(16)   NOT NULL DEFAULT 'SELF' COMMENT 'SELF/DEPT/DEPT_AND_CHILD/GLOBAL',
  status          varchar(16)   NOT NULL COMMENT 'draft/pending/returned/approved/rejected/canceled/active/expired/revoked',
  valid_from      datetime      DEFAULT NULL COMMENT '生效起点',
  valid_until     datetime      DEFAULT NULL COMMENT '生效终点(NULL=长期)',
  apply_reason    varchar(500)  DEFAULT NULL COMMENT '申请理由(必填)',
  applicant       varchar(32)   DEFAULT NULL COMMENT '申请人',
  approver        varchar(32)   DEFAULT NULL COMMENT '末次审批人',
  current_step    int           DEFAULT 1 COMMENT '当前审批步骤',
  source          varchar(16)   NOT NULL DEFAULT 'APPLY' COMMENT 'APPLY/TEMPLATE/MIGRATION',
  ext_note        varchar(500)  DEFAULT NULL,
  create_time     datetime      DEFAULT NULL,
  create_by       varchar(32)   DEFAULT NULL,
  update_time     datetime      DEFAULT NULL,
  update_by       varchar(32)   DEFAULT NULL,
  deleted         tinyint       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_grant_no (grant_no),
  KEY idx_subject_status (subject_type, subject_id, status),
  KEY idx_expire (status, valid_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限授予关系(确权主体)';
```

### 5.2 审批步骤表

```sql
CREATE TABLE sys_permission_grant_step (
  id          varchar(32)  NOT NULL,
  grant_id    varchar(32)  NOT NULL,
  step_no     int          NOT NULL,
  approver    varchar(32)  DEFAULT NULL,
  action      varchar(16)  DEFAULT NULL COMMENT 'wait/approve/reject/return',
  comment     varchar(500) DEFAULT NULL,
  acted_at    datetime     DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_grant (grant_id, step_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限授予审批步骤';
```

### 5.3 与存量表的关系（分期）

| 阶段 | `sys_role_permission` | `sys_permission_grant` |
|---|---|---|
| P0 | 保持只读基线，入口不变 | 新申请走此表 |
| P1 | 存量行回填为 `source='MIGRATION'`、`status='active'`、`valid_until=NULL` | 成为唯一可写授权源 |
| P2 | 降级为物化视图 / 归档 | 唯一事实来源 |

**鉴权读路径在 P1 起必须同时查两张表并取并集**，避免切换期权限丢失。

---

## 6. 状态机契约

### 6.1 状态集合

```java
DRAFT / PENDING / RETURNED / APPROVED / REJECTED / CANCELED   // 前六态与 TeamApprovalStatus 同构
ACTIVE / EXPIRED / REVOKED                                     // 治理层特有
```

前六态的 `canTransitTo()` 规则必须与 `TeamApprovalStatus` 完全一致。

### 6.2 迁移表（唯一合法路径）

| 从 | 到 | 触发动作 | 副作用 |
|---|---|---|---|
| DRAFT | PENDING | submit | 写 step 1，发 `grant.submitted` |
| PENDING | RETURNED | return | 记录 comment |
| PENDING | REJECTED | reject（任一步） | 终态 |
| PENDING | PENDING | approve（非末步） | `current_step++`，不迁移状态 |
| PENDING | APPROVED | approve（末步） | 写入 `valid_from`/`valid_until` |
| RETURNED | PENDING | resubmit | `current_step=1` |
| APPROVED | ACTIVE | 系统生效（立即或到期） | **清 subject 授权缓存**，发 `grant.activated` |
| ACTIVE | EXPIRED | 读时判定 / 扫描落库 | **清 subject 授权缓存**，发 `grant.expired` |
| ACTIVE | REVOKED | `/revoke` | **清 subject 授权缓存**，发 `grant.revoked` |
| DRAFT/PENDING/RETURNED | CANCELED | cancel | 终态 |

> **R2 补入**：凡是迁移导致"鉴权侧可见性变化"的边，副作用必须包含清除 target subject 的授权缓存。Jeecg 的 `queryUserAuths` 结果存在 Shiro/Redis 缓存中，仅改装载 SQL 而不清缓存，回收在缓存过期前不生效。

### 6.3 硬约束

1. `APPROVED → ACTIVE` 由系统完成，不允许人工直写；
2. `EXPIRED` / `REVOKED` / `REJECTED` / `CANCELED` 为终态，不可复活，需重新申请；
3. `valid_until` 到期判定发生在**读路径**（§8），后台扫描仅做状态落库与通知；
4. 任何迁移用 `eq(旧状态)` 条件更新，影响行数为 0 抛 `JeecgBootException`；
5. `sys_permission_grant` 禁止物理删除，`/revoke` 为唯一回收路径。

---

## 7. 接口契约

统一前缀 `/sys/permissionGrant`，返回 `Result<T>`。

| 方法 | 路径 | 说明 | 关键校验 |
|---|---|---|---|
| POST | `/apply` | 提交申请，落 `PENDING` | `apply_reason` 非空；`valid_until > valid_from`；`scope_type` 合法 |
| POST | `/approve` | 审批通过 | 当前用户是 `current_step` 的审批人 |
| POST | `/reject` | 拒绝 | 同上 |
| POST | `/return` | 退回 | 同上 |
| POST | `/resubmit` | 重提 | 状态为 `RETURNED` |
| POST | `/cancel` | 撤销 | 申请人是当前用户 |
| POST | `/revoke` | 回收已生效授权 | 需回收权限；必填 `comment` |
| GET | `/page` | 分页查询 | 默认只查本人可见范围 |
| GET | `/my` | 我收到/我发起的 | — |

`/revoke` 是 G4 闭环的关键出口：权限回收是一等操作，不是删表。

---

## 8. 鉴权读路径改造（G1 + G2 的落点）

### 8.1 落点（R1 修订）

不在 `ShiroRealm` 改，**改 `commonApi.queryUserAuths(userId)` 的实现链**：

- `ShiroRealm.java:66` 的 `doGetAuthorizationInfo` 调用 `commonApi.queryUserAuths(userId)`（第 83 行）；
- 单体模式下 `SysBaseApiImpl.queryUserAuths` 委托给 **`SysBaseApiImpl.getUserPermissionSet`**（实测：`SysUserServiceImpl.getUserPermissionsSet` 是另一条路径，AI 模块在用，两处均已接入同一开关）；
- 改一处即两模式同时生效，且 base-core 零改动。

> 实测更正：单体模式 `/sys/api/**` 因 `SystemApiController` 的 `@ConditionalOnClass` 不加载（GHSA 安全修复），无法用该接口做读路径验证；端到端验证需走真实业务接口。

### 8.2 有效授权定义

```
status = 'active'
AND (valid_from IS NULL OR valid_from <= NOW())
AND (valid_until IS NULL OR valid_until > NOW())
AND deleted = 0
```

未进入 `ACTIVE` 的申请单在鉴权侧**完全不可见**——这是"授权即生效"被结构性阻断的方式：不是加校验，而是让未生效数据在查询条件层面不存在。

### 8.3 兼容查询（P1 起）

```sql
SELECT permission_id FROM sys_permission_grant
 WHERE subject_type = 'ROLE' AND subject_id IN (...)
   AND status = 'active' AND deleted = 0
   AND (valid_until IS NULL OR valid_until > NOW())
UNION
SELECT permission_id FROM sys_role_permission WHERE role_id IN (...)
```

`UNION` 而非 `UNION ALL`，避免重复。P2 移除后半段。

### 8.4 配置开关

`jeecg.permission.grant.enabled`（默认 `false`）。关闭时行为与现状完全一致；开启后才走新表与 UNION 逻辑。回退即在配置层关闭。

---

## 9. 数据权限 fail-closed（G3）

### 9.1 改造方案（两条并行，缺一不可）

**方案 A（结构性）**：拦截点从注解下沉到 Mapper 层。挂载点参考 `jeecg-boot-base-core/.../config/mybatis/JeecgTenantParser.java`（R4 更正：原稿猜的 `TenantHandler` 不存在）。未显式声明时按 `scope_type` 默认最小集 `SELF`。

**方案 B（兜底性）**：注解存在但规则为空时拒绝：

```java
// PermissionDataAspect.java:73 附近
if (dataRules == null || dataRules.isEmpty()) {
    throw new JeecgBootException("数据权限规则缺失，拒绝访问: " + requestPath);
}
```

只做 B，忘加注解的接口仍裸奔；只做 A，存量注解路径行为突变。

### 9.2 灰度策略

1. B 先开**告警模式**（记日志不抛）跑一周，收集受影响接口清单；
2. 清单清零后切**拒绝模式**；
3. A 按模块逐步接入，优先已有多租户语义的模块。

> 知情项：方案 B 修改 `PermissionDataAspect`（base-core），是全文唯一触碰内核之处。升级 Jeecg 时该文件为 merge 冲突点，需登记。

---

## 10. 通知闭环（G4）

- 事件：`org.jeecg.common.log.EventLoggers`（`TeamApprovalServiceImpl` 已在用）。
- 邮件：`jeecg-module-mail` 的发送能力。

| 事件名 | 触发点 | 通知对象 |
|---|---|---|
| `grant.submitted` | submit | 审批人 |
| `grant.approved` | 末步 approve | 申请人 |
| `grant.rejected` | reject | 申请人 |
| `grant.activated` | → ACTIVE | 被授权人 |
| `grant.expiring` | 到期前 3 天 | 被授权人 |
| `grant.expired` | 读时判定或扫描 | 被授权人 |
| `grant.revoked` | `/revoke` | 被授权人（含理由） |

**依赖方向**：`jeecg-module-system` 不依赖 `jeecg-module-mail`（已核实 system-biz pom 无此依赖）。监听器装配在 `jeecg-system-start`（该处已依赖 mail），经 `ApplicationEventPublisher` + `@EventListener` 解耦。

**申诉**：`REVOKED` / `EXPIRED` 保留"重新申请"快捷入口，不提供"复活"接口。

---

## 11. 分期实施计划

### P0（1-2 周）：地基 —— ✅ 已落地（2026-11-19）

- [x] `PermissionGrantStatus` 枚举 + 迁移表单测（5/5 通过）
- [x] Flyway `V3.9.6_12__permission_grant.sql` 建两表 + 5 条按钮权限 + admin 授权
- [x] `apply/approve/reject/return/resubmit/cancel` + CAS 更新
- [x] 读路径接入（开关 `jeecg.permission.grant.enabled` + 缓存失效）

**落地说明**：P0 审批链为单步（`apply` 时指定第 1 步审批人），多步会签留给 P2；缓存失效为全局清一次（治理动作低频，可接受）。

**P0 验收**：未审批的申请单不会让用户获得任何新权限（集成测试证明）。

### P1（1-2 周）：时效与回收 —— ✅ 已落地（2026-11-19）

- [x] `valid_from` / `valid_until` 读时判定（读路径 SQL 已含时间窗口）
- [x] `EXPIRED` / `REVOKED` 落地 + `/revoke`
- [x] 到期扫描任务 `PermissionGrantExpireJob`（Quartz，仅状态落库与通知）
- [x] `grant.expiring` / `grant.expired` 事件（`PermissionGrantEvent` + `ApplicationEventPublisher`）

**落地说明**：提醒去重靠新增列 `expiring_notified`（脚本 `V3.9.6_13`）；事件监听器（邮件/站内信）尚未装配，P2 接入 `jeecg-system-start`。

**P1 验收（按实测修正）**：临时授权到期后，**读路径本身即拒绝**（已验证：status 仍 active、`valid_until` 已过期时业务接口返回 510）。
但 Shiro 授权缓存会保留上一次装订的 `AuthorizationInfo`，**最坏情形下拒绝延迟 = 授权缓存 TTL（当前 200s）**，与扫描任务无关。
回收（`/revoke`）与生效（`activate`）走的是精确清缓存（见 §6.2），实测同会话立即生效，无延迟。

### 实测验证记录（2026-11-19，8081 端口，开关开启）

| # | 场景 | 结果 |
|---|---|---|
| 1 | 未授权用户访问受 `team:mail:template:list` 保护的接口 | 510（无权限） |
| 2 | 申请 + 审批通过后，同一会话立即访问 | 200（生效即时，缓存已清） |
| 3 | `/revoke` 后同一会话立即访问 | 510（73ms 内失效） |
| 4 | `valid_until` 置为过去、status 仍 active，清缓存后访问 | 510（读时判定生效） |
| 5 | 手动触发 `PermissionGrantExpireJob` | active → expired，`update_by=system` |
| 6 | 到期提醒：`valid_until` 落在 3 天内，触发任务 | `expiring_notified` 0 → 1 |

> 关键修正：`shiro-redis` 的 `RedisCache.clear()` 实测为空操作，全局清缓存无效。已改为按受影响用户构造 `SimplePrincipalCollection` 调 `ShiroRealm.clearCache`（与 Jeecg 原生路径一致）。

### P2（2-3 周）：数据权限与存量收敛 —— 🟡 部分落地（2026-11-19）

- [x] `PermissionDataAspect` 空规则告警/拒绝双模式（开关 `jeecg.permission.dataRule.strict`，默认告警）
- [x] 存量回填脚本（`backfill-role-permission.sql`，**手动执行**，不进 Flyway）
- [x] 事件监听器 `PermissionGrantNotifyListener`（装配 jeecg-system-start，站内信通道；邮件待模板+SMTP 后追加）
- [ ] Mapper 层默认最小集 —— 暂缓：需按模块灰度，先看告警清单
- [ ] `TeamAuditBizType` 新增 PERMISSION —— 暂缓：system 层不可依赖 team 模块，领域事件已覆盖

**落地说明**：P2 采用"先告警、后拒绝"的两段式，避免一次性切断存量接口。告警模式实测已产生 `【数据权限告警】` 日志且不影响返回。通知实测：一次申请产生 待审批/已批准/已生效 三条站内信（sys_announcement + send 落库，read_flag=0）。

### P3：复核

- [ ] 使用度统计（0 调用 + 无豁免 → 待确认）
- [ ] 多阶段复核（经理 → 资源所有者 → 安全）
- [ ] 误回收快速恢复通道

---

## 12. 待确认清单（v0.2 已核实）

| # | 项 | 结论 |
|---|---|---|
| 1 | Shiro Realm 类名与权限加载方法 | `jeecg-boot-base-core/.../config/shiro/ShiroRealm.java:66` `doGetAuthorizationInfo`，第 83 行调 `commonApi.queryUserAuths(userId)`；全仓唯一实现点 |
| 2 | 最新 Flyway 版本号 | `V3.9.6_11__team_approval_seed.sql`；**新脚本取 `V3.9.6_12`** |
| 3 | `sys_role_permission` 存量行数 | 待 P1 回填前用 SQL 统计（不影响 P0） |
| 4 | system-biz 是否依赖 mail | **否**（pom 0 命中）；mail 仅在 `jeecg-system-start`——§10 装配点正确 |
| 5 | 多租户/数据权限拦截器叠加顺序 | 拦截点为 `JeecgTenantParser`（`config/mybatis/`），P2 接入时确认顺序 |
| 6 | `EventLoggers` 签名与字段口径 | 类存在：`jeecg-boot-base-core/.../common/log/EventLoggers.java`，口径同 `D-87B` |

---

## 13. 风险与回退

| 风险 | 影响 | 缓解 |
|---|---|---|
| 鉴权读路径改错导致权限丢失 | 高 | P1 起 `UNION` 兼容；上线前跑全量用户权限快照对比 |
| 回收后缓存未清 | 高 | **实测修正**：`shiro-redis` 的 `RedisCache.clear()` 为空操作，已改为按受影响用户精确清 principal 缓存；验收见 §14 |
| 到期后拒绝延迟 | 中 | 读路径即拒绝，但已缓存的 AuthorizationInfo 保留至 TTL（200s）；需要更严时下调缓存 TTL 或在开关开启时关闭授权缓存 |
| 数据权限切拒绝误伤存量 | 中高 | 先告警模式一周，按接口清单逐个配规则 |
| 到期判定依赖应用时钟 | 中 | 统一用数据库 `NOW()` |
| 新增表被物理删除绕过 | 中 | 禁物理删除，`/revoke` 唯一回收路径 |
| 团队审批与权限审批心智混淆 | 低 | §4 D2 明确"同构不同表" |

**回退方案**：读路径改造由 `jeecg.permission.grant.enabled` 控制；关闭即回落为仅查 `sys_role_permission`，新表数据保留不丢。

---

## 14. 验收标准（可执行）

1. **结构性证明**：构造 `PENDING` 授权，断言目标接口 403，且 Realm 权限集合不含该 `permission_id`。
2. **时效性证明**：构造 `valid_until = now - 1s` 的 `ACTIVE` 记录，停掉所有定时任务，断言鉴权拒绝。
3. **缓存失效证明（R2 新增）**：`/revoke` 后**同一会话**立即 403，无需等待缓存过期。
4. **fail-closed 证明**：未加 `@PermissionData` 的 Controller 方法默认返回空集而非全量。
5. **闭环证明**：`/revoke` 产生 `grant.revoked` 事件，监听器收到，邮件记录表存在对应行。
