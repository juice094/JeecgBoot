package org.jeecg.modules.mail.sync;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jeecg.modules.mail.enums.MailCanonicalFolder;

/**
 * @Description: 远端文件夹名 → 规范文件夹映射（B-2）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 口径（需求文档 B-2 / 接口文档 §4.2）：
 * - 内置常见别名（含中文客户端命名），不区分大小写；
 * - 用户自定义映射优先于内置别名；
 * - 未识别返回 null，进入待映射列表，同步不得静默跳过。
 */
public final class MailFolderMapping {

    /** 自定义映射键统一小写（remoteName.trim().toLowerCase） */
    private static final Map<String, String> DEFAULT_ALIASES = new HashMap<>();

    static {
        register(MailCanonicalFolder.INBOX, "INBOX", "收件箱");
        register(MailCanonicalFolder.SENT, "Sent Items", "Sent Mail", "已发送", "发件箱", "Sent Messages");
        register(MailCanonicalFolder.DRAFTS, "Drafts", "草稿", "草稿箱");
        register(MailCanonicalFolder.TRASH, "Trash", "Deleted", "Deleted Items", "已删除", "回收站");
        register(MailCanonicalFolder.SPAM, "Spam", "Junk", "Junk E-mail", "垃圾邮件", "垃圾箱");
        register(MailCanonicalFolder.ARCHIVE, "Archive", "All Mail", "归档");
    }

    private MailFolderMapping() {
    }

    /**
     * 解析远端文件夹名；返回 canonical code（MailCanonicalFolder.getCode()），未识别返回 null。
     *
     * @param customMappings 用户自定义映射（key=远端名，value=canonical code），可空；键名大小写不敏感
     */
    public static String resolve(String remoteName, Map<String, String> customMappings) {
        if (remoteName == null || remoteName.trim().isEmpty()) {
            return null;
        }
        String key = remoteName.trim().toLowerCase(Locale.ROOT);
        if (customMappings != null) {
            String mapped = customMappings.get(key);
            if (mapped != null) {
                return mapped;
            }
        }
        String aliased = DEFAULT_ALIASES.get(key);
        if (aliased != null) {
            return aliased;
        }
        // 远端名本身就是 canonical code（如 "Archive"）
        MailCanonicalFolder folder = MailCanonicalFolder.fromCode(remoteName);
        return folder == null ? null : folder.getCode();
    }

    private static void register(MailCanonicalFolder canonical, String... remoteNames) {
        for (String name : remoteNames) {
            DEFAULT_ALIASES.put(name.toLowerCase(Locale.ROOT), canonical.getCode());
        }
    }
}
