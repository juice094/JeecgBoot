-- =============================================================================
-- 权限确权治理 - P1：到期提醒去重标记 + 到期扫描任务注册
-- 依据：docs/permission-governance/PERMISSION-GOVERNANCE.md §11 P1
-- 号说明：接 V3.9.6_12__permission_grant.sql 之后取 _13
-- 注意：试点环境 Flyway 被排除时，本脚本需手动执行并登记 flyway_schema_history。
-- =============================================================================

-- 1. 到期提醒去重标记（0=未提醒 1=已提醒；授权续期后由业务重置为 0）
ALTER TABLE `sys_permission_grant`
  ADD COLUMN `expiring_notified` tinyint NOT NULL DEFAULT 0 COMMENT '到期提醒是否已发送: 0未发送 1已发送';

-- 2. 到期扫描任务（每 30 分钟：过期落库 + 到期前 3 天提醒）
--    status=0 正常；如需暂停改为 -1
INSERT INTO `sys_quartz_job` (`id`, `create_by`, `create_time`, `del_flag`, `update_by`, `update_time`, `job_class_name`, `cron_expression`, `parameter`, `description`, `status`)
VALUES ('20261119100000000301', 'admin', NOW(), 0, NULL, NULL,
        'org.jeecg.modules.quartz.job.PermissionGrantExpireJob', '0 0/30 * * * ?', '3',
        '权限确权-授权到期扫描（过期落库 + 到期前N天提醒，参数=提前天数）', 0);
