-- =============================================================================
-- 存量授权回填（手动执行，不属于 Flyway 迁移）
-- 依据：docs/permission-governance/PERMISSION-GOVERNANCE.md §5.3 P1/P2
--
-- 为什么不在 flyway/ 下：本脚本会把 sys_role_permission 的存量行复制进
-- sys_permission_grant，属于一次性治理动作。自动执行会在每次新环境初始化时
-- 重复生成历史授权记录，且行数与角色×权限乘积同阶，风险高于收益。
--
-- 执行前置条件：
--   1) jeecg.permission.grant.enabled = true（读路径已并入 grants）
--   2) 已在预发环境验证过读路径 UNION 后权限集合与现状一致
--   3) 建议在低峰期执行，执行前备份 sys_permission_grant
--
-- 回填口径：
--   subject_type = 'ROLE'，subject_id = role_id，source = 'MIGRATION'
--   status = 'active'，valid_from = NOW()，valid_until = NULL（长期）
--   grant_no = 'MIG-' || role_id 前 8 位 || '-' || permission_id 前 8 位
-- 幂等：同一 (subject, permission) 已存在 MIGRATION 行时跳过
-- =============================================================================

INSERT INTO `sys_permission_grant`
  (`id`, `grant_no`, `subject_type`, `subject_id`, `permission_id`, `data_rule_ids`,
   `scope_type`, `status`, `valid_from`, `valid_until`, `apply_reason`, `applicant`,
   `source`, `expiring_notified`, `create_by`, `create_time`, `deleted`)
SELECT
  REPLACE(UUID(), '-', ''),
  CONCAT('MIG-', LEFT(rp.role_id, 8), '-', LEFT(rp.permission_id, 8)),
  'ROLE',
  rp.role_id,
  rp.permission_id,
  rp.data_rule_ids,
  'GLOBAL',
  'active',
  NOW(),
  NULL,
  '存量授权回填（sys_role_permission）',
  'system',
  'MIGRATION',
  0,
  'system',
  NOW(),
  0
FROM `sys_role_permission` rp
WHERE NOT EXISTS (
  SELECT 1 FROM `sys_permission_grant` g
  WHERE g.subject_type = 'ROLE'
    AND g.subject_id = rp.role_id
    AND g.permission_id = rp.permission_id
    AND g.source = 'MIGRATION'
    AND g.deleted = 0
);

-- 校验：回填后两侧计数应一致（差异行需人工确认）
-- SELECT
--   (SELECT COUNT(*) FROM sys_role_permission) AS legacy_rows,
--   (SELECT COUNT(*) FROM sys_permission_grant WHERE source='MIGRATION' AND deleted=0) AS migrated_rows;
