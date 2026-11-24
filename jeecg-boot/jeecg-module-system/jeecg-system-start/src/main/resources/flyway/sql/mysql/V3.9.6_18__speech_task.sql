-- =============================================================================
-- 录音转写任务表（speech_meeting_task）——PR #27 合入后补（2026-11-24 编排层）
-- 号说明：接 V3.9.6_17__speech_permissions.sql 之后取 _18
-- 背景：speech 模块（Ym3699 feature-yy-workbench）合流未随附建表 DDL，
--       启动验证实证 speech/task/page 500（Table 'jeecg-boot.speech_meeting_task' doesn't exist）。
-- 结构依据：org.jeecg.modules.speech.entity.SpeechTask（@TableName + JeecgEntity 基类字段）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `speech_meeting_task` (
  `id`              VARCHAR(36)  NOT NULL COMMENT '主键',
  `create_by`       VARCHAR(50)  NULL COMMENT '创建人',
  `create_time`     DATETIME     NULL COMMENT '创建日期',
  `update_by`       VARCHAR(50)  NULL COMMENT '更新人',
  `update_time`     DATETIME     NULL COMMENT '更新日期',
  `audio_file_id`   VARCHAR(64)  NULL COMMENT '文件记录ID',
  `audio_file_path` VARCHAR(1024) NULL COMMENT '音频文件路径',
  `task_status`     VARCHAR(32)  NOT NULL DEFAULT 'pending' COMMENT '任务状态：pending/processing/success/fail',
  `transcribe_text` LONGTEXT     NULL COMMENT '转写全文',
  `summary_json`    LONGTEXT     NULL COMMENT '会议纪要JSON字符串',
  `fail_reason`     TEXT         NULL COMMENT '失败原因',
  `finish_time`     DATETIME     NULL COMMENT '完成时间',
  `tenant_id`       INT          NULL COMMENT '租户ID',
  `del_flag`        INT          NOT NULL DEFAULT 0 COMMENT '删除标记：0正常 1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_task_status` (`task_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '语音转写任务';
