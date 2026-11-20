package org.jeecg.modules.mail.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMultipart;

import org.apache.commons.lang3.StringUtils;

/**
 * @Description: 邮件正文提取器（B-3）
 * @Author: mail-dev（B-3/B-5/B-6 续作）
 * @Date: 2026-11-18
 *
 * 口径：text/plain 优先、text/html 兜底（仅取首个）；multipart 递归；
 * 附件只取元数据（文件名+字节数），正文内容不读入内存；
 * 正文字符上限 256K，超出截断并标记截断，防止超大邮件拖垮前端。
 */
public final class MailBodyExtractor {

    /** 正文字符上限 */
    public static final int BODY_MAX_CHARS = 256_000;

    /** 附件元数据上限 */
    public static final int ATTACHMENT_META_MAX = 50;

    private MailBodyExtractor() {
    }

    /** 提取结果 */
    public static final class ExtractedBody {
        private String text;
        private String html;
        private boolean truncated;
        private final List<AttachmentMeta> attachments = new ArrayList<>();

        public String getText() {
            return text;
        }

        public String getHtml() {
            return html;
        }

        public boolean isTruncated() {
            return truncated;
        }

        public List<AttachmentMeta> getAttachments() {
            return attachments;
        }
    }

    /** 附件元数据（不含内容） */
    public static final class AttachmentMeta {
        private final String name;
        private final long sizeBytes;

        public AttachmentMeta(String name, long sizeBytes) {
            this.name = name;
            this.sizeBytes = sizeBytes;
        }

        public String getName() {
            return name;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }
    }

    /**
     * 提取正文与附件元数据。
     */
    public static ExtractedBody extract(Message message) throws MessagingException, IOException {
        ExtractedBody body = new ExtractedBody();
        walk(message, body);
        return body;
    }

    private static void walk(Part part, ExtractedBody body) throws MessagingException, IOException {
        if (part == null || body.attachments.size() >= ATTACHMENT_META_MAX) {
            return;
        }
        String disposition = part.getDisposition();
        if (Part.ATTACHMENT.equalsIgnoreCase(StringUtils.defaultString(disposition))) {
            String fileName = part.getFileName();
            body.attachments.add(new AttachmentMeta(
                    StringUtils.isNotBlank(fileName) ? decodeFileName(fileName) : "(未命名附件)",
                    Math.max(part.getSize(), 0)));
            return;
        }
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (content instanceof MimeMultipart) {
                MimeMultipart multipart = (MimeMultipart) content;
                for (int i = 0; i < multipart.getCount(); i++) {
                    walk(multipart.getBodyPart(i), body);
                }
            }
            return;
        }
        // 内联资源（图片等）不进正文
        if (Part.INLINE.equalsIgnoreCase(disposition)) {
            return;
        }
        if (part.isMimeType("text/plain")) {
            if (body.text == null) {
                body.text = cap(readContent(part), body);
            }
            return;
        }
        if (part.isMimeType("text/html")) {
            if (body.html == null) {
                body.html = cap(readContent(part), body);
            }
            return;
        }
        // 其余 text/*（text/calendar 等）：仅在尚无纯文本时兜底
        if (part.isMimeType("text/*") && body.text == null) {
            body.text = cap(readContent(part), body);
        }
    }

    private static String readContent(Part part) throws IOException, MessagingException {
        Object content = part.getContent();
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof InputStream) {
            return readString((InputStream) content, part.getContentType());
        }
        if (content instanceof Reader) {
            StringBuilder sb = new StringBuilder();
            Reader reader = (Reader) content;
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
                if (sb.length() > BODY_MAX_CHARS) {
                    break;
                }
            }
            return sb.toString();
        }
        return null;
    }

    private static String readString(InputStream in, String contentType) throws IOException {
        java.nio.charset.Charset charset = null;
        if (contentType != null) {
            try {
                charset = new jakarta.mail.internet.ContentType(contentType).getParameter("charset") == null
                        ? null
                        : java.nio.charset.Charset.forName(
                                new jakarta.mail.internet.ContentType(contentType).getParameter("charset"));
            } catch (Exception ignored) {
                charset = null;
            }
        }
        if (charset == null) {
            charset = java.nio.charset.StandardCharsets.UTF_8;
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        long total = 0;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            total += n;
            if (total > BODY_MAX_CHARS * 4L) {
                break;
            }
        }
        return out.toString(charset.name());
    }

    private static String cap(String content, ExtractedBody body) {
        if (content == null) {
            return null;
        }
        if (content.length() > BODY_MAX_CHARS) {
            body.truncated = true;
            return content.substring(0, BODY_MAX_CHARS);
        }
        return content;
    }

    private static String decodeFileName(String raw) {
        try {
            return jakarta.mail.internet.MimeUtility.decodeText(raw);
        } catch (Exception e) {
            return raw;
        }
    }
}
