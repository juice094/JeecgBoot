package org.jeecg.modules.mail.delivery;

import java.io.UnsupportedEncodingException;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.mail.constant.MailErrorCodes;
import org.jeecg.modules.mail.entity.SystemMailAccount;
import org.jeecg.modules.mail.mapper.SystemMailAccountMapper;
import org.jeecg.modules.mail.support.MailPasswordCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * @Description: 按账号投递器（Phase 1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 变化（R4）：骨架期复用全局 mailSender（application-dev.yml 单账号）；
 * Phase 1 起按 system_mail_account 记录经 AccountMailSenderFactory 动态构造 Sender，
 * 支持多发送账号（A-1）。密码经 MailPasswordCodec 解密后用于 SMTP 认证（D-76）。
 */
@Component
public class JavaMailDelivery implements MailDelivery {

    @Autowired
    private SystemMailAccountMapper accountMapper;

    @Autowired
    private AccountMailSenderFactory senderFactory;

    @Autowired
    private MailPasswordCodec passwordCodec;

    @Override
    public void deliver(String accountId, String to, String cc, String bcc, String subject, String html) {
        if (StringUtils.isBlank(to)) {
            throw new MailDeliveryException("recipient_refused", "收件人为空");
        }

        // 账号存在性与启停校验（MAIL_ACCOUNT_DISABLED）
        SystemMailAccount account = accountId == null ? null : accountMapper.selectById(accountId);
        if (account == null) {
            throw new MailDeliveryException(MailErrorCodes.MAIL_ACCOUNT_DISABLED, "发送账号不存在或已删除: " + accountId);
        }
        if (account.getStatus() != null && account.getStatus() == 0) {
            throw new MailDeliveryException(MailErrorCodes.MAIL_ACCOUNT_DISABLED, "发送账号已停用: " + accountId);
        }

        JavaMailSenderImpl sender = senderFactory.obtainSender(account, passwordCodec.decode(account.getPassword()));
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(splitAddresses(to));
            if (StringUtils.isNotBlank(cc)) {
                helper.setCc(splitAddresses(cc));
            }
            if (StringUtils.isNotBlank(bcc)) {
                helper.setBcc(splitAddresses(bcc));
            }
            if (StringUtils.isNotBlank(account.getFromAddress())) {
                helper.setFrom(account.getFromAddress(), StringUtils.defaultString(account.getFromName()));
            }
            helper.setSubject(StringUtils.defaultString(subject));
            helper.setText(StringUtils.defaultString(html), true);
            sender.send(message);
        } catch (MailAuthenticationException e) {
            throw new MailDeliveryException("smtp_auth_failed", "SMTP 认证失败", e);
        } catch (MailSendException e) {
            throw new MailDeliveryException(classifySendFailure(e), "SMTP 投递失败", e);
        } catch (MailException e) {
            throw new MailDeliveryException("unknown", "SMTP 发送异常", e);
        } catch (UnsupportedEncodingException e) {
            throw new MailDeliveryException("content_rejected", "发件人显示名编码失败", e);
        } catch (MessagingException e) {
            throw new MailDeliveryException("content_rejected", "邮件内容构造失败", e);
        }
    }

    private String[] splitAddresses(String raw) {
        String[] parts = raw.split(",");
        String[] result = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = parts[i].trim();
        }
        return result;
    }

    private String classifySendFailure(MailSendException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        if (msg.contains("refused") || msg.contains("invalid address")) {
            return "recipient_refused";
        }
        return SmtpErrorClassifier.classify(e);
    }
}
