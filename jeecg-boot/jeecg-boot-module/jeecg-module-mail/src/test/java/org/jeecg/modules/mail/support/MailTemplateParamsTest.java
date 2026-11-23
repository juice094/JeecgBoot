package org.jeecg.modules.mail.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

/**
 * @Description: MailTemplateParams 纯单元测试
 * @Author: mail-dev
 * @Date: 2026-11-17
 */
class MailTemplateParamsTest {

    @Test
    void fromJson_validArray_parsed() {
        assertEquals(Arrays.asList("name", "title"),
                MailTemplateParams.fromJson("[\"name\",\"title\"]"));
    }

    @Test
    void fromJson_blankOrBroken_returnsEmpty() {
        assertTrue(MailTemplateParams.fromJson(null).isEmpty());
        assertTrue(MailTemplateParams.fromJson("").isEmpty());
        // params 损坏按无参数模板处理（发送侧原有口径）
        assertTrue(MailTemplateParams.fromJson("not-json").isEmpty());
        assertTrue(MailTemplateParams.fromJson("{\"a\":1}").isEmpty());
    }

    @Test
    void toJson_null_returnsEmptyArray() {
        assertEquals("[]", MailTemplateParams.toJson(null));
        assertEquals("[]", MailTemplateParams.toJson(Collections.emptyList()));
    }

    @Test
    void roundTrip() {
        assertEquals(Arrays.asList("a", "b"),
                MailTemplateParams.fromJson(MailTemplateParams.toJson(Arrays.asList("a", "b"))));
    }
}
