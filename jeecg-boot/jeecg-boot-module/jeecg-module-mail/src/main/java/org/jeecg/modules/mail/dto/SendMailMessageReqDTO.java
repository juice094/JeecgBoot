package org.jeecg.modules.mail.dto;

import java.util.List;

/**
 * @Description: 员工邮件发送请求（B-6）
 * @Author: mail-dev（B-3/B-5/B-6 续作）
 * @Date: 2026-11-18
 *
 * 口径：经绑定账号的 SMTP 直发；to 必填（支持逗号分隔多地址），cc/bcc 可选；
 * mode/replyToMessageId/attachments/idempotencyKey 为前端契约字段，本期透传不实施
 * （回复=UI 预填收件人与主题；附件上传写信属二期）。
 */
public class SendMailMessageReqDTO {

    /** 绑定账号 id（必填） */
    private String accountId;

    /** 发送模式（new/reply/forward，本期仅用于 UI 语义，不影响服务端行为） */
    private String mode;

    /** 收件人（必填，逗号/分号分隔多地址） */
    private String to;

    /** 抄送（可选，地址数组） */
    private List<String> cc;

    /** 密送（可选，地址数组） */
    private List<String> bcc;

    /** 主题（留空时服务端填「(无主题)」） */
    private String subject;

    /** HTML 正文（必填） */
    private String html;

    /** 幂等键（契约预留，本期不实施去重） */
    private String idempotencyKey;

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public List<String> getCc() {
        return cc;
    }

    public void setCc(List<String> cc) {
        this.cc = cc;
    }

    public List<String> getBcc() {
        return bcc;
    }

    public void setBcc(List<String> bcc) {
        this.bcc = bcc;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
