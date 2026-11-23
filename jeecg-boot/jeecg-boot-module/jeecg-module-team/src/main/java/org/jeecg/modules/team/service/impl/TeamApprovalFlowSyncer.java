package org.jeecg.modules.team.service.impl;

import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.flow.service.FlowApproverResolver;
import org.jeecg.modules.flow.service.FlowTeamApprovalSyncService;
import org.jeecg.modules.team.entity.TeamApproval;
import org.jeecg.modules.team.entity.TeamApprovalFlowRecon;
import org.jeecg.modules.team.entity.TeamApprovalStep;
import org.jeecg.modules.team.enums.TeamApprovalStatus;
import org.jeecg.modules.team.mapper.TeamApprovalFlowReconMapper;
import org.jeecg.modules.team.mapper.TeamApprovalMapper;
import org.jeecg.modules.team.mapper.TeamApprovalStepMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

/**
 * team 审批链 ↔ Flowable 引擎双写的 team 侧同步器（W3）。
 *
 * 分工：引擎读写只在 {@link FlowTeamApprovalSyncService}（flow 模块），本类负责
 * 办理人组织解析兜底、team_approval.process_instance_id 锚定落库、
 * team_approval_flow_recon 对账登记。
 *
 * 口径（charter R2，并行观测期）：
 * <ul>
 *   <li>team 表为准，引擎侧只观测；本类所有观测态入口（onXxx）吞掉全部异常
 *       ——同步失败只记 warn 日志 + 对账行 diff_flag=1，绝不阻断 team 主流程。</li>
 *   <li>引擎镜像仅覆盖二级审批链（与 expenseProcess 同构）；非二级链不起实例、
 *       不登记对账（无镜像可观测）。</li>
 *   <li>对账一致性判定：pending(step1)↔RUNNING:deptApprove；pending(step2)↔RUNNING:vpApprove；
 *       approved↔FINISHED:approvedEnd；rejected/returned↔FINISHED:rejectedEnd
 *       （退回在引擎镜像中等价于未通过终止）；canceled↔DELETED；SYNC_FAILED/其余组合=不一致。</li>
 * </ul>
 *
 * 引擎驱动态（jeecg.team.approval.engine-driven=true）：driveXxx 同步驱动引擎，
 * 引擎异常直接上抛阻断 team 流转（此时 team 表被动跟随）；无引擎镜像的单据
 * （非二级链/历史单）自动回退 team 表自驱。本波只实现不翻转。
 */
@Slf4j
@Component
public class TeamApprovalFlowSyncer {

    /** 引擎镜像覆盖的审批链级数（与 expenseProcess 两级节点一一对应） */
    private static final int MIRROR_STEP_COUNT = 2;
    /** 对账行 detail 长度上限（与表结构 VARCHAR(1024) 对齐） */
    private static final int MAX_DETAIL_LENGTH = 1000;

    @Autowired
    private FlowTeamApprovalSyncService syncService;
    @Autowired
    private FlowApproverResolver approverResolver;
    @Autowired
    private TeamApprovalFlowReconMapper reconMapper;
    @Autowired
    private TeamApprovalMapper approvalMapper;
    @Autowired
    private TeamApprovalStepMapper stepMapper;

    /**
     * 提交/重提后的引擎镜像挂接（观测态，事务提交后由回调触发）。
     * 起实例 + 落 process_instance_id 锚定 + 登记对账；任一步失败仅告警 + 对账行留痕。
     */
    public void onSubmitted(String approvalId) {
        TeamApproval approval = approvalMapper.selectById(approvalId);
        if (approval == null) {
            return;
        }
        try {
            List<TeamApprovalStep> steps = listSteps(approvalId);
            if (steps.size() != MIRROR_STEP_COUNT) {
                log.info("approval chain is not {}-level, skip engine mirror: code={}, steps={}",
                        MIRROR_STEP_COUNT, approval.getCode(), steps.size());
                return;
            }
            String processInstanceId = syncService.startInstance(
                    approval.getCode(),
                    resolveApplicantUsername(approval),
                    approval.getAmount(),
                    resolveDeptManager(approval, steps.get(0)),
                    resolveVp(approval, steps.get(1)));
            approvalMapper.update(null, new LambdaUpdateWrapper<TeamApproval>()
                    .eq(TeamApproval::getId, approvalId)
                    .set(TeamApproval::getProcessInstanceId, processInstanceId));
            recordRecon(approvalId, processInstanceId, null);
        } catch (Exception e) {
            log.warn("engine mirror start failed (team flow unaffected): approvalId={}, code={}",
                    approvalId, approval.getCode(), e);
            recordReconQuietly(approvalId, null, "SYNC_FAILED", e.getMessage());
        }
    }

