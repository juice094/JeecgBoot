# SQL 结构 — jeecg-module-team 数据模型

> 现状部分提取自 flyway 脚本 `V3.9.6_2`–`V3.9.6_7`（jeecg-system-start/resources/flyway/sql/mysql/）与实际实体类，为**已落库事实**。
> 「演进推导」部分是为对齐 GitHub Issues 参考（见 `../.refs/extracted/data-model-and-gaps.md`）而推导的待定 DDL，**未执行，需拍板**。
> 试点环境 Flyway 自动装配被排除：脚本需手动执行并登记 `flyway_schema_history`。

## 一、表总览

| 表 | 角色 | 来源 |
|---|---|---|
| `team_task` | 任务主体（Issues 对齐对象） | V2 建表 + V5 逻辑删 + V7 task_no |
| `team_task_comment` | 任务评论（Markdown 正文） | V7 |
| `team_audit_log` | 审计/时间线事件流（只追加） | V4 建表 + V7 时间线索引 |
| `team_approval` / `team_approval_step` | 审批单 / 步骤链 | V3 |
| `team_bug` | Bug 反馈 | V3 + V5 逻辑删 |

关系：`team_task_comment.task_id → team_task.id`；`team_audit_log(biz_type='task', biz_id) → team_task.id`（逻辑外键，无物理约束，符合平台禁外键约定）。

## 二、team_task（有效结构 = V2 + V5 + V7 合并）

```sql
CREATE TABLE IF NOT EXISTS team_task (
  id            VARCHAR(36)  NOT NULL COMMENT '主键',
  task_no       INT          DEFAULT NULL COMMENT '租户内序号，用于 #12 式引用',      -- V7
  title         VARCHAR(128) NOT NULL COMMENT '任务标题',
  description   VARCHAR(512) DEFAULT NULL COMMENT '任务描述',
  status        VARCHAR(16)  NOT NULL DEFAULT 'todo' COMMENT '状态: todo/doing/done',
  tag           VARCHAR(16)  DEFAULT NULL COMMENT '标签: 需求/开发/缺陷',
  priority      VARCHAR(8)   NOT NULL DEFAULT '中' COMMENT '优先级: 高/中/低',
  assignee_id   VARCHAR(36)  DEFAULT NULL COMMENT '被指派人(sys_user.id)',
  assignee_name VARCHAR(64)  DEFAULT NULL COMMENT '被指派人姓名冗余',
  due_date      DATE         DEFAULT NULL COMMENT '截止日期',
  sort_no       INT          NOT NULL DEFAULT 0 COMMENT '看板列内排序',
  create_by     VARCHAR(32)  DEFAULT NULL COMMENT '创建人登录名',
  create_time   DATETIME     DEFAULT NULL COMMENT '创建日期',
  update_by     VARCHAR(32)  DEFAULT NULL COMMENT '更新人登录名',
  update_time   DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日期',
  sys_org_code  VARCHAR(64)  DEFAULT NULL COMMENT '所属部门',
  sys_tenant_id VARCHAR(10)  DEFAULT NULL COMMENT '租户编号',
  deleted       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=已删',      -- V5
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_task_no (sys_tenant_id, task_no),                                  -- V7
  KEY idx_team_task_status (status),
  KEY idx_team_task_assignee (assignee_id),
  KEY idx_team_task_deleted (deleted)                                                   -- V5
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队协作-任务表';
```

要点：
- `task_no` 为租户内序号（`#12` 式引用），应用层 `max(task_no)+1` 生成，唯一索引兜底并发；存量行 task_no 可为 NULL。
- `status` 存码不存中文：`todo/doing/done`（`TeamTaskStatus` 枚举为唯一口径源）。
- `deleted` 走 MyBatis-Plus `@TableLogic`，所有查询自动过滤，删除端点对外契约不变。
- 单指派模型（GitHub 是 assignees[] 数组，差距见 `.refs` 差距表）。

## 三、team_task_comment（V7）

```sql
CREATE TABLE IF NOT EXISTS team_task_comment (
  id            VARCHAR(36)  NOT NULL COMMENT '主键',
  task_id       VARCHAR(36)  NOT NULL COMMENT '任务ID',
  author_id     VARCHAR(36)  DEFAULT NULL COMMENT '作者(sys_user.id)',
  author_name   VARCHAR(64)  NOT NULL COMMENT '作者姓名冗余',
  body          TEXT         NOT NULL COMMENT '评论正文(Markdown 原文)',
  create_by     VARCHAR(32)  DEFAULT NULL COMMENT '创建人登录名',
  create_time   DATETIME     DEFAULT NULL COMMENT '创建日期',
  update_by     VARCHAR(32)  DEFAULT NULL COMMENT '更新人登录名',
  update_time   DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日期',
  sys_org_code  VARCHAR(64)  DEFAULT NULL COMMENT '所属部门',
  sys_tenant_id VARCHAR(10)  DEFAULT NULL COMMENT '租户编号',
  deleted       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=已删',
  PRIMARY KEY (id),
  KEY idx_comment_task (task_id, create_time),
  KEY idx_comment_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队协作-任务评论';
```

