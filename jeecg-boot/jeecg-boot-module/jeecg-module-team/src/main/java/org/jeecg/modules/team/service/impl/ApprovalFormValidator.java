package org.jeecg.modules.team.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @Description: 审批表单字段校验器（按模板 field_schema 复核 form_data）
 * @Author: juice094
 * @Date: 2026-11-18
 * @Version: V1.0
 * <p>
 * 设计要点：
 * 1. 前端按同一份 schema 渲染并做一次校验，后端必须独立复核——只信前端等于没有校验；
 * 2. schema 缺失（模板未配字段）时不校验，保持向后兼容；
 * 3. 只校验 schema 中声明的字段；form_data 里的多余键被丢弃，避免脏数据落库；
 * 4. 金额单独回填到实体 amount 列，供范围查询与聚合。
 * <p>
 * 支持的字段类型：text / textarea / number / date / select / multiselect。
 */
public final class ApprovalFormValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 校验结果：清洗后的字段值 + 提取出的金额 */
    public static final class Result {
        private final Map<String, Object> cleaned;
        private final BigDecimal amount;

        Result(Map<String, Object> cleaned, BigDecimal amount) {
            this.cleaned = cleaned;
            this.amount = amount;
        }

        public Map<String, Object> getCleaned() {
            return cleaned;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public String toJson() {
            if (cleaned == null || cleaned.isEmpty()) {
                return null;
            }
            try {
                return MAPPER.writeValueAsString(cleaned);
            } catch (Exception e) {
                throw new JeecgBootException("表单数据序列化失败: " + e.getMessage());
            }
        }
    }

    /**
     * 按 schema 校验并清洗 form_data。
     *
     * @param fieldSchemaJson 模板的 field_schema（可空）
     * @param formDataJson    客户端提交的 form_data（可空）
     * @return 清洗后的字段值 + 金额
     */
    public static Result validate(String fieldSchemaJson, String formDataJson) {
        if (StringUtils.isBlank(fieldSchemaJson)) {
            // 模板未配字段契约：不校验，但也不丢弃客户端数据（保持向后兼容）
            return new Result(parseObject(formDataJson), extractAmount(parseObject(formDataJson)));
        }

        List<Map<String, Object>> schema = parseSchema(fieldSchemaJson);
        Map<String, Object> input = parseObject(formDataJson);
        Map<String, Object> cleaned = new LinkedHashMap<>();
        BigDecimal amount = null;

        for (Map<String, Object> field : schema) {
            String key = str(field.get("key"));
            if (StringUtils.isBlank(key)) {
                continue;
            }
            String label = StringUtils.defaultIfBlank(str(field.get("label")), key);
            String type = StringUtils.defaultIfBlank(str(field.get("type")), "text");
            boolean required = Boolean.TRUE.equals(field.get("required"));
            Object raw = input.get(key);

            if (isBlankValue(raw)) {
                if (required) {
                    throw new JeecgBootException("字段「" + label + "」必填");
                }
                continue;
            }

            Object value = validateByType(type, raw, field, label);
            cleaned.put(key, value);

            if ("amount".equals(key) && value instanceof BigDecimal) {
                amount = (BigDecimal) value;
            }
        }

        return new Result(cleaned, amount);
    }

    private static Object validateByType(String type, Object raw, Map<String, Object> field, String label) {
        switch (type) {
            case "number":
                return validateNumber(raw, field, label);
            case "select":
                return validateSelect(raw, field, label);
            case "multiselect":
                return validateMultiSelect(raw, field, label);
            case "date":
                String d = str(raw);
                if (!d.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    throw new JeecgBootException("字段「" + label + "」日期格式应为 yyyy-MM-dd，实际: " + d);
                }
                return d;
            case "text":
            case "textarea":
            default:
                String s = str(raw);
                if (s.length() > 2000) {
                    throw new JeecgBootException("字段「" + label + "」超长（上限 2000 字符）");
                }
                return s;
        }
    }

    private static BigDecimal validateNumber(Object raw, Map<String, Object> field, String label) {
        BigDecimal value;
        try {
            value = raw instanceof Number ? new BigDecimal(raw.toString()) : new BigDecimal(str(raw).trim());
        } catch (Exception e) {
            throw new JeecgBootException("字段「" + label + "」应为数字，实际: " + raw);
        }
        BigDecimal min = toBigDecimal(field.get("min"));
        BigDecimal max = toBigDecimal(field.get("max"));
        if (min != null && value.compareTo(min) < 0) {
            throw new JeecgBootException("字段「" + label + "」不能小于 " + min);
        }
        if (max != null && value.compareTo(max) > 0) {
            throw new JeecgBootException("字段「" + label + "」不能大于 " + max);
        }
        return value;
    }

    private static String validateSelect(Object raw, Map<String, Object> field, String label) {
        String value = str(raw);
        Set<String> options = toOptions(field.get("options"));
        if (!options.isEmpty() && !options.contains(value)) {
            throw new JeecgBootException("字段「" + label + "」取值非法: " + value + "（允许 " + options + "）");
        }
        return value;
    }

    private static List<String> validateMultiSelect(Object raw, Map<String, Object> field, String label) {
        Collection<?> values = raw instanceof Collection ? (Collection<?>) raw : List.of(str(raw).split(","));
        Set<String> options = toOptions(field.get("options"));
        List<String> cleaned = new ArrayList<>();
        for (Object v : values) {
            String s = str(v).trim();
            if (s.isEmpty()) {
                continue;
            }
            if (!options.isEmpty() && !options.contains(s)) {
                throw new JeecgBootException("字段「" + label + "」含非法取值: " + s);
            }
            cleaned.add(s);
        }
        return cleaned;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseSchema(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            throw new JeecgBootException("模板字段契约格式非法: " + e.getMessage());
        }
    }

    private static Map<String, Object> parseObject(String json) {
        if (StringUtils.isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new JeecgBootException("表单数据格式非法（应为 JSON 对象）: " + e.getMessage());
        }
    }

    private static BigDecimal extractAmount(Map<String, Object> data) {
        Object raw = data.get("amount");
        if (raw == null) {
            return null;
        }
        try {
            return raw instanceof Number ? new BigDecimal(raw.toString()) : new BigDecimal(str(raw).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Set<String> toOptions(Object raw) {
        Set<String> set = new HashSet<>();
        if (raw instanceof Collection) {
            for (Object o : (Collection<?>) raw) {
                set.add(str(o));
            }
        }
        return set;
    }

    private static BigDecimal toBigDecimal(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return new BigDecimal(raw.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isBlankValue(Object raw) {
        if (raw == null) {
            return true;
        }
        if (raw instanceof String) {
            return StringUtils.isBlank((String) raw);
        }
        if (raw instanceof Collection) {
            return ((Collection<?>) raw).isEmpty();
        }
        return false;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private ApprovalFormValidator() {
    }
}
