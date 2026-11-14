-- Agent Collab Pilot 全量建表语句
-- MySQL 8.x / InnoDB / utf8mb4
-- 设计约束: 分片友好(MyCat2 预留) —— 热表显式分片键, 无 DB 自增依赖
--           所有 seq 由 Redis INCR 发号, id 由应用生成 uuid

-- ========== 租户与账号池 ==========
CREATE TABLE tenants (
  id         VARCHAR(36)  NOT NULL,
  name       VARCHAR(64)  NOT NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

-- 分片键: tenant_id (accounts 随租户走, 规模可控时也可不分片)
CREATE TABLE accounts (
  id                VARCHAR(36)  NOT NULL,
  tenant_id         VARCHAR(36)  NOT NULL,
  service           VARCHAR(32)  NOT NULL COMMENT 'chatgpt/claude/gemini/...',
  label             VARCHAR(64)  NOT NULL COMMENT '人工可读标识',
  container_ref     VARCHAR(128) NOT NULL COMMENT '容器名/网关连接项',
  proxy_url         VARCHAR(256) DEFAULT NULL COMMENT '独立出口代理',
  status            VARCHAR(16)  NOT NULL DEFAULT 'provisioned'
                    COMMENT 'provisioned/needs_login/healthy/in_use/dead',
  last_health_check DATETIME     DEFAULT NULL,
  created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tenant (tenant_id, status)
);

-- ========== 设备与准入 ==========
CREATE TABLE devices (
  id          VARCHAR(36)  NOT NULL,
  user_id     VARCHAR(36)  NOT NULL,
  fingerprint VARCHAR(128) NOT NULL,
  pubkey      VARCHAR(512) NOT NULL COMMENT '应用层设备密钥',
  wg_pubkey   VARCHAR(128) DEFAULT NULL COMMENT 'WireGuard 公钥',
  assigned_ip VARCHAR(16)  DEFAULT NULL COMMENT '10.10.0.x 分配',
  name        VARCHAR(64)  NOT NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'pending'
              COMMENT 'pending/approved/revoked',
  first_seen  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_seen   DATETIME     DEFAULT NULL,
  approved_by VARCHAR(36)  DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_fp (fingerprint),
  KEY idx_user (user_id)
);

CREATE TABLE device_sessions (
  token      VARCHAR(64) NOT NULL,
  device_id  VARCHAR(36) NOT NULL,
  user_id    VARCHAR(36) NOT NULL,
  issued_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at DATETIME    NOT NULL,
  revoked    TINYINT(1)  NOT NULL DEFAULT 0,
  PRIMARY KEY (token),
  KEY idx_device (device_id)
);

-- ========== IM (写扩散) ==========
CREATE TABLE conversations (
  id          VARCHAR(36) NOT NULL,
  type        VARCHAR(8)  NOT NULL COMMENT 'single/group',
  name        VARCHAR(64) DEFAULT NULL,
  last_msg_id VARCHAR(36) DEFAULT NULL,
  updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

CREATE TABLE conv_members (
  conv_id      VARCHAR(36) NOT NULL,
  user_id      VARCHAR(36) NOT NULL,
  last_read_seq BIGINT     NOT NULL DEFAULT 0,
  pinned       TINYINT(1)  NOT NULL DEFAULT 0,
  muted        TINYINT(1)  NOT NULL DEFAULT 0,
  joined_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (conv_id, user_id),
  KEY idx_user (user_id)
);

-- 分片键: conv_id
CREATE TABLE messages (
  id          VARCHAR(36) NOT NULL,
  conv_id     VARCHAR(36) NOT NULL,
  conv_seq    BIGINT      NOT NULL COMMENT '会话内序号, Redis 发号',
  sender_id   VARCHAR(36) NOT NULL,
  type        VARCHAR(16) NOT NULL DEFAULT 'text',
  content_ref VARCHAR(256) NOT NULL COMMENT '内容存客户端, 此处为定位引用',
  audit_status VARCHAR(16) NOT NULL DEFAULT 'none'
               COMMENT 'none/passed/flagged/blocked',
  recalled    TINYINT(1)  NOT NULL DEFAULT 0,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_conv_seq (conv_id, conv_seq),
  KEY idx_sender (sender_id, created_at)
);

-- 热表. 分片键: user_id. seq 为每用户独立游标(Redis 发号)
CREATE TABLE inbox (
  user_id    VARCHAR(36) NOT NULL,
  seq        BIGINT      NOT NULL,
  msg_id     VARCHAR(36) NOT NULL,
  conv_id    VARCHAR(36) NOT NULL,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted    TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '本端墓碑',
  PRIMARY KEY (user_id, seq),
  KEY idx_msg (msg_id)
);

-- ========== 合规审计 ==========
-- audit_rules 为全局表 (MyCat2 配置 broadcast)
CREATE TABLE audit_rules (
  id         VARCHAR(36)  NOT NULL,
  scope      VARCHAR(16)  NOT NULL COMMENT 'chat/ai/tool',
  rule_type  VARCHAR(16)  NOT NULL COMMENT 'keyword/regex/model',
  pattern    VARCHAR(512) NOT NULL,
  action     VARCHAR(8)   NOT NULL COMMENT 'block/flag',
  enabled    TINYINT(1)   NOT NULL DEFAULT 1,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);

-- 分片键: user_id (auditor 归属)
CREATE TABLE audit_results (
  msg_id         VARCHAR(36) NOT NULL,
  rule_id        VARCHAR(36) NOT NULL,
  auditor_device VARCHAR(36) NOT NULL,
  verdict        VARCHAR(8)  NOT NULL COMMENT 'pass/flag/block',
  hmac           VARCHAR(128) NOT NULL COMMENT '客户端签名, 服务器验签',
  audited_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (msg_id, rule_id),
  KEY idx_device (auditor_device, audited_at)
);

-- ========== Agent 任务 (注册在 Nacos, 此表管任务语义) ==========
CREATE TABLE agent_tasks (
  id             VARCHAR(36)  NOT NULL,
  capability_req VARCHAR(128) NOT NULL COMMENT '如 web-chatgpt',
  payload        JSON         NOT NULL,
  status         VARCHAR(16)  NOT NULL DEFAULT 'queued'
                 COMMENT 'queued/claimed/running/done/failed',
  agent_id       VARCHAR(64)  DEFAULT NULL,
  lease_until    DATETIME     DEFAULT NULL,
  result         JSON         DEFAULT NULL,
  created_by     VARCHAR(36)  NOT NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                 ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_claim (status, capability_req),
  KEY idx_lease (lease_until)
);