要点：正文上限 10000 字符（service 层 `MAX_BODY_LENGTH` 校验，TEXT 容量充裕）；评论有编辑语义（update_time ON UPDATE），事件流不存正文。

## 四、team_audit_log（V4 + V7 索引）—— 时间线数据源

```sql
CREATE TABLE IF NOT EXISTS team_audit_log (
  id             VARCHAR(36)  NOT NULL COMMENT '主键',
  biz_type       VARCHAR(32)  NOT NULL COMMENT '业务类型: task/approval/bug',
  biz_id         VARCHAR(36)  NOT NULL COMMENT '业务单据ID',
  biz_code       VARCHAR(128) DEFAULT NULL COMMENT '业务单号/标题冗余（便于人读检索）',
  action         VARCHAR(32)  NOT NULL COMMENT '动作: create/submit/change_status/comment/approve/reject',
  from_value     VARCHAR(64)  DEFAULT NULL COMMENT '变更前值（状态码；审批为步骤快照）',
  to_value       VARCHAR(64)  DEFAULT NULL COMMENT '变更后值（状态码；评论为评论ID）',
  operator_id    VARCHAR(36)  DEFAULT NULL COMMENT '操作人(sys_user.id)',
  operator_name  VARCHAR(64)  DEFAULT NULL COMMENT '操作人姓名冗余',
  detail         VARCHAR(1024) DEFAULT NULL COMMENT '补充说明（异常上下文/审批意见摘要）',
  create_by      VARCHAR(32)  DEFAULT NULL COMMENT '创建人登录名',
  create_time    DATETIME     DEFAULT NULL COMMENT '创建日期',
  update_by      VARCHAR(32)  DEFAULT NULL COMMENT '更新人登录名',
  update_time    DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日期',
  sys_org_code   VARCHAR(64)  DEFAULT NULL COMMENT '所属部门',
  sys_tenant_id  VARCHAR(10)  DEFAULT NULL COMMENT '租户编号',
  PRIMARY KEY (id),
  KEY idx_audit_biz (biz_type, biz_id),
  KEY idx_audit_action (action),
  KEY idx_audit_create_time (create_time),
  KEY idx_audit_timeline (biz_type, biz_id, create_time)   -- V7：详情页时间线查询
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队协作-审计日志';
```

要点：**只追加不改写**，无更新/删除代码路径；task 时间线 = `biz_type='task' AND biz_id=? ORDER BY create_time ASC`；动作枚举 `TeamAuditAction`（create/submit/change_status/comment/approve/reject）是事件类型唯一口径源。

## 五、其余 team 表（简表）

- `team_approval`：审批单（code `OA-YYYYMMDD-NNN`、category、status pending/approved/rejected、applicant、current_step、起止日期）+ V5 逻辑删。
- `team_approval_step`：步骤链（approval_id、step_no、approver_name/role、action wait/approved/rejected、comment、acted_at）。**按姓名匹配审批人**（试点硬编码链：贺致远 → 黄茜茜）。
- `team_bug`：Bug 反馈（description、screenshot_path、status open/processing/resolved/closed、reporter/handler 姓名冗余）+ V5 逻辑删。

## 六、演进推导（未执行，按 GitHub 差距表）

**1) 多标签 tag_list**（差距表「labels[]」项，优先级最高）
```sql
-- 方案 A（推荐，试点量级够用）：任务表加逗号分隔列，查询走前缀索引
ALTER TABLE team_task
  ADD COLUMN tag_list VARCHAR(128) DEFAULT NULL COMMENT '多标签逗号分隔' AFTER tag,
  ADD KEY idx_team_task_tags (tag_list);
-- 方案 B（标签需独立配色/统计时再上）：关联表
CREATE TABLE team_task_label (
  id VARCHAR(36) NOT NULL, task_id VARCHAR(36) NOT NULL,
  label_code VARCHAR(32) NOT NULL, label_color VARCHAR(8) DEFAULT NULL,
  PRIMARY KEY (id), KEY idx_ttl_task (task_id), KEY idx_ttl_label (label_code)
) COMMENT='团队协作-任务标签关联';
```
取舍：方案 A 无 JOIN、与现有 tag 列共存平滑；方案 B 才有标签元数据（色值、重命名）。**待拍**。

**2) 评论数冗余 comment_count**（列表页 💬 N）
现状前端从详情聚合 count；列表页要角标则需冗余列 + 触发式维护（service 层加减）。任务量上行前不做。

**3) 里程碑 milestone**：未排期。若上，参照 `team_approval` 模式建 `team_milestone(id, title, due_date, state)` + `team_task.milestone_id`，属独立小迁移。

**4) 事件表 team_task_event**：**已否决**（V7 拍板复用 audit_log），不再推导。
