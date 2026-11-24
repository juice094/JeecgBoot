-- =============================================================================
-- 录音转写域（speech）按钮权限——PR #27 合入后补（2026-11-24 编排层）
-- 号说明：接 V3.9.6_16__issue_labels_dependency.sql 之后取 _17
-- 口径：F-09 先例（V3.9.6_14__flow_permissions.sql）——父菜单仅作权限挂载载体（不路由），
--       授权给管理员角色 f6817f48af4fb3af11b9e8bf182f618b。
-- 范围：与 SpeechTaskController @RequiresPermissions 一一对应（4 码）。
-- 背景：speech 模块（Ym3699 feature-yy-workbench）合流未随附权限 SQL，启动验证实证 510 缺口。
-- =============================================================================

-- 0. 父菜单（录音转写，权限挂载载体，sort_no=95 不与 96/97/98/99 冲突）
INSERT INTO `sys_permission` (`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`, `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`)
VALUES ('20261124100000000001', '0', '录音转写', NULL, NULL, 0, NULL, NULL, 1, NULL, '1', 95, 0, NULL, 0, 0, 0, 0, '录音转写域按钮权限挂载载体（speech）', 'admin', '2026-11-24 09:30:00', NULL, NULL, 0, 0, '1', 0);

-- 1. 四个按钮权限（speech:task:*）
INSERT INTO `sys_permission` (`id`, `parent_id`, `name`, `url`, `component`, `is_route`, `component_name`, `redirect`, `menu_type`, `perms`, `perms_type`, `sort_no`, `always_show`, `icon`, `is_leaf`, `keep_alive`, `hidden`, `hide_tab`, `description`, `create_by`, `create_time`, `update_by`, `update_time`, `del_flag`, `rule_flag`, `status`, `internal_or_external`) VALUES
('20261124100000000101', '20261124100000000001', '录音任务-新建', NULL, NULL, 0, NULL, NULL, 2, 'speech:task:add', '1', 1, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2026-11-24 09:30:00', NULL, NULL, 0, 0, '1', 0),
('20261124100000000102', '20261124100000000001', '录音任务-列表', NULL, NULL, 0, NULL, NULL, 2, 'speech:task:list', '1', 2, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2026-11-24 09:30:00', NULL, NULL, 0, 0, '1', 0),
('20261124100000000103', '20261124100000000001', '录音任务-详情', NULL, NULL, 0, NULL, NULL, 2, 'speech:task:detail', '1', 3, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2026-11-24 09:30:00', NULL, NULL, 0, 0, '1', 0),
('20261124100000000104', '20261124100000000001', '录音任务-删除', NULL, NULL, 0, NULL, NULL, 2, 'speech:task:delete', '1', 4, 0, NULL, 1, 0, 0, 0, NULL, 'admin', '2026-11-24 09:30:00', NULL, NULL, 0, 0, '1', 0);

-- 2. 授权给管理员角色（同 flow/team/mail 脚本口径）
INSERT INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `data_rule_ids`, `operate_date`, `operate_ip`)
VALUES
('20261124100000000201', 'f6817f48af4fb3af11b9e8bf182f618b', '20261124100000000101', NULL, NOW(), '127.0.0.1'),
('20261124100000000202', 'f6817f48af4fb3af11b9e8bf182f618b', '20261124100000000102', NULL, NOW(), '127.0.0.1'),
('20261124100000000203', 'f6817f48af4fb3af11b9e8bf182f618b', '20261124100000000103', NULL, NOW(), '127.0.0.1'),
('20261124100000000204', 'f6817f48af4fb3af11b9e8bf182f618b', '20261124100000000104', NULL, NOW(), '127.0.0.1');
