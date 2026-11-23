-- =============================================================================
-- 团队协作 - 审计日志（2026-11-17，issue 见 PR；ROADMAP M3 审计日志底座）
-- 只追加表：业务状态机（task/approval/bug 的 submit/changeStatus/approve/reject）
-- 在事务内统一埋点；不提供更新/删除路径。
-- =============================================================================
CREATE TABLE IF NOT EXISTS team_audit_log (
  id             VARCHAR(36)  NOT NULL COMMENT '主键',
  biz_type       VARCHAR(32)  NOT NULL COMMENT '业务类型: task/approval/bug',
  biz_id         VARCHAR(36)  NOT NULL COMMENT '业务单据ID',
  biz_code       VARCHAR(128) DEFAULT NULL COMMENT '业务单号/标题冗余（便于人读检索）',
  action         VARCHAR(32)  NOT NULL COMMENT '动作: submit/change_status/approve/reject',
  from_value     VARCHAR(64)  DEFAULT NULL COMMENT '变更前值（状态码；审批为步骤快照）',
  to_value       VARCHAR(64)  DEFAULT NULL COMMENT '变更后值（状态码；审批为步骤快照）',
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
  KEY idx_audit_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队协作-审计日志';
