-- =============================================================================
-- OA 邮件体系 - 建表脚本（DRAFT，待 lead 取号）
-- 号说明：V3.9.6_7 已被 team_issue_detail 正式占用，本脚本暂定 _8；
--         若 lead 只批一个号且需与权限脚本合并，则把本文件与
--         V3.9.6_9__oa_mail_permissions.draft.sql 合并为一个 _8 脚本。
-- 落地方式：取号后改名为 V3.9.6_<号>__oa_mail_init.sql，移入
--           jeecg-system-start/src/main/resources/flyway/sql/mysql/。
-- 试点环境 Flyway 被排除，脚本需手动执行并登记 flyway_schema_history。
-- =============================================================================

-- 1. 系统发信账号
CREATE TABLE IF NOT EXISTS system_mail_account (
    id VARCHAR(32) NOT NULL COMMENT '主键',
    host VARCHAR(128) NOT NULL COMMENT 'SMTP 主机',
    port INT NOT NULL COMMENT 'SMTP 端口',
    username VARCHAR(128) NOT NULL COMMENT 'SMTP 用户名',
    password VARCHAR(512) NOT NULL COMMENT 'SMTP 密码（密文，AES）',
    ssl_enable TINYINT(1) DEFAULT 0 COMMENT '是否 SSL',
    starttls_enable TINYINT(1) DEFAULT 0 COMMENT '是否 STARTTLS',
    from_address VARCHAR(128) NOT NULL COMMENT '发件人地址',
    from_name VARCHAR(128) DEFAULT NULL COMMENT '发件人显示名',
    status INT DEFAULT 1 COMMENT '状态：1 启用 / 0 停用',
    create_by VARCHAR(50) DEFAULT NULL,
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(50) DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    sys_org_code VARCHAR(64) DEFAULT NULL,
    sys_tenant_id INT DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'OA邮件-系统发信账号';

-- 2. 系统发信模板
CREATE TABLE IF NOT EXISTS system_mail_template (
    id VARCHAR(32) NOT NULL COMMENT '主键',
    code VARCHAR(128) NOT NULL COMMENT '模板编码（全局唯一）',
    name VARCHAR(128) NOT NULL COMMENT '模板名称',
    account_id VARCHAR(32) DEFAULT NULL COMMENT '发送账号 id',
    title VARCHAR(512) DEFAULT NULL COMMENT '标题模板',
    content TEXT COMMENT '正文模板（HTML）',
    params TEXT COMMENT '占位符 JSON 数组（保存时自动扫描 {key} 生成）',
    status INT DEFAULT 1 COMMENT '状态：1 启用 / 0 停用',
    create_by VARCHAR(50) DEFAULT NULL,
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(50) DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    sys_org_code VARCHAR(64) DEFAULT NULL,
    sys_tenant_id INT DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mail_template_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'OA邮件-系统发信模板';

-- 3. 系统发信日志
CREATE TABLE IF NOT EXISTS system_mail_log (
    id VARCHAR(32) NOT NULL COMMENT '主键',
    account_id VARCHAR(32) DEFAULT NULL COMMENT '发送账号 id',
    template_code VARCHAR(128) DEFAULT NULL COMMENT '模板编码',
    to_mails VARCHAR(2000) DEFAULT NULL COMMENT '收件人（逗号分隔，仅管理端可见）',
    to_hash VARCHAR(16) DEFAULT NULL COMMENT '收件人指纹（SHA-256 前 8 位）',
    cc_mails VARCHAR(2000) DEFAULT NULL COMMENT '抄送',
    bcc_mails VARCHAR(2000) DEFAULT NULL COMMENT '密送',
    template_title VARCHAR(512) DEFAULT NULL COMMENT '标题快照',
    template_content TEXT COMMENT '正文快照',
    send_status INT NOT NULL DEFAULT 0 COMMENT '发送状态：0 待发送/1 发送中/2 成功/3 失败/4 死信',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    error_code VARCHAR(64) DEFAULT NULL COMMENT '结构化错误码',
    error_msg VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    trace_id VARCHAR(64) DEFAULT NULL COMMENT '链路 id',
    idempotency_key VARCHAR(128) DEFAULT NULL COMMENT '幂等键（唯一）',
    create_by VARCHAR(50) DEFAULT NULL,
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(50) DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    sys_org_code VARCHAR(64) DEFAULT NULL,
    sys_tenant_id INT DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mail_log_idem (idempotency_key),
    KEY idx_mail_log_status_time (send_status, update_time),
    KEY idx_mail_log_trace (trace_id),
    KEY idx_mail_log_template_time (template_code, create_time),
    KEY idx_mail_log_to_hash (to_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'OA邮件-系统发信日志';

-- 4. 员工邮箱账号
CREATE TABLE IF NOT EXISTS oa_mail_account (
    id VARCHAR(32) NOT NULL COMMENT '主键',
    email VARCHAR(128) NOT NULL COMMENT '邮箱地址',
    imap_host VARCHAR(128) DEFAULT NULL COMMENT 'IMAP 主机',
    imap_port INT DEFAULT NULL COMMENT 'IMAP 端口',
    imap_ssl TINYINT(1) DEFAULT 0 COMMENT 'IMAP 是否 SSL',
    imap_username VARCHAR(128) DEFAULT NULL COMMENT 'IMAP 用户名',
    imap_password VARCHAR(512) DEFAULT NULL COMMENT 'IMAP 密码（密文）',
    smtp_host VARCHAR(128) DEFAULT NULL COMMENT 'SMTP 主机',
    smtp_port INT DEFAULT NULL COMMENT 'SMTP 端口',
    smtp_ssl TINYINT(1) DEFAULT 0 COMMENT 'SMTP 是否 SSL',
    smtp_username VARCHAR(128) DEFAULT NULL COMMENT 'SMTP 用户名',
    smtp_password VARCHAR(512) DEFAULT NULL COMMENT 'SMTP 密码（密文）',
    test_status INT DEFAULT 0 COMMENT '测连状态：1 通过 / 0 未通过',
    last_sync_time DATETIME DEFAULT NULL COMMENT '最近同步时间',
    create_by VARCHAR(50) DEFAULT NULL,
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(50) DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    sys_org_code VARCHAR(64) DEFAULT NULL,
    sys_tenant_id INT DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_oa_mail_account_email (email, sys_tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'OA邮件-员工邮箱账号';

-- 5. 员工邮箱消息索引（不存正文）
CREATE TABLE IF NOT EXISTS oa_mail_message_index (
    id VARCHAR(32) NOT NULL COMMENT '主键',
    account_id VARCHAR(32) NOT NULL COMMENT '员工邮箱账号 id',
    canonical_folder VARCHAR(32) NOT NULL COMMENT '规范文件夹',
    imap_uid BIGINT DEFAULT NULL COMMENT 'IMAP UID',
    message_id VARCHAR(255) DEFAULT NULL COMMENT 'Message-ID',
    subject VARCHAR(1000) DEFAULT NULL COMMENT '主题',
    from_name VARCHAR(255) DEFAULT NULL COMMENT '发件人显示名',
    from_address_hash VARCHAR(64) DEFAULT NULL COMMENT '发件人地址 hash',
    to_summary VARCHAR(2000) DEFAULT NULL COMMENT '收件人摘要',
    received_time DATETIME DEFAULT NULL COMMENT '接收时间',
    read_flag TINYINT(1) DEFAULT 0 COMMENT '是否已读',
    has_attachment TINYINT(1) DEFAULT 0 COMMENT '是否有附件',
    snippet VARCHAR(1000) DEFAULT NULL COMMENT '摘要片段',
    create_by VARCHAR(50) DEFAULT NULL,
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(50) DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    sys_org_code VARCHAR(64) DEFAULT NULL,
    sys_tenant_id INT DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mail_index_uid (account_id, canonical_folder, imap_uid),
    KEY idx_mail_index_list (account_id, canonical_folder, received_time DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'OA邮件-员工邮箱消息索引';

-- 6. 文件夹同步状态（canonical 空=未识别，不静默跳过）
CREATE TABLE IF NOT EXISTS oa_mail_folder_state (
    id VARCHAR(32) NOT NULL COMMENT '主键',
    account_id VARCHAR(32) NOT NULL COMMENT '员工邮箱账号 id',
    remote_name VARCHAR(255) NOT NULL COMMENT '远端文件夹名',
    canonical VARCHAR(32) DEFAULT NULL COMMENT '规范文件夹；空=未识别，需人工映射',
    last_uid BIGINT DEFAULT NULL COMMENT '同步游标（error 时不回退，下次续跑）',
    create_by VARCHAR(50) DEFAULT NULL,
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(50) DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    sys_org_code VARCHAR(64) DEFAULT NULL,
    sys_tenant_id INT DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mail_folder_remote (account_id, remote_name),
    KEY idx_mail_folder_canonical (account_id, canonical)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'OA邮件-文件夹同步状态';

-- 7. 邮件与业务关联
CREATE TABLE IF NOT EXISTS oa_mail_biz_link (
    id VARCHAR(32) NOT NULL COMMENT '主键',
    message_index_id VARCHAR(32) NOT NULL COMMENT '邮件索引 id',
    biz_type VARCHAR(64) NOT NULL COMMENT '业务类型（team_task / team_approval 等）',
    biz_id VARCHAR(64) NOT NULL COMMENT '业务单据 id',
    create_by VARCHAR(50) DEFAULT NULL,
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(50) DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    sys_org_code VARCHAR(64) DEFAULT NULL,
    sys_tenant_id INT DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_mail_biz_link_msg (message_index_id),
    KEY idx_mail_biz_link_biz (biz_type, biz_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'OA邮件-邮件与业务关联';
