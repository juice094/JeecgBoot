-- =============================================================================
-- 团队协作 - 审批模板种子数据（DRAFT，与 V3.9.6_10__team_approval_v2 配套）
-- 落地方式：取号后改名为 V3.9.6_<号>__team_approval_seed.sql，移入
--           jeecg-system-start/src/main/resources/flyway/sql/mysql/
-- 说明：模板 id 固定，便于与 team_approval_template_step 关联；重复执行会因主键冲突失败，
--       符合 Flyway 一次执行语义。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 费用报销：金额 + 费用类型 + 发票张数
-- ---------------------------------------------------------------------------
INSERT INTO team_approval_template
    (id, category, name, description, field_schema, enabled, create_by, create_time)
VALUES (
    'tpl-expense-0001',
    '费用报销',
    '费用报销审批',
    '金额 > 5000 需副总经理审批（条件分支待实现，当前按固定两级链）',
    '[{"key":"amount","label":"金额","type":"number","required":true,"min":0,"max":1000000},
      {"key":"expenseType","label":"费用类型","type":"select","required":true,"options":["差旅","招待","办公","其他"]},
      {"key":"invoiceCount","label":"发票张数","type":"number","min":1,"max":200}]',
    1, 'system', NOW()
);

INSERT INTO team_approval_template_step
    (id, template_id, step_no, approver_id, approver_name, approver_role, sign_type, create_time)
VALUES
    ('tpl-expense-0001-s1', 'tpl-expense-0001', 1, NULL, '贺致远', '部门主管', 'SEQ', NOW()),
    ('tpl-expense-0001-s2', 'tpl-expense-0001', 2, NULL, '黄茜茜', '副总经理', 'SEQ', NOW());

-- ---------------------------------------------------------------------------
-- 2. 采购申请：物品 + 金额 + 供应商
-- ---------------------------------------------------------------------------
INSERT INTO team_approval_template
    (id, category, name, description, field_schema, enabled, create_by, create_time)
VALUES (
    'tpl-purchase-0001',
    '采购申请',
    '采购审批',
    '采购物品明细与预算金额',
    '[{"key":"amount","label":"金额","type":"number","required":true,"min":0,"max":10000000},
      {"key":"itemName","label":"物品名称","type":"text","required":true},
      {"key":"supplier","label":"供应商","type":"text"},
      {"key":"quantity","label":"数量","type":"number","min":1}]',
    1, 'system', NOW()
);

INSERT INTO team_approval_template_step
    (id, template_id, step_no, approver_id, approver_name, approver_role, sign_type, create_time)
VALUES
    ('tpl-purchase-0001-s1', 'tpl-purchase-0001', 1, NULL, '贺致远', '部门主管', 'SEQ', NOW()),
    ('tpl-purchase-0001-s2', 'tpl-purchase-0001', 2, NULL, '黄茜茜', '副总经理', 'SEQ', NOW());

-- ---------------------------------------------------------------------------
-- 3. 加班申请：时段（无金额，验证非金额类模板）
-- ---------------------------------------------------------------------------
INSERT INTO team_approval_template
    (id, category, name, description, field_schema, enabled, create_by, create_time)
VALUES (
    'tpl-overtime-0001',
    '加班申请',
    '加班审批',
    '非工作时间加班，需注明时段类型',
    '[{"key":"overtimeType","label":"加班类型","type":"select","required":true,"options":["工作日延时","周末","节假日"]},
      {"key":"hours","label":"时长（小时）","type":"number","required":true,"min":0.5,"max":24}]',
    1, 'system', NOW()
);

INSERT INTO team_approval_template_step
    (id, template_id, step_no, approver_id, approver_name, approver_role, sign_type, create_time)
VALUES
    ('tpl-overtime-0001-s1', 'tpl-overtime-0001', 1, NULL, '贺致远', '部门主管', 'SEQ', NOW()),
    ('tpl-overtime-0001-s2', 'tpl-overtime-0001', 2, NULL, '黄茜茜', '副总经理', 'SEQ', NOW());

-- ---------------------------------------------------------------------------
-- 注意：approver_id 为 NULL，走姓名兜底（与历史行口径一致）。
--       正式上线前应把 approver_id 回填为真实 sys_user.id，否则重名者仍会互串。
-- ---------------------------------------------------------------------------
