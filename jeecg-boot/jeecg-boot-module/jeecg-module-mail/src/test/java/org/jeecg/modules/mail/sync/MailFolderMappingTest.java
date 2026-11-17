package org.jeecg.modules.mail.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * @Description: MailFolderMapping 纯单元测试（B-2）
 * @Author: mail-dev
 * @Date: 2026-11-17
 */
class MailFolderMappingTest {

    @Test
    void resolve_builtinAliases_coverCommonClients() {
        assertEquals("Inbox", MailFolderMapping.resolve("INBOX", null));
        assertEquals("Sent", MailFolderMapping.resolve("Sent Items", null));
        assertEquals("Sent", MailFolderMapping.resolve("Sent Mail", null));
        assertEquals("Sent", MailFolderMapping.resolve("已发送", null));
        assertEquals("Drafts", MailFolderMapping.resolve("草稿箱", null));
        assertEquals("Trash", MailFolderMapping.resolve("Deleted Items", null));
        assertEquals("Spam", MailFolderMapping.resolve("Junk E-mail", null));
        assertEquals("Archive", MailFolderMapping.resolve("归档", null));
    }

    @Test
    void resolve_caseInsensitive() {
        assertEquals("Sent", MailFolderMapping.resolve("sent items", null));
        assertEquals("Inbox", MailFolderMapping.resolve("inbox", null));
    }

    @Test
    void resolve_canonicalCodeItself() {
        assertEquals("Archive", MailFolderMapping.resolve("Archive", null));
        assertEquals("Sent", MailFolderMapping.resolve("sent", null));
    }

    @Test
    void resolve_customMapping_overridesBuiltin() {
        Map<String, String> custom = new HashMap<>();
        custom.put("sent items", "Archive");
        assertEquals("Archive", MailFolderMapping.resolve("Sent Items", custom));
    }

    @Test
    void resolve_unknown_returnsNull_pendingManualMapping() {
        assertNull(MailFolderMapping.resolve("自定义归档", null));
        assertNull(MailFolderMapping.resolve(null, null));
        assertNull(MailFolderMapping.resolve("  ", null));
    }
}
