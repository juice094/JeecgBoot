package org.jeecg.modules.mail.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.mail.entity.SystemMailAccount;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * @Description: AccountMailSenderFactory 纯单元测试（R4 动态 JavaMailSenderImpl）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 锁定口径（需求文档 A-1 / §4.3）：多发送账号按库记录构造投递器；
 * 账号修改后必须 evict，否则旧连接参数残留。
 */
class AccountMailSenderFactoryTest {

    private SystemMailAccount account(String id) {
        SystemMailAccount account = new SystemMailAccount();
        account.setId(id);
        account.setHost("smtp.example.com");
        account.setPort(587);
        account.setUsername("noreply@example.com");
        account.setSslEnable(false);
        account.setStarttlsEnable(true);
        return account;
    }

    @Test
    void buildSender_mapsAccountConfig() {
        SystemMailAccount account = account("a1");
        JavaMailSenderImpl sender = AccountMailSenderFactory.buildSender(account, "plain-pass");
        assertEquals("smtp.example.com", sender.getHost());
        assertEquals(587, sender.getPort());
        assertEquals("noreply@example.com", sender.getUsername());
        assertEquals("plain-pass", sender.getPassword());
        Properties props = sender.getJavaMailProperties();
        assertEquals("true", props.getProperty("mail.smtp.auth"));
        assertEquals("true", props.getProperty("mail.smtp.starttls.enable"));
        // sslEnable=false 时不应出现 SSL 开关
        assertEquals(null, props.getProperty("mail.smtp.ssl.enable"));
    }

    @Test
    void buildSender_sslEnable_setsSslProperty() {
        SystemMailAccount account = account("a2");
        account.setSslEnable(true);
        account.setStarttlsEnable(false);
        JavaMailSenderImpl sender = AccountMailSenderFactory.buildSender(account, "p");
        assertEquals("true", sender.getJavaMailProperties().getProperty("mail.smtp.ssl.enable"));
        assertEquals(null, sender.getJavaMailProperties().getProperty("mail.smtp.starttls.enable"));
    }

    @Test
    void obtainSender_sameAccount_reusesCachedInstance() {
        AccountMailSenderFactory factory = new AccountMailSenderFactory();
        SystemMailAccount account = account("a3");
        JavaMailSenderImpl first = factory.obtainSender(account, "p1");
        JavaMailSenderImpl second = factory.obtainSender(account, "p2");
        assertSame(first, second);
    }

    @Test
    void obtainSender_afterEvict_rebuilds() {
        AccountMailSenderFactory factory = new AccountMailSenderFactory();
        SystemMailAccount account = account("a4");
        JavaMailSenderImpl before = factory.obtainSender(account, "old-pass");
        factory.evict("a4");
        JavaMailSenderImpl after = factory.obtainSender(account, "new-pass");
        assertNotSame(before, after);
        assertEquals("new-pass", after.getPassword());
    }

    @Test
    void obtainSender_nullAccount_throws() {
        AccountMailSenderFactory factory = new AccountMailSenderFactory();
        JeecgBootException ex = assertThrows(JeecgBootException.class, () -> factory.obtainSender(null, "p"));
        assertTrue(ex.getMessage().contains("发送账号为空"));
    }

    @Test
    void testSmtp_unreachableHost_failsWithClassification() {
        // 真实网络失败路径：不可达地址应在超时/连接错误内返回 smtp 分类，而非抛异常
        AccountMailSenderFactory factory = new AccountMailSenderFactory();
        SystemMailAccount account = account("a5");
        account.setHost("127.0.0.1");
        account.setPort(1);
        var result = factory.testSmtp(account, "p");
        assertNotNull(result.getErrorCode());
        assertTrue(!result.isSmtpOk());
    }
}
