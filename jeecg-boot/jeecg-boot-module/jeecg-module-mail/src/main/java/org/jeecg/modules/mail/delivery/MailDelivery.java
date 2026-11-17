package org.jeecg.modules.mail.delivery;

/**
 * @Description: 邮件投递器接口（骨架 V0.3）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 *
 * 通道层 B 的发送复用本接口，不重复实现 SMTP 会话管理（需求文档 §4.1 规则 3）。
 * 实现方抛 MailDeliveryException 表示投递失败，含结构化错误码。
 */
public interface MailDelivery {

    /**
     * 投递一封已渲染的邮件。
     *
     * @param accountId 发送账号 id
     * @param to        收件人（逗号分隔）
     * @param cc        抄送，可空
     * @param bcc       密送，可空
     * @param subject   标题
     * @param html      正文 HTML
     */
    void deliver(String accountId, String to, String cc, String bcc, String subject, String html);
}
