package org.jeecg.modules.mail.constant;

/**
 * @Description: OA 邮件-业务错误码常量（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.2
 *
 * 对齐接口文档 §7.1；错误码写入 system_mail_log.error_code 与日志/指标标签，
 * 对外仍走 jeecg Result.error(message) 业务文案。
 */
public final class MailErrorCodes {

    public static final String MAIL_TEMPLATE_NOT_FOUND = "MAIL_TEMPLATE_NOT_FOUND";
    public static final String MAIL_SEND_TEMPLATE_PARAM_MISS = "MAIL_SEND_TEMPLATE_PARAM_MISS";
    public static final String MAIL_ACCOUNT_DISABLED = "MAIL_ACCOUNT_DISABLED";
    public static final String MAIL_IMAP_CONNECT_FAILED = "MAIL_IMAP_CONNECT_FAILED";
    public static final String MAIL_SMTP_CONNECT_FAILED = "MAIL_SMTP_CONNECT_FAILED";
    public static final String MAIL_FOLDER_MAPPING_MISS = "MAIL_FOLDER_MAPPING_MISS";
    public static final String MAIL_AGENT_CONFIRM_REQUIRED = "MAIL_AGENT_CONFIRM_REQUIRED";
    public static final String MAIL_AGENT_TOKEN_EXPIRED = "MAIL_AGENT_TOKEN_EXPIRED";
    public static final String MAIL_IDEMPOTENT_CONFLICT = "MAIL_IDEMPOTENT_CONFLICT";
    public static final String MAIL_RETRY_LIMIT_EXCEEDED = "MAIL_RETRY_LIMIT_EXCEEDED";
    public static final String MAIL_INTERNAL_ERROR = "MAIL_INTERNAL_ERROR";

    /** 骨架专用：SMTP 投递未接入，明确区分于真实失败，避免误判为线上故障 */
    public static final String MAIL_SMTP_NOT_IMPLEMENTED = "MAIL_SMTP_NOT_IMPLEMENTED";

    private MailErrorCodes() {
    }
}
