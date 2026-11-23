-- 权限体系完善（范围 C：通用模板化）—— 幂等，可重复执行
-- 日期：2026-11-20；目标库：jeecg-boot（本机 dev）
-- 内容：
--   1) 全员补"系统用户标配角色"（设计意图：所有用户默认拥有；本部署租户关闭，无自动合并）
--   2) zhoujingxiao 镜像 admin：管理员 + Vue3全角色（管理员角色不含 system:online:list，需 vue3 角色补齐）
--   3) 角色分层：新建"业务主管"(team_manager)，从"蓝鸥成员"剥离审批/审计/邮件日志管理权限，赋给 lanou_mgr/lanou_vp

-- ============ 1. 全员补标配角色 ============
INSERT INTO sys_user_role (id, user_id, role_id)
SELECT REPLACE(UUID(), '-', ''), u.id, 'ee8626f80f7c2619917b6236f3a7f02b'
FROM sys_user u
WHERE u.del_flag = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur
    WHERE ur.user_id = u.id AND ur.role_id = 'ee8626f80f7c2619917b6236f3a7f02b'
  );

-- ============ 2. zhoujingxiao 赋管理员 + Vue3全角色 ============
INSERT INTO sys_user_role (id, user_id, role_id)
SELECT REPLACE(UUID(), '-', ''), u.id, 'f6817f48af4fb3af11b9e8bf182f618b'
FROM sys_user u
WHERE u.username = 'zhoujingxiao'
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur
    WHERE ur.user_id = u.id AND ur.role_id = 'f6817f48af4fb3af11b9e8bf182f618b'
  );

INSERT INTO sys_user_role (id, user_id, role_id)
SELECT REPLACE(UUID(), '-', ''), u.id, '1501570619841810433'
FROM sys_user u
WHERE u.username = 'zhoujingxiao'
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur
    WHERE ur.user_id = u.id AND ur.role_id = '1501570619841810433'
  );

-- ============ 3. 角色分层：业务主管 ============
-- 3.1 建角色（幂等：按 role_code 判重）
INSERT INTO sys_role (id, role_name, role_code, description, create_by, create_time, update_by, update_time)
SELECT 'b7e3d1a2c4f548e6a9b0d3e5f7a1c8d2', '业务主管', 'team_manager',
       'team 域审批/审计/邮件日志管理权限（从蓝鸥成员剥离，通用模板管理层）', 'admin', NOW(), 'admin', NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = 'team_manager');

-- 3.2 主管权限（审批通过/拒绝、审计日志、邮件日志统计与重试）
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT REPLACE(UUID(), '-', ''), 'b7e3d1a2c4f548e6a9b0d3e5f7a1c8d2', p.id
FROM sys_permission p
WHERE p.perms IN ('team:approval:approve', 'team:approval:reject', 'team:audit:list', 'team:mail:log:stat', 'team:mail:log:retry')
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp
    WHERE rp.role_id = 'b7e3d1a2c4f548e6a9b0d3e5f7a1c8d2' AND rp.permission_id = p.id
  );

-- 3.3 从蓝鸥成员剥离上述权限（成员保留：提交申请、查看列表/详情/步骤链）
DELETE rp FROM sys_role_permission rp
JOIN sys_permission p ON p.id = rp.permission_id
WHERE rp.role_id = '3996bc4ee31f11f1a4b200155d9be2ce'
  AND p.perms IN ('team:approval:approve', 'team:approval:reject', 'team:audit:list', 'team:mail:log:stat', 'team:mail:log:retry');

-- 3.4 主管角色赋给 lanou_mgr / lanou_vp
INSERT INTO sys_user_role (id, user_id, role_id)
SELECT REPLACE(UUID(), '-', ''), u.id, 'b7e3d1a2c4f548e6a9b0d3e5f7a1c8d2'
FROM sys_user u
WHERE u.username IN ('lanou_mgr', 'lanou_vp')
  AND NOT EXISTS (
    SELECT 1 FROM sys_user_role ur
    WHERE ur.user_id = u.id AND ur.role_id = 'b7e3d1a2c4f548e6a9b0d3e5f7a1c8d2'
  );
