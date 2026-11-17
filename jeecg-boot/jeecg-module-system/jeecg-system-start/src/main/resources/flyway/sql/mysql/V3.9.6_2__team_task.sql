-- =============================================================================
-- 团队协作 - 任务表（issue #2/#10，ROADMAP M3 任务协作模块底座）
-- 前后端联调合同：jeecg-team-web 任务看板（src/constants/task.ts 枚举与此一致）
-- =============================================================================
CREATE TABLE IF NOT EXISTS team_task (
  id            VARCHAR(36)  NOT NULL COMMENT '主键',
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
  PRIMARY KEY (id),
  KEY idx_team_task_status (status),
  KEY idx_team_task_assignee (assignee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队协作-任务表';
