package org.jeecg.modules.team.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.jeecg.modules.team.entity.TeamApproval;
import org.jeecg.modules.team.entity.TeamApprovalStep;

/**
 * 审批单轻量状态机：submit → 按步骤顺序审批 → 全通过=approved / 任一拒绝=rejected
 * <p>
 * v2 扩展：模板优先生成步骤、撤回、退回重提、approver_id 精确匹配。
 * 状态口径见 {@link org.jeecg.modules.team.enums.TeamApprovalStatus}。
 */
public interface ITeamApprovalService extends IService<TeamApproval> {

    /**
     * 提交申请：按 category 查启用的模板生成步骤；模板缺失时回退到 clientSteps（兼容既有调用）。
     * 校验：审批链不得包含申请人本人。
     */
    TeamApproval submit(TeamApproval approval, List<TeamApprovalStep> clientSteps);

    /**
     * 通过当前步骤；最后一步通过后整单 approved。条件更新防并发双推。
     */
    void approve(String approvalId, String comment);

    /**
     * 拒绝当前步骤，整单 rejected（终态）。
     */
    void reject(String approvalId, String comment);

    /**
     * 申请人撤回（仅 pending 且所有步骤均未操作）。终态 canceled。
     */
    void cancel(String approvalId);

    /**
     * 审批人退回至申请人：当前步骤标记 returned，整单状态 returned，可 resubmit。
     */
    void returnToApplicant(String approvalId, String comment);

    /**
     * 申请人重提（仅 returned）：按模板/传入 steps 重建步骤链，状态回 pending。
     */
    TeamApproval resubmit(String approvalId, List<TeamApprovalStep> clientSteps);

    /**
     * 查询单子的步骤链
     */
    List<TeamApprovalStep> listSteps(String approvalId);

    /**
     * 查某审批人参与过（任意步骤）的审批单 ID 集合。
     * approver_id 优先，历史数据（approver_id 为空）按 approver_name 兜底。
     * 用于 /list 的“我已审批”过滤；无匹配返回空集合。
     */
    List<String> listApprovalIdsByApprover(String approverId, String approverName);

    /**
     * 待我审批分页：approver_id 优先，历史数据（approver_id 为空）按 approver_name 兜底。
     *
     * @param approverId   当前登录人 sys_user.id（可空则只按姓名匹配）
     * @param approverName 当前登录人姓名
     * @param pageNo       页码
     * @param pageSize     页大小
     */
    IPage<TeamApproval> todo(String approverId, String approverName, int pageNo, int pageSize);
}