    /** 审批通过后的引擎同步（观测态，事务提交后触发） */
    public void onApproved(String approvalId) {
        syncAfterAction(approvalId, "APPROVE");
    }

    /** 审批拒绝后的引擎同步（观测态） */
    public void onRejected(String approvalId) {
        syncAfterAction(approvalId, "REJECT");
    }

    /** 审批退回后的引擎同步（观测态；引擎镜像按 rejectedEnd 终止） */
    public void onReturned(String approvalId) {
        syncAfterAction(approvalId, "REJECT");
    }

    /** 撤回后的引擎同步（观测态；删除在途实例） */
    public void onCanceled(String approvalId) {
        syncAfterAction(approvalId, "CANCEL");
    }

    /** 引擎驱动态：先 complete 引擎任务，team 表随后被动更新；引擎异常上抛阻断流转 */
    public void driveApprove(String approvalId) {
        String processInstanceId = requireMirror(approvalId);
        syncService.syncApprove(processInstanceId);
    }

    /** 引擎驱动态：拒绝/退回先驱动引擎；引擎异常上抛阻断流转 */
    public void driveReject(String approvalId) {
        String processInstanceId = requireMirror(approvalId);
        syncService.syncReject(processInstanceId);
    }

    /** 引擎驱动态：撤回先驱动引擎删除实例；引擎异常上抛阻断流转 */
    public void driveCancel(String approvalId) {
        String processInstanceId = requireMirror(approvalId);
        syncService.syncCancel(processInstanceId);
    }

    /** 对账不一致清单（diff_flag=1），供内部观测端点使用 */
    public List<TeamApprovalFlowRecon> listDiffs() {
        return reconMapper.selectList(new LambdaQueryWrapper<TeamApprovalFlowRecon>()
                .eq(TeamApprovalFlowRecon::getDiffFlag, 1)
                .orderByDesc(TeamApprovalFlowRecon::getUpdateTime));
    }

    /** 全量对账行（含一致），供观测端点 diffOnly=false 使用 */
    public List<TeamApprovalFlowRecon> listAll() {
        return reconMapper.selectList(new LambdaQueryWrapper<TeamApprovalFlowRecon>()
                .orderByDesc(TeamApprovalFlowRecon::getUpdateTime));
    }

    private void syncAfterAction(String approvalId, String action) {
        TeamApproval approval = approvalMapper.selectById(approvalId);
        if (approval == null || StringUtils.isBlank(approval.getProcessInstanceId())) {
            return;
        }
        String processInstanceId = approval.getProcessInstanceId();
        try {
            switch (action) {
                case "APPROVE":
                    syncService.syncApprove(processInstanceId);
                    break;
                case "REJECT":
                    syncService.syncReject(processInstanceId);
                    break;
                case "CANCEL":
                    syncService.syncCancel(processInstanceId);
                    break;
                default:
                    throw new IllegalStateException("unknown sync action: " + action);
            }
            recordRecon(approvalId, processInstanceId, null);
        } catch (Exception e) {
            log.warn("engine sync failed (team flow unaffected): approvalId={}, code={}, action={}",
                    approvalId, approval.getCode(), action, e);
            recordReconQuietly(approvalId, processInstanceId, "SYNC_FAILED", e.getMessage());
        }
    }

    private String requireMirror(String approvalId) {
        TeamApproval approval = approvalMapper.selectById(approvalId);
        if (approval == null || StringUtils.isBlank(approval.getProcessInstanceId())) {
            throw new JeecgBootException("引擎驱动模式缺少流程实例锚定，无法驱动: approvalId=" + approvalId);
        }
        return approval.getProcessInstanceId();
    }

    /**
     * 登记/刷新对账行：重新读取 team 单据最新状态，观测引擎快照，按口径判定一致性。
     *
     * @param expectEngineStatus 非空时跳过引擎观测直接采用（如同步失败时的 SYNC_FAILED 由调用方处理）
     */
    private void recordRecon(String approvalId, String processInstanceId, String expectEngineStatus) {
        TeamApproval approval = approvalMapper.selectById(approvalId);
        if (approval == null) {
            return;
        }
        String engineStatus = expectEngineStatus != null
                ? expectEngineStatus
                : syncService.observeState(processInstanceId);
        boolean consistent = isConsistent(approval, engineStatus);
        upsertRecon(approval, processInstanceId, engineStatus, consistent, null);
    }

