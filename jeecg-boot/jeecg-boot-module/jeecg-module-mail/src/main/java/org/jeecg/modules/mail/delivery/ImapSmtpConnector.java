package org.jeecg.modules.mail.delivery;

import java.util.Properties;

import org.jeecg.modules.mail.dto.ConnectionTestResult;
import org.jeecg.modules.mail.dto.MailServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;

/**
 * @Description: IMAP/SMTP 双测连器（B-1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 口径（需求文档 B-1 / 接口文档 §4.1）：IMAP 与 SMTP 均测连通过才允许落库；
 * 单侧失败明确返回失败侧与错误分类，不允许"能看不能回"。
 * 只握手不发信、不收信；增量同步（B-2+）不在本类。
 */
@Component
public class ImapSmtpConnector {

    private static final Logger log = LoggerFactory.getLogger(ImapSmtpConnector.class);

    /**
     * 打开已认证的 IMAP Store（同步引擎使用；调用方负责 close）。
     */
    public Store openImapStore(MailServerConfig cfg) throws MessagingException {
        Properties props = new Properties();
        props.put("mail.imap.connectiontimeout", MailConnectionDefaults.TIMEOUT_MS);
        props.put("mail.imap.timeout", MailConnectionDefaults.TIMEOUT_MS);
        if (cfg.isSsl()) {
            props.put("mail.imap.ssl.enable", "true");
        }
        Session session = Session.getInstance(props);
        Store store = session.getStore(MailConnectionDefaults.PROTOCOL_IMAP);
        store.connect(cfg.getHost(), cfg.getPort(), cfg.getUsername(), cfg.getPassword());
        return store;
    }

    /**
     * IMAP 测连（真实握手）。
     */
    public ConnectionTestResult testImap(MailServerConfig cfg) {
        ConnectionTestResult result = new ConnectionTestResult();
        long start = System.currentTimeMillis();
        Store store = null;
        try {
            store = openImapStore(cfg);
            result.setImapOk(true);
        } catch (AuthenticationFailedException e) {
            failImap(result, e);
        } catch (MessagingException e) {
            failImap(result, e);
        } finally {
            if (store != null) {
                try {
                    store.close();
                } catch (MessagingException e) {
                    log.warn("[mail] IMAP 测连关闭连接失败 host={}:{}", cfg.getHost(), cfg.getPort());
                }
            }
        }
        result.setLatencyMs(System.currentTimeMillis() - start);
        return result;
    }

    /**
     * SMTP 测连（真实握手），配置来源为员工邮箱绑定配置。
     */
    public ConnectionTestResult testSmtp(MailServerConfig cfg) {
        ConnectionTestResult result = new ConnectionTestResult();
        long start = System.currentTimeMillis();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(cfg.getHost());
        sender.setPort(cfg.getPort());
        sender.setUsername(cfg.getUsername());
        sender.setPassword(cfg.getPassword());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", MailConnectionDefaults.TIMEOUT_MS);
        props.put("mail.smtp.timeout", MailConnectionDefaults.TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", MailConnectionDefaults.TIMEOUT_MS);
        if (cfg.isSsl()) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        Transport transport = null;
        try {
            transport = sender.getSession().getTransport(MailConnectionDefaults.PROTOCOL_SMTP);
            // 显式传凭据：no-arg connect() 只读 session 属性，JavaMailSenderImpl 的
            // username/password 不会进入 session，会导致 "no password specified" 误判
            transport.connect(cfg.getHost(), cfg.getPort(), cfg.getUsername(), cfg.getPassword());
            result.setSmtpOk(true);
        } catch (AuthenticationFailedException e) {
            failSmtp(result, e);
        } catch (MessagingException e) {
            failSmtp(result, e);
        } finally {
            if (transport != null) {
                try {
                    transport.close();
                } catch (MessagingException e) {
                    log.warn("[mail] SMTP 测连关闭连接失败 host={}:{}", cfg.getHost(), cfg.getPort());
                }
            }
        }
        result.setLatencyMs(System.currentTimeMillis() - start);
        return result;
    }

    /**
     * IMAP + SMTP 双测连；总耗时覆盖两侧，errorCode 取首个失败侧分类。
     */
    public ConnectionTestResult testBoth(MailServerConfig imap, MailServerConfig smtp) {
        long start = System.currentTimeMillis();
        ConnectionTestResult imapResult = testImap(imap);
        ConnectionTestResult smtpResult = testSmtp(smtp);
        ConnectionTestResult combined = new ConnectionTestResult();
        combined.setImapOk(imapResult.isImapOk());
        combined.setSmtpOk(smtpResult.isSmtpOk());
        combined.setImapErrorCode(imapResult.getImapErrorCode());
        combined.setSmtpErrorCode(smtpResult.getSmtpErrorCode());
        combined.setLatencyMs(System.currentTimeMillis() - start);
        if (!imapResult.isImapOk()) {
            combined.setErrorCode(imapResult.getImapErrorCode());
        } else if (!smtpResult.isSmtpOk()) {
            combined.setErrorCode(smtpResult.getSmtpErrorCode());
        }
        return combined;
    }

    private void failImap(ConnectionTestResult result, MessagingException e) {
        String code = SmtpErrorClassifier.classifyWithPrefix(e, "imap_");
        result.setImapOk(false);
        result.setImapErrorCode(code);
        log.warn("[mail] IMAP 测连失败 error={}", e.getMessage());
    }

    private void failSmtp(ConnectionTestResult result, MessagingException e) {
        String code = SmtpErrorClassifier.classify(e);
        result.setSmtpOk(false);
        result.setSmtpErrorCode(code);
        log.warn("[mail] SMTP 测连失败 error={}", e.getMessage());
    }
}
