package org.jeecg.modules.mail.enums;

/**
 * @Description: 邮件发送状态机枚举（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.1
 *
 * 口径（OA 邮件体系需求文档 §4.3）：
 *   0 待发送 → 1 发送中 → 2 成功
 *   1 发送中 → 3 失败
 *   3 失败 → 1 重试
 *   3 失败 → 4 死信（重试超限）
 */
public enum MailSendStatus {

    PENDING(0, "待发送"),
    SENDING(1, "发送中"),
    SUCCESS(2, "成功"),
    FAILED(3, "失败"),
    DEAD(4, "死信");

    private final int code;
    private final String label;

    MailSendStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean canTransitTo(MailSendStatus target) {
        if (target == null) {
            return false;
        }
        switch (this) {
            case PENDING:
                return target == SENDING;
            case SENDING:
                return target == SUCCESS || target == FAILED;
            case FAILED:
                return target == SENDING || target == DEAD;
            default:
                return false;
        }
    }

    public static MailSendStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (MailSendStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
