-- =============================================================================
-- 团队协作 - 审批 v2（2026-11-18 取号转正：_10；原 docs/sql 下 DRAFT 已移除）
-- 试点环境 Flyway 自动装配被排除，本脚本手动执行并登记 flyway_schema_history。
--
-- 变更内容：
--   1) team_approval_step 补 approver_id，修正按姓名匹配审批人的缺陷
--   2) 新增审批模板表 team_approval_template / team_approval_template_step
--   3) 状态机扩展为 draft/pending/returned/approved/rejected/canceled
--      （代码层枚举 TeamApprovalStatus，落库仍为 varchar(16)）
--   4) 字段模板：team_approval 加 form_data JSON + amount DECIMAL(12,2)；
--      team_approval_template 加 field_schema JSON
--
-- 兼容性：新增列均可空；现有数据不回填，approver_id 为空的旧步骤按姓名兜底。
-- 幂等性：ADD COLUMN / CREATE INDEX 非幂等，重复执行会报错，符合 Flyway 一次执行语义。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. team_approval_step 补 approver_id
-- ---------------------------------------------------------------------------
ALTER TABLE team_approval_step
    ADD COLUMN approver_id VARCHAR(36) DEFAULT NULL COMMENT '审批人(sys_user.id)，为空时按 approver_name 兜底' AFTER approver_name,
    ADD KEY idx_step_approver_wait (approver_id, action);

-- 保留原 (approver_name, action) 索引以兼容历史数据

-- ---------------------------------------------------------------------------
-- 2. 审批模板表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS team_approval_template (
    id            VARCHAR(36)  NOT NULL COMMENT '主键',
    category      VARCHAR(64)  NOT NULL COMMENT '申请类别，与 team_approval.category 对齐',
    name          VARCHAR(128) NOT NULL COMMENT '模板名称',
    description   VARCHAR(512) DEFAULT NULL COMMENT '模板说明',
    enabled       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用 1=启用 0=停用',
    create_by     VARCHAR(32)  DEFAULT NULL COMMENT '创建人登录名',
    create_time   DATETIME     DEFAULT NULL COMMENT '创建日期',
    update_by     VARCHAR(32)  DEFAULT NULL COMMENT '更新人登录名',
    update_time   DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日期',
    sys_org_code  VARCHAR(64)  DEFAULT NULL COMMENT '所属部门',
    sys_tenant_id VARCHAR(10)  DEFAULT NULL COMMENT '租户编号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_category (sys_tenant_id, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队协作-审批模板';

CREATE TABLE IF NOT EXISTS team_approval_template_step (
    id            VARCHAR(36)  NOT NULL COMMENT '主键',
    template_id   VARCHAR(36)  NOT NULL COMMENT '关联 team_approval_template.id',
    step_no       INT          NOT NULL COMMENT '步骤序号，从1开始',
    approver_id   VARCHAR(36)  DEFAULT NULL COMMENT '审批人(sys_user.id)',
    approver_name VARCHAR(64)  NOT NULL COMMENT '审批人姓名冗余',
    approver_role VARCHAR(64)  DEFAULT NULL COMMENT '审批人角色说明',
    sign_type     VARCHAR(16)  NOT NULL DEFAULT 'SEQ' COMMENT '签核类型 SEQ=依次 ALL=会签 ANY=或签（本期仅 SEQ 生效）',
    create_by     VARCHAR(32)  DEFAULT NULL COMMENT '创建人登录名',
    create_time   DATETIME     DEFAULT NULL COMMENT '创建日期',
    update_by     VARCHAR(32)  DEFAULT NULL COMMENT '更新人登录名',
    update_time   DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日期',
    sys_org_code  VARCHAR(64)  DEFAULT NULL COMMENT '所属部门',
    sys_tenant_id VARCHAR(10)  DEFAULT NULL COMMENT '租户编号',
    PRIMARY KEY (id),
    KEY idx_tpl_step (template_id, step_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队协作-审批模板步骤';

-- ---------------------------------------------------------------------------
-- 3. team_approval.status 取值口径（无结构变更）
-- ---------------------------------------------------------------------------
-- 合法值（TeamApprovalStatus 枚举为唯一口径源）：
--   draft     草稿（预留，本期 submit 直接写 pending）
--   pending   待审（current_step 指示当前处理人）
--   returned  已退回（申请人可改后 resubmit）
--   approved  已通过（终态）
--   rejected  已拒绝（终态）
--   canceled  已撤销（终态）
--
-- 若存量 status 列为 VARCHAR(16)，六态均不超长，无需扩容。

-- ---------------------------------------------------------------------------
-- 4. 字段模板（form_data + amount + field_schema）
-- ---------------------------------------------------------------------------
-- 设计取舍：
--   a) 类别专属字段（发票张数、出差地点等）走 form_data JSON——一次改表即可无限扩展，
--      审批单是「存整条读整条」场景，JSON 与普通列性能几乎无差；
--   b) amount 单独成列——它是唯一需要范围查询、索引、聚合的字段
--      （「超 5000 走二级审批」「本月报销总额」），JSON 里查会全表扫描；
--   c) field_schema 存模板侧字段契约，前端按它渲染，后端按它复核（不只信前端）。

ALTER TABLE team_approval
    ADD COLUMN form_data JSON DEFAULT NULL COMMENT '类别专属字段值（按模板 field_schema 校验）' AFTER reason,
    ADD COLUMN amount DECIMAL(12, 2) DEFAULT NULL COMMENT '金额（从 form_data.amount 提取，供范围查询/聚合/分级审批）' AFTER form_data,
    ADD KEY idx_approval_amount (amount);

ALTER TABLE team_approval_template
    ADD COLUMN field_schema JSON DEFAULT NULL COMMENT '字段契约数组：[{key,label,type,required,options,min,max}]' AFTER description;

-- field_schema 元素结构约定：
--   key       字段标识（与 form_data 的键一致）
--   label     前端显示名
--   type      text | textarea | number | date | select | multiselect
--   required  是否必填
--   options   select/multiselect 的候选值数组
--   min/max   number 的取值范围
