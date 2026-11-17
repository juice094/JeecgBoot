-- =============================================================================
-- 团队协作 - 工作项详情页支撑（GitHub Issues 架构对齐，2026-11-17）
-- 设计取舍：
--   1. 不新建事件表，复用 team_audit_log 作为时间线数据源（其设计本就是只追加不改写）
--   2. 评论独立成表，与事件流分离（评论有正文与编辑，事件只有元数据）
--   3. task_no 为租户内序号，用于 #12 式引用；应用层生成 + 唯一索引兜底
-- 注意：Flyway 在试点环境被排除，本脚本需手动执行并登记 flyway_schema_history
-- =============================================================================

-- 1. 任务表：加租户内序号
ALTER TABLE team_task
  ADD COLUMN task_no INT DEFAULT NULL COMMENT '租户内序号，用于 #12 式引用' AFTER title,
  ADD UNIQUE KEY uk_team_task_no (sys_tenant_id, task_no);

-- 2. 评论表
CREATE TABLE IF NOT EXISTS team_task_comment (
  id            VARCHAR(36)  NOT NULL COMMENT '主键',
  task_id       VARCHAR(36)  NOT NULL COMMENT '任务ID',
  author_id     VARCHAR(36)  DEFAULT NULL COMMENT '作者(sys_user.id)',
  author_name   VARCHAR(64)  NOT NULL COMMENT '作者姓名冗余',
  body          TEXT         NOT NULL COMMENT '评论正文(Markdown 原文)',
  create_by     VARCHAR(32)  DEFAULT NULL COMMENT '创建人登录名',
  create_time   DATETIME     DEFAULT NULL COMMENT '创建日期',
  update_by     VARCHAR(32)  DEFAULT NULL COMMENT '更新人登录名',
  update_time   DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新日期',
  sys_org_code  VARCHAR(64)  DEFAULT NULL COMMENT '所属部门',
  sys_tenant_id VARCHAR(10)  DEFAULT NULL COMMENT '租户编号',
  deleted       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除 0=正常 1=已删',
  PRIMARY KEY (id),
  KEY idx_comment_task (task_id, create_time),
  KEY idx_comment_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队协作-任务评论';

-- 3. 审计日志：加时间线查询索引（按业务实体 + 时间倒序拉取）
ALTER TABLE team_audit_log
  ADD KEY idx_audit_timeline (biz_type, biz_id, create_time);
