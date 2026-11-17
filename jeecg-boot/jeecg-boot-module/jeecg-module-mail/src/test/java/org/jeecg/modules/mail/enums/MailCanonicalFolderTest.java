package org.jeecg.modules.mail.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * @Description: MailCanonicalFolder 规范文件夹映射纯单元测试（零 Spring 依赖）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 锁定口径（接口文档 §4.2 / 需求文档 B-2）：
 * 远端文件夹经映射归入 canonical；未识别（fromCode 返回 null）需人工维护映射，同步不得静默跳过。
 */
class MailCanonicalFolderTest {

    @Test
    void fromCode_exactCode_matches() {
        assertEquals(MailCanonicalFolder.INBOX, MailCanonicalFolder.fromCode("Inbox"));
        assertEquals(MailCanonicalFolder.SENT, MailCanonicalFolder.fromCode("Sent"));
        assertEquals(MailCanonicalFolder.DRAFTS, MailCanonicalFolder.fromCode("Drafts"));
        assertEquals(MailCanonicalFolder.TRASH, MailCanonicalFolder.fromCode("Trash"));
        assertEquals(MailCanonicalFolder.SPAM, MailCanonicalFolder.fromCode("Spam"));
        assertEquals(MailCanonicalFolder.ARCHIVE, MailCanonicalFolder.fromCode("Archive"));
    }

    @Test
    void fromCode_caseInsensitive() {
        assertEquals(MailCanonicalFolder.INBOX, MailCanonicalFolder.fromCode("inbox"));
        assertEquals(MailCanonicalFolder.SENT, MailCanonicalFolder.fromCode("SENT"));
        assertEquals(MailCanonicalFolder.ARCHIVE, MailCanonicalFolder.fromCode("archive"));
    }

    @Test
    void fromCode_surroundingWhitespace_trimmed() {
        assertEquals(MailCanonicalFolder.SENT, MailCanonicalFolder.fromCode("  Sent  "));
        assertEquals(MailCanonicalFolder.TRASH, MailCanonicalFolder.fromCode("\tTrash\n"));
    }

    @Test
    void fromCode_unknownRemoteName_returnsNull() {
        // 别名/远端名（如 Sent Items、已发送、自定义归档）不在枚举口径内，
        // 由映射表归一后再调 fromCode；未识别返回 null，进入待映射列表
        assertNull(MailCanonicalFolder.fromCode("Sent Items"));
        assertNull(MailCanonicalFolder.fromCode("已发送"));
        assertNull(MailCanonicalFolder.fromCode("自定义归档"));
    }

    @Test
    void fromCode_nullBlank_returnsNull() {
        assertNull(MailCanonicalFolder.fromCode(null));
        assertNull(MailCanonicalFolder.fromCode(""));
        assertNull(MailCanonicalFolder.fromCode("   "));
    }

    @Test
    void codesAndLabels_nonBlank() {
        for (MailCanonicalFolder folder : MailCanonicalFolder.values()) {
            assertEquals(folder, MailCanonicalFolder.fromCode(folder.getCode()));
        }
    }
}
