package org.jeecg.modules.mail.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jeecg.modules.mail.dto.ConnectionTestResult;
import org.jeecg.modules.mail.dto.MailServerConfig;
import org.junit.jupiter.api.Test;

/**
 * @Description: ImapSmtpConnector 测连单测
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 验证分级：负路径为真实网络失败（127.0.0.1:1 连接被拒）+ 分类正确性；
 * 正路径（真实邮箱握手）无可用账号，跳过原因如实标注（见 B-1 施工记录）。
 */
class ImapSmtpConnectorTest {

    private MailServerConfig deadConfig(int port) {
        MailServerConfig cfg = new MailServerConfig();
        cfg.setHost("127.0.0.1");
        cfg.setPort(port);
        cfg.setSsl(false);
        cfg.setUsername("probe");
        cfg.setPassword("probe");
        return cfg;
    }

    @Test
    void testImap_unreachableHost_returnsImapConnectionCode() {
        ImapSmtpConnector connector = new ImapSmtpConnector();
        ConnectionTestResult result = connector.testImap(deadConfig(1));
        assertFalse(result.isImapOk());
        assertEquals("imap_connection", result.getImapErrorCode());
    }

    @Test
    void testSmtp_unreachableHost_returnsSmtpConnectionCode() {
        ImapSmtpConnector connector = new ImapSmtpConnector();
        ConnectionTestResult result = connector.testSmtp(deadConfig(1));
        assertFalse(result.isSmtpOk());
        assertEquals("smtp_connection", result.getSmtpErrorCode());
    }

    @Test
    void testBoth_bothDead_errorCodeIsFirstFailingSide() {
        ImapSmtpConnector connector = new ImapSmtpConnector();
        ConnectionTestResult result = connector.testBoth(deadConfig(1), deadConfig(2));
        assertFalse(result.isImapOk());
        assertFalse(result.isSmtpOk());
        assertEquals("imap_connection", result.getImapErrorCode());
        assertEquals("smtp_connection", result.getSmtpErrorCode());
        assertEquals("imap_connection", result.getErrorCode());
        assertTrue(result.getLatencyMs() >= 0);
    }
}
