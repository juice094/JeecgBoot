-- =============================================================================
-- 团队协作 - 表单模板表（2026-11-17，issue #3 落地：模板化申请 + 轻量审批流）
-- 模板：内置请假/报销两个模板（步骤链 JSON 配置化）；form_fields 为试点预留列。
-- 同步变更：team_approval 增加 template_id 记录所用模板（可追溯）。
-- =============================================================================
CREATE TABLE IF NOT EXISTS team_form_template (
  id            VARCHAR(36)  NOT NULL COMMENT '主键',
  code          VARCHAR(32)  NOT NULL COMMENT '模板编码',
  name          VARCHAR(64)  NOT NULL COMMENT '模板名称（页面展示）',
  group_name    VARCHAR(32)  NOT NULL COMMENT '左侧导航分组',
  category      VARCHAR(32)  NOT NULL COMMENT '申请类别（落 team_approval.category）',
  form_tip      VARCHAR(256) DEFAULT NULL COMMENT '表单页提示语',
  form_fields   JSON         DEFAULT NULL COMMENT '表单字段配置（试点预留，动态渲染后置）',
  step_template JSON         NOT NULL COMMENT '默认审批步骤模板: [{approverName, approverRole}]',
  enabled       TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用 1=启用',
  sort_no       INT          NOT NULL DEFAULT 0 COMMENT '排序',
  create_by     VARCHAR(32)  DEFAULT NULL COMMENT '创建人登录名',
  create_time   DATETIME     DEFAULT NULL COMMENT '创建日期',
  update_by     VARCHAR(32)  DEFAULT NULL COMMENT '更新人登录名',
  update_time   DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日期',
  sys_org_code  VARCHAR(64)  DEFAULT NULL COMMENT '所属部门',
  sys_tenant_id VARCHAR(10)  DEFAULT NULL COMMENT '租户编号',
  PRIMARY KEY (id),
  UNIQUE KEY uk_form_template_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队协作-表单模板';

-- team_approval 追加模板追溯列（ Flyway 对既有表做增量变更，不重建）
ALTER TABLE team_approval
  ADD COLUMN template_id VARCHAR(36) DEFAULT NULL COMMENT '表单模板ID(team_form_template.id)' AFTER current_step;

-- 内置模板种子：请假申请 / 费用报销（审批链：部门主管 → 副总经理，姓名见 docs/19 §2.1）
INSERT INTO team_form_template (id, code, name, group_name, category, form_tip, step_template, enabled, sort_no, create_time)
VALUES
  ('tpl-leave-00000000000000000001', 'leave', '请假申请', '考勤与假期', '请假申请',
   '请提前提交请假申请，紧急情况事后 1 个工作日内补提。',
   JSON_ARRAY(
     JSON_OBJECT('approverName', '贺致远', 'approverRole', '部门主管'),
     JSON_OBJECT('approverName', '黄茜茜', 'approverRole', '副总经理')
   ), 1, 1, NOW()),
  ('tpl-expense-0000000000000000001', 'expense', '费用报销', '费用与财务', '费用报销',
   '报销单需附发票扫描件，超过 5000 元另附明细清单。',
   JSON_ARRAY(
     JSON_OBJECT('approverName', '贺致远', 'approverRole', '部门主管'),
     JSON_OBJECT('approverName', '黄茜茜', 'approverRole', '副总经理')
   ), 1, 2, NOW());
