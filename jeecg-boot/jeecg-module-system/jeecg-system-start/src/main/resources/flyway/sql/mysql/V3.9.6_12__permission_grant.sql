-- =============================================================================
-- 权限确权治理基座 - 授权关系与审批步骤（P0）
-- 依据：docs/permission-governance/PERMISSION-GOVERNANCE.md §5
-- 号说明：接 V3.9.6_11__team_approval_seed.sql 之后取 _12
-- 注意：试点环境 Flyway 被排除时，本脚本需手动执行并登记 flyway_schema_history。
-- =============================================================================

-- 0. 授权关系主表（确权主体，禁止物理删除，回收走 /revoke）
CREATE TABLE IF NOT EXISTS `sys_permission_grant` (
  `id`              varchar(32)   NOT NULL COMMENT '主键(雪花)',
  `grant_no`        varchar(64)   NOT NULL COMMENT '授权单号',
  `subject_type`    varchar(16)   NOT NULL COMMENT '主体类型: USER/ROLE/DEPT',
  `subject_id`      varchar(32)   NOT NULL COMMENT '主体ID',
  `permission_id`   varchar(32)   NOT NULL COMMENT '权限ID(sys_permission)',
  `data_rule_ids`   varchar(1000) DEFAULT NULL COMMENT '数据规则ID串',
  `scope_type`      varchar(16)   NOT NULL DEFAULT 'SELF' COMMENT 'SELF/DEPT/DEPT_AND_CHILD/GLOBAL',
  `status`          varchar(16)   NOT NULL COMMENT 'draft/pending/returned/approved/rejected/canceled/active/expired/revoked',
  `valid_from`      datetime      DEFAULT NULL COMMENT '生效起点',
  `valid_until`     datetime      DEFAULT NULL COMMENT '生效终点(NULL=长期)',
  `apply_reason`    varchar(500)  DEFAULT NULL COMMENT '申请理由(必填)',
  `applicant`       varchar(32)   DEFAULT NULL COMMENT '申请人',
  `approver`        varchar(32)   DEFAULT NULL COMMENT '末次审批人',
  `current_step`    int           DEFAULT 1 COMMENT '当前审批步骤',
  `source`          varchar(16)   NOT NULL DEFAULT 'APPLY' COMMENT 'APPLY/TEMPLATE/MIGRATION',
  `ext_note`        varchar(500)  DEFAULT NULL,
  `create_time`     datetime      DEFAULT NULL,
  `create_by`       varchar(32)   DEFAULT NULL,
  `update_time`     datetime      DEFAULT NULL,
  `update_by`       varchar(32)   DEFAULT NULL,
  `deleted`         tinyint       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_grant_no` (`grant_no`),
  KEY `idx_subject_status` (`subject_type`, `subject_id`, `status`),
  KEY `idx_expire` (`status`, `valid_until`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限授予关系(确权主体)';

-- 1. 审批步骤表
CREATE TABLE IF NOT EXISTS `sys_permission_grant_step` (
  `id`          varchar(32)  NOT NULL,
  `grant_id`    varchar(32)  NOT NULL,
  `step_no`     int          NOT NULL,
  `approver`    varchar(32)  DEFAULT NULL,
  `action`      varchar(16)  DEFAULT NULL COMMENT 'wait/approve/reject/return',
  `comment`     varchar(500) DEFAULT NULL,
  `acted_at`    datetime     DEFAULT NULL,
  `create_by`   varchar(32)  DEFAULT NULL,
  `create_time` datetime     DEFAULT NULL,
  `update_by`   varchar(32)  DEFAULT NULL,
  `update_time` datetime     DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_grant` (`grant_id`, `step_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限授予审批步骤';

-- 2. 按钮权限（与 Controller @RequiresPermissions 一一对应）
--    父菜单承载挂载，前端不路由
INSERT INTO `sys_permission` (`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`, `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
VALUES ('20261119100000000001', '0', '权限确权', NULL, NULL, 0, NULL, NULL, 1, NULL, '1', 97, 0, NULL, 0, 0, 0, 0, '权限确权治理基座按钮权限挂载载体', 'admin', '2026-11-19 10:00:00', NULL, NULL, 0, 0, '1', 0);

INSERT INTO `sys_permission` (`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`, `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`) VALUES
('20261119100000000101', '20261119100000000001', '确权-申请', NULL, NULL, 0, NULL, NULL, 2, 'system:permissionGrant:apply', '1', 1, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2026-11-19 10:00:00', NULL, NULL, 0, 0, '1', 0),
('20261119100000000102', '20261119100000000001', '确权-审批', NULL, NULL, 0, NULL, NULL, 2, 'system:permissionGrant:approve', '1', 2, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2026-11-19 10:00:00', NULL, NULL, 0, 0, '1', 0),
('20261119100000000103', '20261119100000000001', '确权-回收', NULL, NULL, 0, NULL, NULL, 2, 'system:permissionGrant:revoke', '1', 3, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2026-11-19 10:00:00', NULL, NULL, 0, 0, '1', 0),
('20261119100000000104', '20261119100000000001', '确权-查询', NULL, NULL, 0, NULL, NULL, 2, 'system:permissionGrant:list', '1', 4, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2026-11-19 10:00:00', NULL, NULL, 0, 0, '1', 0);

-- 3. 授权给系统管理员角色（同 team/mail 脚本口径）
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
VALUES
('20261119100000000201', 'f6817f48af4fb3af11b9e8bf182f618b', '20261119100000000001', NULL, NOW(), '127.0.0.1'),
('20261119100000000202', 'f6817f48af4fb3af11b9e8bf182f618b', '20261119100000000101', NULL, NOW(), '127.0.0.1'),
('20261119100000000203', 'f6817f48af4fb3af11b9e8bf182f618b', '20261119100000000102', NULL, NOW(), '127.0.0.1'),
('20261119100000000204', 'f6817f48af4fb3af11b9e8bf182f618b', '20261119100000000103', NULL, NOW(), '127.0.0.1'),
('20261119100000000205', 'f6817f48af4fb3af11b9e8bf182f618b', '20261119100000000104', NULL, NOW(), '127.0.0.1');
