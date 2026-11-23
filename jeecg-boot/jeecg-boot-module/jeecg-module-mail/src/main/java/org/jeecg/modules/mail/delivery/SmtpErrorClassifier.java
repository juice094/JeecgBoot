package org.jeecg.modules.mail.delivery;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;

/**
 * @Description: SMTP 错误分类（接口文档 §7.2）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 分类写入 system_mail_log.error_code 与日志/指标标签：
 * smtp_connection / smtp_auth_failed / smtp_timeout / recipient_refused / content_rejected / unknown。
 */
public final class SmtpErrorClassifier {

    private static final String CODE_CONNECTION = "connection";
    private static final String CODE_AUTH_FAILED = "auth_failed";
    private static final String CODE_TIMEOUT = "timeout";
    private static final String CODE_UNKNOWN = "unknown";

    private static final String MARK_TIMEOUT = "timeout";
    private static final String MARK_TIMED_OUT = "timed out";
    private static final String MARK_CONNECT = "connect";
    private static final String MARK_REFUSED = "refused";
    private static final String MARK_UNREACHABLE = "unreachable";

    private SmtpErrorClassifier() {
    }

    /**
     * 对 SMTP 会话异常分类；入参异常信息为空时返回 unknown。
     */
    public static String classify(Exception e) {
        return classifyWithPrefix(e, "smtp_");
    }

    /**
     * 按协议前缀分类（smtp_/imap_），IMAP 侧沿用同型分类口径（立项版重对点）。
     *
     * 特征优先级：连接类先于超时——JavaMail 的连接失败文案自带 “; timeout NNNN”
     * 后缀（配置值），超时优先会把所有连接失败误判为超时。
     */
    public static String classifyWithPrefix(Exception e, String prefix) {
        if (e == null) {
            return CODE_UNKNOWN;
        }
        if (e instanceof AuthenticationFailedException) {
            return prefix + CODE_AUTH_FAILED;
        }
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains(MARK_CONNECT) || msg.contains(MARK_REFUSED) || msg.contains(MARK_UNREACHABLE)) {
            return prefix + CODE_CONNECTION;
        }
        if (msg.contains(MARK_TIMEOUT) || msg.contains(MARK_TIMED_OUT)) {
            return prefix + CODE_TIMEOUT;
        }
        return CODE_UNKNOWN;
    }

    /**
     * 判断异常链是否属于 Jakarta Mail 网络/协议层（测连接口需要区分"连不上"与"业务异常"）。
     */
    public static boolean isMessagingException(Exception e) {
        return e instanceof MessagingException;
    }
}
