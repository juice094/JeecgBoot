package org.jeecg.modules.mail.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Description: 邮件模板占位符解析（骨架 V0.3）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 *
 * 口径（需求文档 A-2）：保存时扫描 {key} 生成 params；发送时缺参数返回
 * MAIL_SEND_TEMPLATE_PARAM_MISS 并列出缺失 key，不允许发出半截邮件。
 *
 * 纯静态工具，无 Spring 依赖，便于直接单测。
 */
public final class MailTemplateParser {

    /** 只匹配简单标识符占位符，避免把 CSS/JS 的 { } 误判为参数 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

    private MailTemplateParser() {
    }

    /**
     * 扫描文本中的占位符 key，按出现顺序去重。
     */
    public static List<String> extractKeys(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        Set<String> keys = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return new ArrayList<>(keys);
    }

    /**
     * 收集缺失参数：params 为空或缺少某个 key 都算缺失。
     */
    public static List<String> missingKeys(List<String> requiredKeys, Map<String, Object> params) {
        List<String> missing = new ArrayList<>();
        if (requiredKeys == null || requiredKeys.isEmpty()) {
            return missing;
        }
        for (String key : requiredKeys) {
            Object value = params == null ? null : params.get(key);
            if (value == null || String.valueOf(value).isEmpty()) {
                missing.add(key);
            }
        }
        return missing;
    }

    /**
     * 渲染：替换 {key} 为参数值；未提供的 key 原样保留（调用方应先用 missingKeys 拦截）。
     */
    public static String render(String text, Map<String, Object> params) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = params == null ? null : params.get(key);
            String replacement = value == null ? matcher.group(0) : Matcher.quoteReplacement(String.valueOf(value));
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