    /** 观测态异常留痕：尽力登记 SYNC_FAILED 对账行，登记失败仅告警（不二次抛错） */
    private void recordReconQuietly(String approvalId, String processInstanceId, String engineStatus, String detail) {
        try {
            TeamApproval approval = approvalMapper.selectById(approvalId);
            if (approval == null) {
                return;
            }
            upsertRecon(approval, processInstanceId, engineStatus, false, detail);
        } catch (Exception e) {
            log.warn("recon record failed, observation lost: approvalId={}", approvalId, e);
        }
    }

    private void upsertRecon(TeamApproval approval, String processInstanceId, String engineStatus,
                             boolean consistent, String detail) {
        Date now = new Date();
        TeamApprovalFlowRecon existing = reconMapper.selectOne(new LambdaQueryWrapper<TeamApprovalFlowRecon>()
                .eq(TeamApprovalFlowRecon::getApprovalId, approval.getId()));
        if (existing == null) {
            TeamApprovalFlowRecon recon = new TeamApprovalFlowRecon()
                    .setApprovalId(approval.getId())
                    .setApprovalCode(approval.getCode())
                    .setProcessInstanceId(processInstanceId)
                    .setTeamStatus(approval.getStatus())
                    .setTeamCurrentStep(approval.getCurrentStep())
                    .setEngineStatus(engineStatus)
                    .setDiffFlag(consistent ? 0 : 1)
                    .setDetail(truncate(detail))
                    .setCreateTime(now)
                    .setUpdateTime(now);
            reconMapper.insert(recon);
            return;
        }
        existing.setProcessInstanceId(processInstanceId);
        existing.setTeamStatus(approval.getStatus());
        existing.setTeamCurrentStep(approval.getCurrentStep());
        existing.setEngineStatus(engineStatus);
        existing.setDiffFlag(consistent ? 0 : 1);
        existing.setDetail(truncate(detail));
        existing.setUpdateTime(now);
        reconMapper.updateById(existing);
    }

    /** 对账一致性口径（注释即契约，见类注释） */
    private boolean isConsistent(TeamApproval approval, String engineStatus) {
        TeamApprovalStatus status = TeamApprovalStatus.fromCode(approval.getStatus());
        if (status == null || engineStatus == null) {
            return false;
        }
        switch (status) {
            case PENDING:
                if (approval.getCurrentStep() == null) {
                    return false;
                }
                String expectedNode = approval.getCurrentStep() <= 1 ? "RUNNING:deptApprove" : "RUNNING:vpApprove";
                return expectedNode.equals(engineStatus);
            case APPROVED:
                return "FINISHED:approvedEnd".equals(engineStatus);
            case REJECTED:
            case RETURNED:
                return "FINISHED:rejectedEnd".equals(engineStatus);
            case CANCELED:
                return "DELETED".equals(engineStatus);
            default:
                return false;
        }
    }

    private String resolveApplicantUsername(TeamApproval approval) {
        String username = approverResolver.resolveUsernameById(approval.getApplicantId());
        return StringUtils.defaultIfBlank(username, approval.getApplicantName());
    }

    /** 部门主管：组织解析优先 → 步骤审批人 sys_user.id 反查 username → 审批人姓名兜底 */
    private String resolveDeptManager(TeamApproval approval, TeamApprovalStep step) {
        String resolved = approverResolver.resolveDeptManagerUsername(approval.getApplicantId());
        if (StringUtils.isNotBlank(resolved)) {
            return resolved;
        }
        return StringUtils.defaultIfBlank(
                approverResolver.resolveUsernameById(step.getApproverId()),
                step.getApproverName());
    }

    /** 副总：角色解析优先 → 步骤审批人 sys_user.id 反查 username → 审批人姓名兜底 */
    private String resolveVp(TeamApproval approval, TeamApprovalStep step) {
        String resolved = approverResolver.resolveVpUsername(approval.getApplicantId());
        if (StringUtils.isNotBlank(resolved)) {
            return resolved;
        }
        return StringUtils.defaultIfBlank(
                approverResolver.resolveUsernameById(step.getApproverId()),
                step.getApproverName());
    }

    private List<TeamApprovalStep> listSteps(String approvalId) {
        return stepMapper.selectList(new LambdaQueryWrapper<TeamApprovalStep>()
                .eq(TeamApprovalStep::getApprovalId, approvalId)
                .orderByAsc(TeamApprovalStep::getStepNo));
    }

    private String truncate(String detail) {
        if (detail == null) {
            return null;
        }
        return detail.length() <= MAX_DETAIL_LENGTH ? detail : detail.substring(0, MAX_DETAIL_LENGTH);
    }
}
