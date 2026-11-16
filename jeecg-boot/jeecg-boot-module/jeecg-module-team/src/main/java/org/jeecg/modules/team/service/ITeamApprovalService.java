package org.jeecg.modules.team.service;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.team.entity.TeamApproval;
import org.jeecg.modules.team.entity.TeamApprovalStep;

/**
 * 审批单轻量状态机：submit → 按步骤顺序审批 → 全通过=approved / 任一拒绝=rejected
 */
public interface ITeamApprovalService extends IService<TeamApproval> {

    /** 提交申请：生成单号，按模板落步骤（状态机起点） */
    TeamApproval submit(TeamApproval approval, List<TeamApprovalStep> stepTemplate);

    /** 通过当前步骤；最后一步通过后整单 approved */
    void approve(String approvalId, String comment);

    /** 拒绝当前步骤，整单 rejected */
    void reject(String approvalId, String comment);

    /** 查询单子的步骤链 */
    List<TeamApprovalStep> listSteps(String approvalId);
}
