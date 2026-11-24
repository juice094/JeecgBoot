-- =============================================================================
-- 流程域（Flowable）按钮权限——W2 施工（6 码，与 FlowProcessController @RequiresPermissions 一一对应）
-- 号说明：接 V3.9.6_13__permission_grant_expire_job.sql 之后取 _14
-- 口径：F-09 先例（V3.9.6_9__oa_mail_permissions.sql）——父菜单仅作权限挂载载体（不路由），
--       授权给系统管理员角色 f6817f48af4fb3af11b9e8bf182f618b。
-- 范围：不含 process:cancel / task:reassign（宿裁留后续波次）。
-- =============================================================================

-- 0. 父菜单（流程审批，权限挂载载体，sort_no=96 不与 97/98/99 冲突）
INSERT INTO `sys_permission` (`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`, `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
VALUES ('20261120100000000001', '0', '流程审批', NULL, NULL, 0, NULL, NULL, 1, NULL, '1', 96, 0, NULL, 0, 0, 0, 0, '流程域按钮权限挂载载体（Flowable W2）', 'admin', '2026-11-20 10:00:00', NULL, NULL, 0, 0, '1', 0);

-- 1. 六个按钮权限（team:flow:*）
INSERT INTO `sys_permission` (`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`, `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`) VALUES
('20261120100000000101', '20261120100000000001', '流程定义-列表', NULL, NULL, 0, NULL, NULL, 2, 'team:flow:process:list', '1', 1, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2026-11-20 10:00:00', NULL, NULL, 0, 0, '1', 0),
('20261120100000000102', '20261120100000000001', '流程-发起', NULL, NULL, 0, NULL, NULL, 2, 'team:flow:process:start', '1', 2, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2026-11-20 10:00:00', NULL, NULL, 0, 0, '1', 0),
('20261120100000000103', '20261120100000000001', '审批-我的待办', NULL, NULL, 0, NULL, NULL, 2, 'team:flow:approve:todo', '1', 3, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2026-11-20 10:00:00', NULL, NULL, 0, 0, '1', 0),
('20261120100000000104', '20261120100000000001', '审批-我的已办', NULL, NULL, 0, NULL, NULL, 2, 'team:flow:approve:done', '1', 4, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2026-11-20 10:00:00', NULL, NULL, 0, 0, '1', 0),
('20261120100000000105', '20261120100000000001', '审批-办理', NULL, NULL, 0, NULL, NULL, 2, 'team:flow:approve:handle', '1', 5, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2026-11-20 10:00:00', NULL, NULL, 0, 0, '1', 0),
('20261120100000000106', '20261120100000000001', '流程实例-详情', NULL, NULL, 0, NULL, NULL, 2, 'team:flow:instance:detail', '1', 6, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2026-11-20 10:00:00', NULL, NULL, 0, 0, '1', 0);

-- 2. 授权给系统管理员角色（同 team/mail/确权脚本口径）
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
VALUES
('20261120100000000201', 'f6817f48af4fb3af11b9e8bf182f618b', '20261120100000000101', NULL, NOW(), '127.0.0.1'),
('20261120100000000202', 'f6817f48af4fb3af11b9e8bf182f618b', '20261120100000000102', NULL, NOW(), '127.0.0.1'),
('20261120100000000203', 'f6817f48af4fb3af11b9e8bf182f618b', '20261120100000000103', NULL, NOW(), '127.0.0.1'),
('20261120100000000204', 'f6817f48af4fb3af11b9e8bf182f618b', '20261120100000000104', NULL, NOW(), '127.0.0.1'),
('20261120100000000205', 'f6817f48af4fb3af11b9e8bf182f618b', '20261120100000000105', NULL, NOW(), '127.0.0.1'),
('20261120100000000206', 'f6817f48af4fb3af11b9e8bf182f618b', '20261120100000000106', NULL, NOW(), '127.0.0.1');
