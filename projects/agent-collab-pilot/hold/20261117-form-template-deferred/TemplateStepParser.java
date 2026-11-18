package org.jeecg.modules.team.service.impl;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.team.entity.TeamApprovalStep;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @Description: 表单模板步骤链解析器（纯静态逻辑，便于无库自测）
 * @Author: Kimi Code
 * @Date: 2026-11-17
 * @Version: V1.0
 *
 * team_form_template.step_template 为 JSON 文本，格式：
 * [{"approverName":"贺致远","approverRole":"部门主管"}, ...]
 * 解析与业务校验（非空、审批人非空）在此一处，提交申请与模板维护共用。
 */
public final class TemplateStepParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 解析模板步骤链 JSON
     *
     * @param templateCode 模板编码（仅用于异常上下文）
     * @param stepTemplateJson step_template 列文本
     * @return 步骤模板（stepNo/action 未填充，由状态机落库时赋值）
     */
    public static List<TeamApprovalStep> parse(String templateCode, String stepTemplateJson) {
        if (StringUtils.isBlank(stepTemplateJson)) {
            throw new JeecgBootException("表单模板缺少审批步骤配置: template=" + templateCode);
        }
        List<TeamApprovalStep> steps;
        try {
            steps = OBJECT_MAPPER.readValue(stepTemplateJson, new TypeReference<List<TeamApprovalStep>>() {
            });
        } catch (Exception e) {
            throw new JeecgBootException("表单模板步骤配置非法 JSON: template=" + templateCode, e);
        }
        if (steps == null || steps.isEmpty()) {
            throw new JeecgBootException("表单模板步骤配置为空: template=" + templateCode);
        }
        for (int i = 0; i < steps.size(); i++) {
            TeamApprovalStep step = steps.get(i);
            if (step == null || StringUtils.isBlank(step.getApproverName())) {
                throw new JeecgBootException("表单模板步骤缺少审批人: template=" + templateCode + ", 第 " + (i + 1) + " 步");
            }
        }
        return steps;
    }

    private TemplateStepParser() {
    }
}
