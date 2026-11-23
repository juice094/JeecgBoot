package org.jeecg.modules.mail.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * @Description: MailTemplateParser 纯单元测试（零 Spring 依赖）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 锁定口径（需求文档 A-2 / 接口文档 §3.2）：
 * 保存时扫描 {key} 生成 params；发送时缺参数返回 MAIL_SEND_TEMPLATE_PARAM_MISS 并列出缺失 key。
 */
class MailTemplateParserTest {

    // ---------- extractKeys ----------

    @Test
    void extractKeys_nullText_returnsEmpty() {
        assertTrue(MailTemplateParser.extractKeys(null).isEmpty());
    }

    @Test
    void extractKeys_emptyText_returnsEmpty() {
        assertTrue(MailTemplateParser.extractKeys("").isEmpty());
    }

    @Test
    void extractKeys_noPlaceholder_returnsEmpty() {
        assertTrue(MailTemplateParser.extractKeys("纯文本，没有占位符。").isEmpty());
    }

    @Test
    void extractKeys_plainTextWithBracesButNoPlaceholder_returnsEmpty() {
        // CSS 块与 JSON 字面量的 { } 不得被误判为模板参数
        assertTrue(MailTemplateParser.extractKeys("body { color: red; margin: 0 }").isEmpty());
        assertTrue(MailTemplateParser.extractKeys("{\"name\": \"zhangsan\", \"age\": 18}").isEmpty());
    }

    @Test
    void extractKeys_multipleKeys_dedupAndKeepOrder() {
        List<String> keys = MailTemplateParser.extractKeys("您好 {name}，单据 {title} 将于 {deadline} 超时，再见 {name}。");
        assertEquals(Arrays.asList("name", "title", "deadline"), keys);
    }

    @Test
    void extractKeys_invalidIdentifier_notMatched() {
        // 数字开头、中文、空花括号均不是合法占位符
        List<String> keys = MailTemplateParser.extractKeys("单号 {1123}、{中文}、{}、{_private}");
        assertEquals(Collections.singletonList("_private"), keys);
    }

    // ---------- missingKeys ----------

    @Test
    void missingKeys_nullRequiredKeys_returnsEmpty() {
        assertTrue(MailTemplateParser.missingKeys(null, Collections.emptyMap()).isEmpty());
    }

    @Test
    void missingKeys_emptyRequiredKeys_returnsEmpty() {
        assertTrue(MailTemplateParser.missingKeys(Collections.emptyList(), null).isEmpty());
    }

    @Test
    void missingKeys_nullParams_allRequiredMissing() {
        List<String> required = Arrays.asList("name", "title");
        assertEquals(required, MailTemplateParser.missingKeys(required, null));
    }

    @Test
    void missingKeys_partialParams_onlyMissingListedInRequiredOrder() {
        List<String> required = Arrays.asList("name", "title", "deadline");
        Map<String, Object> params = new HashMap<>();
        params.put("title", "报销单-1123");
        // 提供 key 但值为空串，同样算缺失
        params.put("deadline", "");
        assertEquals(Arrays.asList("name", "deadline"), MailTemplateParser.missingKeys(required, params));
    }

    @Test
    void missingKeys_allPresent_returnsEmpty() {
        List<String> required = Arrays.asList("name", "title");
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");
        params.put("title", "报销单-1123");
        assertTrue(MailTemplateParser.missingKeys(required, params).isEmpty());
    }

    // ---------- render ----------

    @Test
    void render_nullOrEmptyText_returnsAsIs() {
        assertNull(MailTemplateParser.render(null, Collections.emptyMap()));
        assertEquals("", MailTemplateParser.render("", Collections.emptyMap()));
    }

    @Test
    void render_replacesAllOccurrences() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");
        assertEquals("张三 您好，张三 再见", MailTemplateParser.render("{name} 您好，{name} 再见", params));
    }

    @Test
    void render_missingKey_keptAsIs() {
        // 调用方应先用 missingKeys 拦截；未提供的 key 原样保留，不渲染半截
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");
        assertEquals("张三 您好，{title}", MailTemplateParser.render("{name} 您好，{title}", params));
    }

    @Test
    void render_nullParams_textUnchanged() {
        assertEquals("您好 {name}", MailTemplateParser.render("您好 {name}", null));
    }

    @Test
    void render_valueWithDollarAndBackslash_renderedLiterally() {
        // Matcher.quoteReplacement 语义：$ 与 \ 是替换串元字符，必须按字面输出
        Map<String, Object> params = new HashMap<>();
        params.put("amount", "$100\\");
        assertEquals("报销 $100\\ 元", MailTemplateParser.render("报销 {amount} 元", params));
    }
}
