-- =============================================================================
-- 团队协作 - 审批链双写 Flowable（W3 施工）
-- 号说明：接 V3.9.6_14__flow_permissions.sql 之后取 _15
-- 试点环境 Flyway 自动装配被排除，本脚本手动执行并登记 flyway_schema_history（同 _10 口径）。
--
-- 变更内容：
--   1) team_approval 加 process_instance_id：team 单据 ↔ 引擎实例的双向锚定
--      （引擎侧 businessKey=申请单号，team 侧冗余实例 ID 便于直连观测）
--   2) 新增对账表 team_approval_flow_recon：每次双写动作后登记 team 状态 vs 引擎状态，
--      diff_flag=1 即不一致清单数据源（并行期以 team 表为准，引擎侧只观测，charter R2）
--
-- 兼容性：新增列可空、新表独立；存量数据不回填（无实例 = 不对账）。
-- 幂等性：ADD COLUMN / CREATE TABLE 非幂等，重复执行会报错，符合 Flyway 一次执行语义。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. team_approval 挂接流程实例
-- ---------------------------------------------------------------------------
ALTER TABLE team_approval
    ADD COLUMN process_instance_id VARCHAR(64) DEFAULT NULL COMMENT 'Flowable 流程实例 ID（W3 双写锚定，businessKey=code）' AFTER status,
    ADD KEY idx_approval_procinst (process_instance_id);

-- ---------------------------------------------------------------------------
-- 2. 双写对账表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS team_approval_flow_recon (
    id                 VARCHAR(36)  NOT NULL COMMENT '主键',
    approval_id        VARCHAR(36)  NOT NULL COMMENT 'team_approval.id',
    approval_code      VARCHAR(32)  NOT NULL COMMENT '申请单号（OA-YYYYMMDD-NNN）',
    process_instance_id VARCHAR(64) DEFAULT NULL COMMENT '流程实例 ID（引擎未挂接时为空）',
    team_status        VARCHAR(16)  NOT NULL COMMENT 'team 侧状态（TeamApprovalStatus 落库码）',
    team_current_step  INT          DEFAULT NULL COMMENT 'team 侧当前步骤序号',
    engine_status      VARCHAR(64)  DEFAULT NULL COMMENT '引擎侧观测快照：RUNNING:<节点> / FINISHED:<endEvent> / DELETED / NOT_FOUND / SYNC_FAILED',
    diff_flag          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '不一致标记 1=不一致 0=一致',
    detail             VARCHAR(1024) DEFAULT NULL COMMENT '差异/失败说明（同步异常时记录异常消息）',
    create_time        DATETIME     DEFAULT NULL COMMENT '创建日期',
    update_time        DATETIME     DEFAULT NULL COMMENT '更新日期',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recon_approval (approval_id),
    KEY idx_recon_diff (diff_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队协作-审批双写对账（team 状态 vs 引擎实例状态）';
