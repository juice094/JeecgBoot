package org.jeecg.modules.team.service.impl;

import org.jeecg.modules.team.entity.TeamFormTemplate;
import org.jeecg.modules.team.mapper.TeamFormTemplateMapper;
import org.jeecg.modules.team.service.ITeamFormTemplateService;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 团队协作-表单模板 ServiceImpl
 * @Author: Kimi Code
 * @Date: 2026-11-17
 * @Version: V1.0
 */
@Service
public class TeamFormTemplateServiceImpl extends ServiceImpl<TeamFormTemplateMapper, TeamFormTemplate>
        implements ITeamFormTemplateService {
}
