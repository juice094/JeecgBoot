package org.jeecg.modules.mail.delivery;

/**
 * @Description: SMTP/IMAP 连接默认参数
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * pilot 固定口径，二期可配置化。
 */
public final class MailConnectionDefaults {

    /** 建连/读/写超时（毫秒） */
    public static final String TIMEOUT_MS = "10000";

    public static final String PROTOCOL_SMTP = "smtp";

    public static final String PROTOCOL_IMAP = "imap";

    private MailConnectionDefaults() {
    }
}
