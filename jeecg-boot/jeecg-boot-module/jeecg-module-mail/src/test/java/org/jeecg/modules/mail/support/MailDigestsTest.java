package org.jeecg.modules.mail.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * @Description: MailDigests 纯单元测试
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 锁定口径（接口文档 §8）：收件人指纹 = SHA-256 前 8 位十六进制。
 */
class MailDigestsTest {

    @Test
    void toHash8_null_returnsNull() {
        assertNull(MailDigests.toHash8(null));
    }

    @Test
    void toHash8_knownValue_matchesContractExampleShape() {
        String hash = MailDigests.toHash8("zhangsan@example.com");
        // SHA-256 前 8 位：8 个十六进制小写字符
        assertEquals(8, hash.length());
        assertEquals(hash, hash.toLowerCase());
        assertEquals(hash, MailDigests.toHash8("zhangsan@example.com"));
    }

    @Test
    void toHash8_sensitiveInput_notContainedInOutput() {
        String to = "zhangsan@example.com";
        String hash = MailDigests.toHash8(to);
        assertNotEquals(to, hash);
    }
}
