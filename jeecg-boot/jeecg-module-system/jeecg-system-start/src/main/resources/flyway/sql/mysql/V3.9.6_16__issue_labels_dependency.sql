-- =============================================================================
-- /issues P1 协作语义：标签体系 + 依赖阻塞 + claim/reassign 支撑（2026-11-23 施工）
-- 号说明：接 V3.9.6_15__team_approval_flow_sync.sql 之后取 _16
-- 试点环境 Flyway 自动装配被排除，本脚本手动执行并登记 flyway_schema_history（同 _15 口径）。
-- 执行实况（2026-11-23）：本脚本四项 DDL 已由主会话先行变更在开发库逐字落位
-- （show create table 核对一致），本脚本登记为 canonical 定义 + history 登记，
-- checksum 取文件真实值（逐行 CRC32，Flyway 7 口径）。
--
-- 变更内容：
--   1) team_bug 加 bug_no 缺陷序号（#B-n 展示编号；存量按 create_time 顺序回填，
--      新建由 TeamBugController.add 服务端赋值，口径同 task_no）
--   2) team_task 加 blocked 标记位（0/1；被未完成依赖阻塞时置 1，
--      六态/三态状态机零改动——blocked 只是派生展示位，重算入口见 TeamTaskDependencyService）
--   3) team_label + team_task_label：多标签体系（颜色 chips）；存量 tag 单字段保留不迁移（P2 再议）
--   4) team_task_dependency：任务依赖（task 依赖 depends_on 完成才可进入 doing；
--      depends_on 完成时重算解除 blocked）
--
-- 兼容性：新增列均有默认值、新表独立；team_task.tag 单字段不动。
-- 幂等性：ADD COLUMN / CREATE TABLE 非幂等，重复执行会报错，符合 Flyway 一次执行语义。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. team_bug 缺陷序号（#B-n 展示编号）
-- ---------------------------------------------------------------------------
ALTER TABLE team_bug
    ADD COLUMN bug_no INT DEFAULT NULL COMMENT '缺陷序号（#B-n 展示编号，服务端赋值，口径同 task_no）' AFTER description,
    ADD UNIQUE KEY uk_team_bug_no (bug_no);

-- 存量回填：按创建时间顺序编号（与 task_no「当前最大值+1」后续增长兼容）
SET @bug_seq := 0;
UPDATE team_bug SET bug_no = (@bug_seq := @bug_seq + 1) ORDER BY create_time, id;

-- ---------------------------------------------------------------------------
-- 2. team_task 阻塞标记位（状态机零改动，纯派生位）
-- ---------------------------------------------------------------------------
ALTER TABLE team_task ADD COLUMN blocked TINYINT(1) NOT NULL DEFAULT 0 COMMENT '阻塞标记 1=被未完成依赖阻塞 0=正常（派生位，见 team_task_dependency）' AFTER status;

-- ---------------------------------------------------------------------------
-- 3. 标签体系
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS team_label (
    id          VARCHAR(36)  NOT NULL COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '标签名',
    color       VARCHAR(16)  DEFAULT NULL COMMENT '展示色（十六进制，如 #1f883d）',
    create_by   VARCHAR(32)  DEFAULT NULL COMMENT '创建人',
    create_time DATETIME     DEFAULT NULL COMMENT '创建日期',
    update_by   VARCHAR(32)  DEFAULT NULL COMMENT '更新人',
    update_time DATETIME     DEFAULT NULL COMMENT '更新日期',
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_label_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队协作-标签';

CREATE TABLE IF NOT EXISTS team_task_label (
    id          VARCHAR(36) NOT NULL COMMENT '主键',
    task_id     VARCHAR(36) NOT NULL COMMENT 'team_task.id',
    label_id    VARCHAR(36) NOT NULL COMMENT 'team_label.id',
    create_time DATETIME    DEFAULT NULL COMMENT '创建日期',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_label (task_id, label_id),
    KEY idx_tasklabel_label (label_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队协作-任务标签关联';

-- ---------------------------------------------------------------------------
-- 4. 任务依赖/阻塞
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS team_task_dependency (
    id                   VARCHAR(36) NOT NULL COMMENT '主键',
    task_id              VARCHAR(36) NOT NULL COMMENT '依赖方任务 team_task.id',
    depends_on_task_id   VARCHAR(36) NOT NULL COMMENT '被依赖任务 team_task.id（其完成后依赖方才解锁）',
    create_time          DATETIME    DEFAULT NULL COMMENT '创建日期',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_dependency (task_id, depends_on_task_id),
    KEY idx_dependency_depends_on (depends_on_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='团队协作-任务依赖（blocked 派生位重算源）';
