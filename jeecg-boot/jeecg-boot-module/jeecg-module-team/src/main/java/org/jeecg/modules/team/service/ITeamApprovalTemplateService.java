package org.jeecg.modules.team.service;

import java.util.List;

import org.jeecg.modules.team.entity.TeamApprovalTemplate;
import org.jeecg.modules.team.entity.TeamApprovalTemplateStep;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: 团队协作-审批模板 Service
 * @Author: juice094
 * @Date: 2026-11-18
 * @Version: V1.0
 */
public interface ITeamApprovalTemplateService extends IService<TeamApprovalTemplate> {

    /**
     * 按类别查启用模板；不存在或停用时返回 null
     */
    TeamApprovalTemplate findEnabledByCategory(String category);

    /**
     * 按模板ID查有序步骤（step_no 升序）；模板不存在返回空列表
     */
    List<TeamApprovalTemplateStep> listSteps(String templateId);
}
