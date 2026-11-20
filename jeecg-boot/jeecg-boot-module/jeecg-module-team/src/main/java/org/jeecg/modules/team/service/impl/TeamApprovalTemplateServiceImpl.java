package org.jeecg.modules.team.service.impl;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.modules.team.entity.TeamApprovalTemplate;
import org.jeecg.modules.team.entity.TeamApprovalTemplateStep;
import org.jeecg.modules.team.mapper.TeamApprovalTemplateMapper;
import org.jeecg.modules.team.mapper.TeamApprovalTemplateStepMapper;
import org.jeecg.modules.team.service.ITeamApprovalTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 审批模板查询（只读；模板维护走管理端，本期不提供写接口）
 * @Author: juice094
 * @Date: 2026-11-18
 * @Version: V1.0
 */
@Service
public class TeamApprovalTemplateServiceImpl
        extends ServiceImpl<TeamApprovalTemplateMapper, TeamApprovalTemplate>
        implements ITeamApprovalTemplateService {

    private static final int ENABLED = 1;

    @Autowired
    private TeamApprovalTemplateStepMapper stepMapper;

    @Override
    public TeamApprovalTemplate findEnabledByCategory(String category) {
        if (StringUtils.isBlank(category)) {
            return null;
        }
        LambdaQueryWrapper<TeamApprovalTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamApprovalTemplate::getCategory, category)
                .eq(TeamApprovalTemplate::getEnabled, ENABLED)
                .last("LIMIT 1");
        return getOne(wrapper, false);
    }

    @Override
    public List<TeamApprovalTemplateStep> listSteps(String templateId) {
        if (StringUtils.isBlank(templateId)) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<TeamApprovalTemplateStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamApprovalTemplateStep::getTemplateId, templateId)
                .orderByAsc(TeamApprovalTemplateStep::getStepNo);
        return stepMapper.selectList(wrapper);
    }
}
