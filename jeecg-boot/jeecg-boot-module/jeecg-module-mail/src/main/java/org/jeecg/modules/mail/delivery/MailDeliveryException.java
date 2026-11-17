package org.jeecg.modules.mail.delivery;

/**
 * @Description: 邮件投递异常（骨架 V0.3）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 *
 * 携带结构化错误码（smtp_connection / smtp_auth_failed 等），写入 system_mail_log.error_code。
 */
public class MailDeliveryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public MailDeliveryException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MailDeliveryException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
