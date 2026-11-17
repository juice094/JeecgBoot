package org.jeecg.modules.mail.support;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.alibaba.fastjson2.JSON;

/**
 * @Description: 模板 params（JSON 数组字符串）与 List 互转
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * params 损坏时按无参数模板处理并记录告警（发送侧 SystemMailServiceImpl 原有口径），
 * 此处集中实现避免两处重复。
 */
public final class MailTemplateParams {

    private MailTemplateParams() {
    }

    /**
     * 解析 params JSON 数组；空或损坏返回空列表。
     */
    public static List<String> fromJson(String paramsJson) {
        if (StringUtils.isBlank(paramsJson)) {
            return Collections.emptyList();
        }
        // 先校验必须是 JSON 数组：fastjson2 对裸词等非数组文本有宽松解析，必须挡住
        if (!JSON.isValidArray(paramsJson)) {
            return Collections.emptyList();
        }
        try {
            return JSON.parseArray(paramsJson, String.class);
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    /**
     * 序列化为 JSON 数组字符串。
     */
    public static String toJson(List<String> params) {
        return JSON.toJSONString(params == null ? Collections.emptyList() : params);
    }
}
