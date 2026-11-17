package org.jeecg.modules.mail.enums;

/**
 * @Description: 员工邮箱写信模式枚举（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.1
 */
public enum MailSendMode {

    NEW("new"),
    REPLY("reply"),
    REPLY_ALL("replyAll"),
    FORWARD("forward");

    private final String code;

    MailSendMode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static MailSendMode fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (MailSendMode mode : values()) {
            if (mode.code.equalsIgnoreCase(code.trim())) {
                return mode;
            }
        }
        return null;
    }
}
