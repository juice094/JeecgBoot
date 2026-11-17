package org.jeecg.modules.mail.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;

import org.junit.jupiter.api.Test;

/**
 * @Description: SmtpErrorClassifier 纯单元测试（接口文档 §7.2）
 * @Author: mail-dev
 * @Date: 2026-11-17
 */
class SmtpErrorClassifierTest {

    @Test
    void classify_null_returnsUnknown() {
        assertEquals("unknown", SmtpErrorClassifier.classify(null));
    }

    @Test
    void classify_authFailedException_returnsAuthCode() {
        assertEquals("smtp_auth_failed", SmtpErrorClassifier.classify(new AuthenticationFailedException()));
    }

    @Test
    void classify_timeoutMessage_returnsTimeoutCode() {
        assertEquals("smtp_timeout", SmtpErrorClassifier.classify(new MessagingException("Read timed out")));
    }

    @Test
    void classify_connectionRefused_returnsConnectionCode() {
        assertEquals("smtp_connection", SmtpErrorClassifier.classify(new MessagingException("Connection refused")));
        // 连接类特征优先于超时：JavaMail 连接失败文案自带 “; timeout NNNN” 配置值后缀
        assertEquals("smtp_connection", SmtpErrorClassifier.classify(new MessagingException("connect timeout")));
        assertEquals("smtp_connection", SmtpErrorClassifier.classify(
                new MessagingException("Couldn't connect to host, port: 1; timeout 10000")));
    }

    @Test
    void classify_otherMessage_returnsUnknown() {
        assertEquals("unknown", SmtpErrorClassifier.classify(new MessagingException("weird failure")));
    }

    @Test
    void classify_messageCaseInsensitive() {
        assertEquals("smtp_timeout", SmtpErrorClassifier.classify(new MessagingException("TIMEOUT waiting")));
    }

    @Test
    void isMessagingException_distinguishesLayer() {
        assertTrue(SmtpErrorClassifier.isMessagingException(new MessagingException("x")));
        assertFalse(SmtpErrorClassifier.isMessagingException(new IllegalStateException("x")));
    }
}
