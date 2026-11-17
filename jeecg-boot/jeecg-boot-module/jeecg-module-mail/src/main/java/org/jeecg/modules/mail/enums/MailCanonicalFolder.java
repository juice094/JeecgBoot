package org.jeecg.modules.mail.enums;

/**
 * @Description: 员工邮箱规范文件夹枚举（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.1
 *
 * 口径（OA 邮件体系接口文档 §4.2）：远端文件夹经映射后归入下列 canonical；
 * canonical 为 null 表示未识别，需人工映射，同步不得静默跳过。
 */
public enum MailCanonicalFolder {

    INBOX("Inbox", "收件箱"),
    SENT("Sent", "已发送"),
    DRAFTS("Drafts", "草稿箱"),
    TRASH("Trash", "已删除"),
    SPAM("Spam", "垃圾邮件"),
    ARCHIVE("Archive", "归档");

    private final String code;
    private final String label;

    MailCanonicalFolder(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static MailCanonicalFolder fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (MailCanonicalFolder folder : values()) {
            if (folder.code.equalsIgnoreCase(code.trim())) {
                return folder;
            }
        }
        return null;
    }
}
