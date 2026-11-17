package org.jeecg.modules.mail.delivery;

import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.mail.dto.ConnectionTestResult;
import org.jeecg.modules.mail.entity.SystemMailAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;

/**
 * @Description: 按系统发信账号动态构造 JavaMailSender（R4 / A-1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 口径变化：骨架期投递复用全局 mailSender（application-dev.yml 的单账号），
 * Phase 1 起改为按 system_mail_account 记录构造 JavaMailSenderImpl，
 * 支持多发送账号（host/port/SSL/STARTTLS/账号密码/发件人）。
 *
 * 缓存策略：按账号 id 缓存 JavaMailSenderImpl（配置后线程安全，批量发送复用）；
 * 账号修改/删除时必须调用 evict，否则旧连接参数残留。
 */
@Component
public class AccountMailSenderFactory {

    private static final Logger log = LoggerFactory.getLogger(AccountMailSenderFactory.class);

    private final ConcurrentMap<String, JavaMailSenderImpl> senderCache = new ConcurrentHashMap<>();

    /**
     * 取账号对应的 Sender（命中缓存则复用）；plainPassword 必须为解密后的明文。
     */
    public JavaMailSenderImpl obtainSender(SystemMailAccount account, String plainPassword) {
        if (account == null || account.getId() == null) {
            throw new JeecgBootException("发送账号为空，无法构造投递器");
        }
        return senderCache.computeIfAbsent(account.getId(),
                k -> buildSender(account, plainPassword));
    }

    /**
     * 账号配置变更或删除后调用，清缓存。
     */
    public void evict(String accountId) {
        if (accountId != null && senderCache.remove(accountId) != null) {
            log.info("[mail] 已清除账号 {} 的 SMTP 连接缓存", accountId);
        }
    }

    /**
     * 按账号配置构造 JavaMailSenderImpl。
     */
    public static JavaMailSenderImpl buildSender(SystemMailAccount account, String plainPassword) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(account.getHost());
        sender.setPort(account.getPort());
        sender.setUsername(account.getUsername());
        sender.setPassword(plainPassword);
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", MailConnectionDefaults.TIMEOUT_MS);
        props.put("mail.smtp.timeout", MailConnectionDefaults.TIMEOUT_MS);
        props.put("mail.smtp.writetimeout", MailConnectionDefaults.TIMEOUT_MS);
        if (Boolean.TRUE.equals(account.getSslEnable())) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        if (Boolean.TRUE.equals(account.getStarttlsEnable())) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        return sender;
    }

    /**
     * SMTP 测连（接口文档 §3.1）：真实握手，返回连通性与错误分类，不发信。
     */
    public ConnectionTestResult testSmtp(SystemMailAccount account, String plainPassword) {
        ConnectionTestResult result = new ConnectionTestResult();
        long start = System.currentTimeMillis();
        JavaMailSenderImpl sender = buildSender(account, plainPassword);
        Transport transport = null;
        try {
            transport = sender.getSession().getTransport(MailConnectionDefaults.PROTOCOL_SMTP);
            // 显式传凭据：no-arg connect() 只读 session 属性，JavaMailSenderImpl 的
            // username/password 不会进入 session，会导致 "no password specified" 误判
            transport.connect(account.getHost(), account.getPort(), account.getUsername(), plainPassword);
            result.setSmtpOk(true);
        } catch (AuthenticationFailedException e) {
            result.setSmtpOk(false);
            result.setErrorCode(SmtpErrorClassifier.classify(e));
            log.warn("[mail] SMTP 测连认证失败 accountId={} host={}:{}",
                    account.getId(), account.getHost(), account.getPort());
        } catch (MessagingException e) {
            result.setSmtpOk(false);
            result.setErrorCode(SmtpErrorClassifier.classify(e));
            log.warn("[mail] SMTP 测连失败 accountId={} host={}:{} error={}",
                    account.getId(), account.getHost(), account.getPort(), e.getMessage());
        } finally {
            if (transport != null) {
                try {
                    transport.close();
                } catch (MessagingException e) {
                    log.warn("[mail] SMTP 测连关闭连接失败 accountId={}", account.getId());
                }
            }
        }
        result.setLatencyMs(System.currentTimeMillis() - start);
        return result;
    }
}
