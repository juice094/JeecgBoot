-- word-sync 模块独立库初始化（db_wordsync）
-- 表结构依据 jeecg-module-word-sync 的 Mapper XML 逆向（tb_document/tb_version/tb_audit_log）
-- 2026-11-24 PR #27 合并后补：合入前任何环境均无该库 DDL 留痕

CREATE DATABASE IF NOT EXISTS db_wordsync DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE db_wordsync;

CREATE TABLE IF NOT EXISTS tb_document (
  document_id     BIGINT       AUTO_INCREMENT PRIMARY KEY,
  title           VARCHAR(512) NOT NULL,
  owner_id        BIGINT       NULL,
  current_version INT          NOT NULL DEFAULT 0,
  storage_key     VARCHAR(1024) NULL,
  status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS tb_version (
  version_id BIGINT        AUTO_INCREMENT PRIMARY KEY,
  doc_id     BIGINT        NOT NULL,
  version_no INT           NOT NULL,
  storage_key VARCHAR(1024) NULL,
  size       BIGINT        NULL,
  sha256     VARCHAR(128)  NULL,
  source     VARCHAR(64)   NULL,
  created_by BIGINT        NULL,
  created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_doc_version (doc_id, version_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS tb_audit_log (
  log_id     BIGINT      AUTO_INCREMENT PRIMARY KEY,
  doc_id     BIGINT      NULL,
  user_id    BIGINT      NULL,
  action     VARCHAR(64) NULL,
  detail     TEXT        NULL,
  ip         VARCHAR(64) NULL,
  trace_id   VARCHAR(64) NULL,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_doc_time (doc_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
