-- =============================================================================
-- 团队协作 - 逻辑删除改造（2026-11-17，宿拍板 D-69a）
-- team_task / team_bug / team_approval 三表加 deleted 标志位（只 ALTER 不建表），
-- 实体侧统一 @TableLogic（MyBatis-Plus 逻辑删除），删除端点改逻辑删，对外契约不变。
-- 注意：Flyway 自动装配在试点环境被排除，本脚本需手动执行并登记 flyway_schema_history。
-- =============================================================================
ALTER TABLE team_task
  ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=已删' AFTER sys_tenant_id,
  ADD KEY idx_team_task_deleted (deleted);

ALTER TABLE team_bug
  ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=已删' AFTER sys_tenant_id,
  ADD KEY idx_team_bug_deleted (deleted);

ALTER TABLE team_approval
  ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=已删' AFTER sys_tenant_id,
  ADD KEY idx_team_approval_deleted (deleted);
