package org.jeecg.modules.mail.dto;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jeecg.modules.mail.support.MailBodyExtractor.AttachmentMeta;

/**
 * @Description: 员工邮件详情响应（B-3）
 * @Author: mail-dev（B-3/B-5/B-6 续作）
 * @Date: 2026-11-18
 *
 * 口径：正文实时 FETCH 不落库；text 为纯文本正文（优先），html 为 HTML 正文（无纯文本时兜底）；
 * attachments 仅元数据；fetchedFromImap/fetchLatencyMs 如实上报抓取耗时。
 */
public class OaMailMessageDetailDTO {

    private String id;
    private String accountId;
    private String canonicalFolder;
    private Long imapUid;
    private String subject;
    private String fromName;
    private String fromAddressHash;
    private String toSummary;
    private Date receivedTime;
    private Boolean readFlag;
    private Boolean hasAttachment;

    private String text;
    private String html;
    private boolean truncated;
    private List<AttachmentMeta> attachments = new ArrayList<>();
    private Boolean fetchedFromImap = true;
    private Long fetchLatencyMs;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getCanonicalFolder() {
        return canonicalFolder;
    }

    public void setCanonicalFolder(String canonicalFolder) {
        this.canonicalFolder = canonicalFolder;
    }

    public Long getImapUid() {
        return imapUid;
    }

    public void setImapUid(Long imapUid) {
        this.imapUid = imapUid;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public String getFromAddressHash() {
        return fromAddressHash;
    }

    public void setFromAddressHash(String fromAddressHash) {
        this.fromAddressHash = fromAddressHash;
    }

    public String getToSummary() {
        return toSummary;
    }

    public void setToSummary(String toSummary) {
        this.toSummary = toSummary;
    }

    public Date getReceivedTime() {
        return receivedTime;
    }

    public void setReceivedTime(Date receivedTime) {
        this.receivedTime = receivedTime;
    }

    public Boolean getReadFlag() {
        return readFlag;
    }

    public void setReadFlag(Boolean readFlag) {
        this.readFlag = readFlag;
    }

    public Boolean getHasAttachment() {
        return hasAttachment;
    }

    public void setHasAttachment(Boolean hasAttachment) {
        this.hasAttachment = hasAttachment;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getHtml() {
        return html;
    }

    public void setHtml(String html) {
        this.html = html;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public List<AttachmentMeta> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<AttachmentMeta> attachments) {
        this.attachments = attachments;
    }

    public Boolean getFetchedFromImap() {
        return fetchedFromImap;
    }

    public void setFetchedFromImap(Boolean fetchedFromImap) {
        this.fetchedFromImap = fetchedFromImap;
    }

    public Long getFetchLatencyMs() {
        return fetchLatencyMs;
    }

    public void setFetchLatencyMs(Long fetchLatencyMs) {
        this.fetchLatencyMs = fetchLatencyMs;
    }
}
