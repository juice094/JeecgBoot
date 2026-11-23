-- =============================================================================
-- 团队协作 - 审批单 + 审批步骤 + Bug 反馈（2026-11-17，issue 见 PR）
-- 轻量状态机：提交 → 按步骤顺序审批 → 全通过=approved / 任一拒绝=rejected
-- =============================================================================
CREATE TABLE IF NOT EXISTS team_approval (
  id             VARCHAR(36)  NOT NULL COMMENT '主键',
  code           VARCHAR(32)  NOT NULL COMMENT '申请单号 OA-YYYYMMDD-NNN',
  category       VARCHAR(32)  NOT NULL COMMENT '申请类别: 请假申请/出差申请/费用报销等',
  title          VARCHAR(128) NOT NULL COMMENT '事项标题',
  reason         VARCHAR(1024) DEFAULT NULL COMMENT '申请事由',
  status         VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  applicant_id   VARCHAR(36)  DEFAULT NULL COMMENT '申请人(sys_user.id)',
  applicant_name VARCHAR(64)  DEFAULT NULL COMMENT '申请人姓名冗余',
  start_date     DATE         DEFAULT NULL COMMENT '开始日期',
  end_date       DATE         DEFAULT NULL COMMENT '结束日期',
  current_step   INT          NOT NULL DEFAULT 1 COMMENT '当前待审步骤序号',
  create_by      VARCHAR(32)  DEFAULT NULL COMMENT '创建人登录名',
  create_time    DATETIME     DEFAULT NULL COMMENT '创建日期',
  update_by      VARCHAR(32)  DEFAULT NULL COMMENT '更新人登录名',
  update_time    DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日期',
  sys_org_code   VARCHAR(64)  DEFAULT NULL COMMENT '所属部门',
  sys_tenant_id  VARCHAR(10)  DEFAULT NULL COMMENT '租户编号',
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_approval_code (code),
  KEY idx_team_approval_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队协作-审批单';

CREATE TABLE IF NOT EXISTS team_approval_step (
  id            VARCHAR(36) NOT NULL COMMENT '主键',
  approval_id   VARCHAR(36) NOT NULL COMMENT '审批单ID',
  step_no       INT         NOT NULL COMMENT '步骤序号, 从1开始',
  approver_name VARCHAR(64) NOT NULL COMMENT '审批人姓名',
  approver_role VARCHAR(64) DEFAULT NULL COMMENT '审批人角色说明',
  action        VARCHAR(16) NOT NULL DEFAULT 'wait' COMMENT 'wait/approved/rejected',
  comment       VARCHAR(512) DEFAULT NULL COMMENT '审批意见',
  acted_at      DATETIME    DEFAULT NULL COMMENT '操作时间',
  create_time   DATETIME    DEFAULT NULL COMMENT '创建日期',
  PRIMARY KEY (id),
  KEY idx_step_approval (approval_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队协作-审批步骤';

CREATE TABLE IF NOT EXISTS team_bug (
  id               VARCHAR(36)  NOT NULL COMMENT '主键',
  description      VARCHAR(2048) NOT NULL COMMENT '问题描述',
  screenshot_path  VARCHAR(256) DEFAULT NULL COMMENT '截图附件路径',
  status           VARCHAR(16)  NOT NULL DEFAULT 'open' COMMENT 'open/processing/resolved/closed',
  reporter_id      VARCHAR(36)  DEFAULT NULL COMMENT '提交人(sys_user.id)',
  reporter_name    VARCHAR(64)  DEFAULT NULL COMMENT '提交人姓名冗余',
  handler_name     VARCHAR(64)  DEFAULT NULL COMMENT '处理人姓名',
  create_by        VARCHAR(32)  DEFAULT NULL COMMENT '创建人登录名',
  create_time      DATETIME     DEFAULT NULL COMMENT '创建日期',
  update_by        VARCHAR(32)  DEFAULT NULL COMMENT '更新人登录名',
  update_time      DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日期',
  sys_org_code     VARCHAR(64)  DEFAULT NULL COMMENT '所属部门',
  sys_tenant_id    VARCHAR(10)  DEFAULT NULL COMMENT '租户编号',
  PRIMARY KEY (id),
  KEY idx_team_bug_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队协作-Bug反馈';
