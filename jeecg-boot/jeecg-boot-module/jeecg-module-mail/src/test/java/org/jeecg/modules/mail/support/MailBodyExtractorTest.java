package org.jeecg.modules.mail.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.jeecg.modules.mail.support.MailBodyExtractor.AttachmentMeta;
import org.jeecg.modules.mail.support.MailBodyExtractor.ExtractedBody;
import org.junit.jupiter.api.Test;

/**
 * @Description: 正文提取器纯 Java 单测（B-3）
 * @Author: mail-dev（B-3/B-5/B-6 续作）
 * @Date: 2026-11-18
 *
 * 三态覆盖：纯文本 / 仅 HTML / multipart（正文+附件元数据）；另覆盖截断与中文文件名解码。
 */
class MailBodyExtractorTest {

    private static MimeMessage newMessage() {
        return new MimeMessage((Session) null);
    }

    @Test
    void plainTextMessage_extractsText() throws Exception {
        MimeMessage message = newMessage();
        message.setText("这是一封纯文本邮件。", "UTF-8");
        message.saveChanges();

        ExtractedBody body = MailBodyExtractor.extract(message);
        assertNotNull(body.getText());
        assertTrue(body.getText().contains("纯文本邮件"));
        assertNull(body.getHtml());
        assertTrue(body.getAttachments().isEmpty());
        assertTrue(!body.isTruncated());
    }

    @Test
    void htmlOnlyMessage_extractsHtmlAsFallback() throws Exception {
        MimeMessage message = newMessage();
        message.setContent("<p>HTML 正文</p>", "text/html; charset=UTF-8");
        message.saveChanges(); // 写 Content-Type 头，否则 getContentType 回落默认 text/plain

        ExtractedBody body = MailBodyExtractor.extract(message);
        assertNull(body.getText());
        assertNotNull(body.getHtml());
        assertTrue(body.getHtml().contains("HTML 正文"));
    }

    @Test
    void multipartMessage_collectsTextAndAttachmentMeta() throws Exception {
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText("multipart 正文内容", "UTF-8");
        multipart.addBodyPart(textPart);

        MimeBodyPart attachment = new MimeBodyPart();
        attachment.setDataHandler(new jakarta.activation.DataHandler(
                new jakarta.mail.util.ByteArrayDataSource(
                        "fake-pdf-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        "application/pdf")));
        attachment.setFileName(jakarta.mail.internet.MimeUtility.encodeText("报告.pdf", "UTF-8", "B"));
        attachment.setDisposition(jakarta.mail.Part.ATTACHMENT);
        multipart.addBodyPart(attachment);

        MimeMessage message = newMessage();
        message.setContent(multipart);
        message.saveChanges();

        ExtractedBody body = MailBodyExtractor.extract(message);
        assertNotNull(body.getText());
        assertTrue(body.getText().contains("multipart 正文内容"));
        assertEquals(1, body.getAttachments().size());
        AttachmentMeta meta = body.getAttachments().get(0);
        assertEquals("报告.pdf", meta.getName());
        // 内存态 MimeBodyPart 无编码流，size 可能为 0/未知；真实 IMAP 抓取的邮件有准确大小
        assertTrue(meta.getSizeBytes() >= 0);
    }

    @Test
    void oversizedBody_isTruncated() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MailBodyExtractor.BODY_MAX_CHARS + 1000; i++) {
            sb.append('a');
        }
        MimeMessage message = newMessage();
        message.setText(sb.toString(), "UTF-8");
        message.saveChanges();

        ExtractedBody body = MailBodyExtractor.extract(message);
        assertTrue(body.isTruncated());
        assertEquals(MailBodyExtractor.BODY_MAX_CHARS, body.getText().length());
    }
}
