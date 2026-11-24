package org.jeecg.modules.team.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.log.EventLoggers;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.team.entity.TeamApproval;
import org.jeecg.modules.team.entity.TeamApprovalStep;
import org.jeecg.modules.team.entity.TeamApprovalTemplate;
import org.jeecg.modules.team.entity.TeamApprovalTemplateStep;
import org.jeecg.modules.team.enums.TeamApprovalStatus;
import org.jeecg.modules.team.enums.TeamAuditAction;
import org.jeecg.modules.team.enums.TeamAuditBizType;
import org.jeecg.modules.team.mapper.TeamApprovalMapper;
import org.jeecg.modules.team.mapper.TeamApprovalStepMapper;
import org.jeecg.modules.team.service.ITeamApprovalService;
import org.jeecg.modules.team.service.ITeamApprovalTemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 审批单状态机实现（v2：模板 + 撤回/退回/重提 + approver_id）
 * @Author: juice094
 * @Date: 2026-11-18
 * @Version: V2.0
 */
@Service
public class TeamApprovalServiceImpl extends ServiceImpl<TeamApprovalMapper, TeamApproval> implements ITeamApprovalService {

    private static final String ACTION_WAIT = "wait";
    private static final String ACTION_APPROVED = "approved";
    private static final String ACTION_REJECTED = "rejected";
    private static final String ACTION_RETURNED = "returned";

    /** 事件名（规划稿 §1 埋点清单） */
    private static final String EVENT_APPROVAL_SUBMITTED = "approval.submitted";
    private static final String EVENT_APPROVAL_TRANSITION = "approval.transition";
    private static final String EVENT_VALIDATION_FAILED = "approval.validation_failed";

    /** 事件结果口径 */
    private static final String RESULT_OK = "ok";
    private static final String RESULT_CONFLICT = "conflict";

    @Autowired
    private TeamApprovalStepMapper stepMapper;

    @Autowired
    private TeamAuditLogger auditLogger;

    @Autowired
    private ITeamApprovalTemplateService templateService;

    /**
     * W3 切流开关：引擎驱动态审批动作改由引擎任务驱动、team 表被动同步。
     * 本波只实现不翻转（默认 false=并行观测态，team 表为准、引擎侧只观测，charter R2）。
     */
    @Value("${jeecg.team.approval.engine-driven:false}")
    private boolean engineDriven;

    @Autowired
    private TeamApprovalFlowSyncer flowSyncer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamApproval submit(TeamApproval approval, List<TeamApprovalStep> clientSteps) {
        validateHeader(approval);
        List<TeamApprovalStep> effectiveSteps = resolveSteps(approval, clientSteps);
        validateStepChain(effectiveSteps, approval);
        applyFormValidation(approval);

        approval.setCode(generateCode());
        approval.setStatus(TeamApprovalStatus.PENDING.getCode());
        approval.setCurrentStep(1);
        approval.setCreateTime(new Date());
        save(approval);

        persistSteps(approval.getId(), effectiveSteps);

        auditLogger.log(TeamAuditBizType.APPROVAL, approval.getId(), approval.getCode(),
                TeamAuditAction.SUBMIT, null, TeamApprovalStatus.PENDING.getCode(),
                "步骤数=" + effectiveSteps.size() + ", 类别=" + approval.getCategory());
        EventLoggers.info(EventLoggers.TEAM_EVENT, EVENT_APPROVAL_SUBMITTED,
                "code", approval.getCode(),
                "category", approval.getCategory(),
                "amount", approval.getAmount(),
                "applicant", StringUtils.defaultIfBlank(approval.getApplicantName(), approval.getApplicantId()),
                "stepCount", effectiveSteps.size());
        // W3 双写：事务提交后起引擎镜像（观测态失败不阻断主流程，已由 syncer 兜底）
        afterCommitSync(() -> flowSyncer.onSubmitted(approval.getId()));
        return approval;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String approvalId, String comment) {
        TeamApproval approval = requireStatus(approvalId, TeamApprovalStatus.PENDING);
        TeamApprovalStep step = requireActionableStep(approval);
        // W3 引擎驱动态：守卫全部通过后先驱动引擎任务，失败上抛回滚，team 表被动跟随
        boolean driven = driveFirstIfEnabled(approvalId, approval, "approve");

        Date now = new Date();
        int stepUpdated = stepMapper.update(null, new LambdaUpdateWrapper<TeamApprovalStep>()
                .eq(TeamApprovalStep::getId, step.getId())
                .eq(TeamApprovalStep::getAction, ACTION_WAIT)
                .set(TeamApprovalStep::getAction, ACTION_APPROVED)
                .set(TeamApprovalStep::getComment, comment)
                .set(TeamApprovalStep::getActedAt, now));
        if (stepUpdated == 0) {
            logTransition(approval, stepSnapshot(step.getStepNo()), stepSnapshot(step.getStepNo()),
                    TeamAuditAction.APPROVE.getCode(), RESULT_CONFLICT);
            throw new JeecgBootException("并发冲突：当前步骤已被他人操作: code=" + approval.getCode());
        }

        long total = countSteps(approvalId);
        String stepSnapshot = stepSnapshot(step.getStepNo());
        boolean isLast = step.getStepNo() >= total;
        String newStatus = isLast ? TeamApprovalStatus.APPROVED.getCode() : TeamApprovalStatus.PENDING.getCode();
        int newStep = isLast ? step.getStepNo() : step.getStepNo() + 1;

        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        LambdaUpdateWrapper<TeamApproval> approvalUpdate = new LambdaUpdateWrapper<TeamApproval>()
                .eq(TeamApproval::getId, approval.getId())
                .eq(TeamApproval::getStatus, TeamApprovalStatus.PENDING.getCode())
                .eq(TeamApproval::getCurrentStep, approval.getCurrentStep())
                .set(TeamApproval::getStatus, newStatus)
                .set(TeamApproval::getCurrentStep, newStep)
                .set(TeamApproval::getUpdateTime, now);
        if (loginUser != null) {
            approvalUpdate.set(TeamApproval::getUpdateBy, loginUser.getUsername());
        }
        if (baseMapper.update(null, approvalUpdate) == 0) {
            logTransition(approval, stepSnapshot(step.getStepNo()),
                    isLast ? TeamApprovalStatus.APPROVED.getCode() : stepSnapshot(newStep),
                    TeamAuditAction.APPROVE.getCode(), RESULT_CONFLICT);
            throw new JeecgBootException("并发冲突：审批单状态已变化: code=" + approval.getCode());
        }

        auditLogger.log(TeamAuditBizType.APPROVAL, approval.getId(), approval.getCode(),
                TeamAuditAction.APPROVE, stepSnapshot,
                isLast ? TeamApprovalStatus.APPROVED.getCode() : stepSnapshot(newStep),
                comment);
        logTransition(approval, stepSnapshot,
                isLast ? TeamApprovalStatus.APPROVED.getCode() : stepSnapshot(newStep),
                TeamAuditAction.APPROVE.getCode(), RESULT_OK);
        if (!driven) {
            // W3 双写（观测态）：事务提交后同步引擎，失败仅告警 + 对账留痕
            afterCommitSync(() -> flowSyncer.onApproved(approvalId));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String approvalId, String comment) {
        TeamApproval approval = requireStatus(approvalId, TeamApprovalStatus.PENDING);
        TeamApprovalStep step = requireActionableStep(approval);
        boolean driven = driveFirstIfEnabled(approvalId, approval, "reject");

        Date now = new Date();
        int stepUpdated = stepMapper.update(null, new LambdaUpdateWrapper<TeamApprovalStep>()
                .eq(TeamApprovalStep::getId, step.getId())
                .eq(TeamApprovalStep::getAction, ACTION_WAIT)
                .set(TeamApprovalStep::getAction, ACTION_REJECTED)
                .set(TeamApprovalStep::getComment, comment)
                .set(TeamApprovalStep::getActedAt, now));
        if (stepUpdated == 0) {
            logTransition(approval, stepSnapshot(step.getStepNo()), stepSnapshot(step.getStepNo()),
                    TeamAuditAction.REJECT.getCode(), RESULT_CONFLICT);
            throw new JeecgBootException("并发冲突：当前步骤已被他人操作: code=" + approval.getCode());
        }

        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        LambdaUpdateWrapper<TeamApproval> approvalUpdate = new LambdaUpdateWrapper<TeamApproval>()
                .eq(TeamApproval::getId, approval.getId())
                .eq(TeamApproval::getStatus, TeamApprovalStatus.PENDING.getCode())
                .set(TeamApproval::getStatus, TeamApprovalStatus.REJECTED.getCode())
                .set(TeamApproval::getUpdateTime, now);
        if (loginUser != null) {
            approvalUpdate.set(TeamApproval::getUpdateBy, loginUser.getUsername());
        }
        if (baseMapper.update(null, approvalUpdate) == 0) {
            logTransition(approval, stepSnapshot(step.getStepNo()), TeamApprovalStatus.REJECTED.getCode(),
                    TeamAuditAction.REJECT.getCode(), RESULT_CONFLICT);
            throw new JeecgBootException("并发冲突：审批单状态已变化: code=" + approval.getCode());
        }

        auditLogger.log(TeamAuditBizType.APPROVAL, approval.getId(), approval.getCode(),
                TeamAuditAction.REJECT, stepSnapshot(step.getStepNo()),
                TeamApprovalStatus.REJECTED.getCode(), comment);
        logTransition(approval, stepSnapshot(step.getStepNo()), TeamApprovalStatus.REJECTED.getCode(),
                TeamAuditAction.REJECT.getCode(), RESULT_OK);
        if (!driven) {
            afterCommitSync(() -> flowSyncer.onRejected(approvalId));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(String approvalId) {
        TeamApproval approval = requireStatus(approvalId, TeamApprovalStatus.PENDING);
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser == null) {
            throw new JeecgBootException("未登录，无法撤回审批单");
        }
        if (!loginUser.getId().equals(approval.getApplicantId())) {
            throw new JeecgBootException("仅申请人可撤回审批单: code=" + approval.getCode());
        }
        if (countStepsByActionNot(approvalId, ACTION_WAIT) > 0) {
            throw new JeecgBootException("审批单已被处理，不可撤回: code=" + approval.getCode());
        }
        boolean driven = driveFirstIfEnabled(approvalId, approval, "cancel");

        int updated = baseMapper.update(null, new LambdaUpdateWrapper<TeamApproval>()
                .eq(TeamApproval::getId, approval.getId())
                .eq(TeamApproval::getStatus, TeamApprovalStatus.PENDING.getCode())
                .set(TeamApproval::getStatus, TeamApprovalStatus.CANCELED.getCode())
                .set(TeamApproval::getUpdateTime, new Date())
                .set(TeamApproval::getUpdateBy, loginUser.getUsername()));
        if (updated == 0) {
            logTransition(approval, TeamApprovalStatus.PENDING.getCode(), TeamApprovalStatus.CANCELED.getCode(),
                    TeamAuditAction.CANCEL.getCode(), RESULT_CONFLICT);
            throw new JeecgBootException("并发冲突：审批单状态已变化: code=" + approval.getCode());
        }

        auditLogger.log(TeamAuditBizType.APPROVAL, approval.getId(), approval.getCode(),
                TeamAuditAction.CANCEL, TeamApprovalStatus.PENDING.getCode(),
                TeamApprovalStatus.CANCELED.getCode(), null);
        logTransition(approval, TeamApprovalStatus.PENDING.getCode(), TeamApprovalStatus.CANCELED.getCode(),
                TeamAuditAction.CANCEL.getCode(), RESULT_OK);
        if (!driven) {
            afterCommitSync(() -> flowSyncer.onCanceled(approvalId));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnToApplicant(String approvalId, String comment) {
        TeamApproval approval = requireStatus(approvalId, TeamApprovalStatus.PENDING);
        TeamApprovalStep step = requireActionableStep(approval);
        boolean driven = driveFirstIfEnabled(approvalId, approval, "return");

        Date now = new Date();
        int stepUpdated = stepMapper.update(null, new LambdaUpdateWrapper<TeamApprovalStep>()
                .eq(TeamApprovalStep::getId, step.getId())
                .eq(TeamApprovalStep::getAction, ACTION_WAIT)
                .set(TeamApprovalStep::getAction, ACTION_RETURNED)
                .set(TeamApprovalStep::getComment, comment)
                .set(TeamApprovalStep::getActedAt, now));
        if (stepUpdated == 0) {
            logTransition(approval, stepSnapshot(step.getStepNo()), stepSnapshot(step.getStepNo()),
                    TeamAuditAction.RETURN.getCode(), RESULT_CONFLICT);
            throw new JeecgBootException("并发冲突：当前步骤已被他人操作: code=" + approval.getCode());
        }

        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        LambdaUpdateWrapper<TeamApproval> approvalUpdate = new LambdaUpdateWrapper<TeamApproval>()
                .eq(TeamApproval::getId, approval.getId())
                .eq(TeamApproval::getStatus, TeamApprovalStatus.PENDING.getCode())
                .set(TeamApproval::getStatus, TeamApprovalStatus.RETURNED.getCode())
                .set(TeamApproval::getUpdateTime, now);
        if (loginUser != null) {
            approvalUpdate.set(TeamApproval::getUpdateBy, loginUser.getUsername());
        }
        if (baseMapper.update(null, approvalUpdate) == 0) {
            logTransition(approval, stepSnapshot(step.getStepNo()), TeamApprovalStatus.RETURNED.getCode(),
                    TeamAuditAction.RETURN.getCode(), RESULT_CONFLICT);
            throw new JeecgBootException("并发冲突：审批单状态已变化: code=" + approval.getCode());
        }

        auditLogger.log(TeamAuditBizType.APPROVAL, approval.getId(), approval.getCode(),
                TeamAuditAction.RETURN, stepSnapshot(step.getStepNo()),
                TeamApprovalStatus.RETURNED.getCode(), comment);
        logTransition(approval, stepSnapshot(step.getStepNo()), TeamApprovalStatus.RETURNED.getCode(),
                TeamAuditAction.RETURN.getCode(), RESULT_OK);
        if (!driven) {
            // 引擎镜像中退回等价于 rejectedEnd 终止（对账口径见 TeamApprovalFlowSyncer）
            afterCommitSync(() -> flowSyncer.onReturned(approvalId));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamApproval resubmit(String approvalId, List<TeamApprovalStep> clientSteps) {
        TeamApproval approval = requireStatus(approvalId, TeamApprovalStatus.RETURNED);
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser == null) {
            throw new JeecgBootException("未登录，无法重提审批单");
        }
        if (!loginUser.getId().equals(approval.getApplicantId())) {
            throw new JeecgBootException("仅申请人可重提审批单: code=" + approval.getCode());
        }
        List<TeamApprovalStep> effectiveSteps = resolveSteps(approval, clientSteps);
        validateStepChain(effectiveSteps, approval);

        LambdaQueryWrapper<TeamApprovalStep> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(TeamApprovalStep::getApprovalId, approvalId);
        stepMapper.delete(delWrapper);
        persistSteps(approvalId, effectiveSteps);

        int updated = baseMapper.update(null, new LambdaUpdateWrapper<TeamApproval>()
                .eq(TeamApproval::getId, approval.getId())
                .eq(TeamApproval::getStatus, TeamApprovalStatus.RETURNED.getCode())
                .set(TeamApproval::getStatus, TeamApprovalStatus.PENDING.getCode())
                .set(TeamApproval::getCurrentStep, 1)
                .set(TeamApproval::getUpdateTime, new Date())
                .set(TeamApproval::getUpdateBy, loginUser.getUsername()));
        if (updated == 0) {
            logTransition(approval, TeamApprovalStatus.RETURNED.getCode(), TeamApprovalStatus.PENDING.getCode(),
                    TeamAuditAction.RESUBMIT.getCode(), RESULT_CONFLICT);
            throw new JeecgBootException("并发冲突：审批单状态已变化: code=" + approval.getCode());
        }

        auditLogger.log(TeamAuditBizType.APPROVAL, approval.getId(), approval.getCode(),
                TeamAuditAction.RESUBMIT, TeamApprovalStatus.RETURNED.getCode(),
                TeamApprovalStatus.PENDING.getCode(), "新步骤数=" + effectiveSteps.size());
        logTransition(approval, TeamApprovalStatus.RETURNED.getCode(), TeamApprovalStatus.PENDING.getCode(),
                TeamAuditAction.RESUBMIT.getCode(), RESULT_OK);

        approval.setStatus(TeamApprovalStatus.PENDING.getCode());
        approval.setCurrentStep(1);
        // W3 双写：重提后重启引擎镜像（旧镜像已 rejectedEnd 终止，换新实例重新锚定）
        afterCommitSync(() -> flowSyncer.onSubmitted(approvalId));
        return approval;
    }

    @Override
    public List<TeamApprovalStep> listSteps(String approvalId) {
        LambdaQueryWrapper<TeamApprovalStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamApprovalStep::getApprovalId, approvalId).orderByAsc(TeamApprovalStep::getStepNo);
        return stepMapper.selectList(wrapper);
    }

    @Override
    public List<String> listApprovalIdsByApprover(String approverId, String approverName) {
        if (StringUtils.isBlank(approverId) && StringUtils.isBlank(approverName)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<TeamApprovalStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> {
            if (StringUtils.isNotBlank(approverId)) {
                w.eq(TeamApprovalStep::getApproverId, approverId);
                if (StringUtils.isNotBlank(approverName)) {
                    w.or(sub -> sub.isNull(TeamApprovalStep::getApproverId)
                            .eq(TeamApprovalStep::getApproverName, approverName));
                }
            } else {
                w.eq(TeamApprovalStep::getApproverName, approverName);
            }
        });
        return stepMapper.selectList(wrapper).stream()
                .map(TeamApprovalStep::getApprovalId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public IPage<TeamApproval> todo(String approverId, String approverName, int pageNo, int pageSize) {
        if (StringUtils.isBlank(approverId) && StringUtils.isBlank(approverName)) {
            throw new JeecgBootException("无法识别当前登录人，不能查询待我审批");
        }
        LambdaQueryWrapper<TeamApprovalStep> stepWrapper = new LambdaQueryWrapper<>();
        stepWrapper.eq(TeamApprovalStep::getAction, ACTION_WAIT);
        stepWrapper.and(w -> {
            if (StringUtils.isNotBlank(approverId)) {
                w.eq(TeamApprovalStep::getApproverId, approverId)
                        .or(sub -> sub.isNull(TeamApprovalStep::getApproverId)
                                .eq(TeamApprovalStep::getApproverName, approverName));
            } else {
                w.isNull(TeamApprovalStep::getApproverId)
                        .eq(TeamApprovalStep::getApproverName, approverName);
            }
        });
        List<TeamApprovalStep> waitSteps = stepMapper.selectList(stepWrapper);
        Page<TeamApproval> page = new Page<>(pageNo, pageSize);
        if (waitSteps.isEmpty()) {
            page.setTotal(0);
            page.setRecords(new ArrayList<>());
            return page;
        }
        List<String> ids = waitSteps.stream()
                .map(TeamApprovalStep::getApprovalId).distinct().collect(Collectors.toList());
        LambdaQueryWrapper<TeamApproval> approvalWrapper = new LambdaQueryWrapper<>();
        approvalWrapper.in(TeamApproval::getId, ids)
                .eq(TeamApproval::getStatus, TeamApprovalStatus.PENDING.getCode());
        List<TeamApproval> matched = ApprovalTodoMatcher.match(waitSteps, list(approvalWrapper));

        int total = matched.size();
        int fromIndex = Math.min(Math.max((pageNo - 1) * pageSize, 0), total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        page.setTotal(total);
        page.setRecords(matched.subList(fromIndex, toIndex));
        return page;
    }

    /** 模板优先；回退到客户端 steps；都没有报错 */
    private List<TeamApprovalStep> resolveSteps(TeamApproval approval, List<TeamApprovalStep> clientSteps) {
        TeamApprovalTemplate template = templateService.findEnabledByCategory(approval.getCategory());
        if (template != null) {
            List<TeamApprovalTemplateStep> tplSteps = templateService.listSteps(template.getId());
            if (!tplSteps.isEmpty()) {
                List<TeamApprovalStep> steps = new ArrayList<>(tplSteps.size());
                for (TeamApprovalTemplateStep ts : tplSteps) {
                    TeamApprovalStep s = new TeamApprovalStep();
                    s.setApproverId(ts.getApproverId());
                    s.setApproverName(ts.getApproverName());
                    s.setApproverRole(ts.getApproverRole());
                    steps.add(s);
                }
                return steps;
            }
        }
        if (clientSteps != null && !clientSteps.isEmpty()) {
            return new ArrayList<>(clientSteps);
        }
        throw new JeecgBootException("审批类别「" + approval.getCategory()
                + "」无可用模板，且未提供审批步骤");
    }

    /**
     * 字段模板校验：按 category 对应模板的 field_schema 复核 form_data，
     * 回填清洗后的 formData 与提取出的 amount。模板未配 schema 时跳过（向后兼容）。
     */
    private void applyFormValidation(TeamApproval approval) {
        TeamApprovalTemplate template = templateService.findEnabledByCategory(approval.getCategory());
        String schema = template == null ? null : template.getFieldSchema();
        ApprovalFormValidator.Result result;
        try {
            result = ApprovalFormValidator.validate(schema, approval.getFormData());
        } catch (JeecgBootException e) {
            // 负路径埋点（规划稿 §1 approval.validation_failed）：模板字段校验拒绝，观测前端绕过尝试
            EventLoggers.info(EventLoggers.TEAM_EVENT, EVENT_VALIDATION_FAILED,
                    "category", approval.getCategory(),
                    "reason", e.getMessage());
            throw e;
        }
        approval.setFormData(result.toJson());
        if (result.getAmount() != null) {
            approval.setAmount(result.getAmount());
        }
    }

    /** 头部字段校验：标题、起止日期 */
    private void validateHeader(TeamApproval approval) {
        if (StringUtils.isBlank(approval.getTitle())) {
            throw new JeecgBootException("事项标题不能为空");
        }
        if (approval.getStartDate() != null && approval.getEndDate() != null
                && approval.getStartDate().after(approval.getEndDate())) {
            throw new JeecgBootException("开始日期不能晚于结束日期: " + approval.getStartDate() + " ~ " + approval.getEndDate());
        }
    }

    /** 步骤链校验：非空、审批人非空、不含申请人本人 */
    private void validateStepChain(List<TeamApprovalStep> steps, TeamApproval approval) {
        if (steps == null || steps.isEmpty()) {
            throw new JeecgBootException("审批流程至少需要一个步骤");
        }
        int index = 0;
        for (TeamApprovalStep step : steps) {
            index++;
            if (StringUtils.isBlank(step.getApproverName())) {
                throw new JeecgBootException("审批步骤缺少审批人: 第 " + index + " 步");
            }
            if (StringUtils.isNotBlank(approval.getApplicantId())
                    && approval.getApplicantId().equals(step.getApproverId())) {
                throw new JeecgBootException("申请人不能出现在审批链中: 第 " + index + " 步");
            }
            if (StringUtils.isNotBlank(approval.getApplicantName())
                    && approval.getApplicantName().equals(step.getApproverName())) {
                throw new JeecgBootException("申请人不能出现在审批链中: 第 " + index + " 步");
            }
        }
    }

    /** 清理客户端 id，统一回填 wait 状态后落库 */
    private void persistSteps(String approvalId, List<TeamApprovalStep> steps) {
        int stepNo = 1;
        for (TeamApprovalStep step : steps) {
            step.setId(null);
            step.setApprovalId(approvalId);
            step.setStepNo(stepNo++);
            step.setAction(ACTION_WAIT);
            step.setComment(null);
            step.setActedAt(null);
            step.setCreateTime(new Date());
            stepMapper.insert(step);
        }
    }

    private TeamApproval requireStatus(String approvalId, TeamApprovalStatus expected) {
        TeamApproval approval = getById(approvalId);
        if (approval == null) {
            throw new JeecgBootException("审批单不存在: id=" + approvalId);
        }
        if (!expected.getCode().equals(approval.getStatus())) {
            throw new JeecgBootException("审批单当前状态为 " + approval.getStatus()
                    + "，期望 " + expected.getCode() + "（code=" + approval.getCode() + "）");
        }
        return approval;
    }

    /**
     * 取当前步骤并做操作合法性守卫：
     * 1. 步骤存在性；2. 步骤未被操作过；3. approver_id 优先，缺失按姓名兜底越权校验；
     * 4. 自审拦截：申请人不能审批/拒绝/退回自己的单据。
     */
    private TeamApprovalStep requireActionableStep(TeamApproval approval) {
        TeamApprovalStep step = currentStep(approval);
        if (!ACTION_WAIT.equals(step.getAction())) {
            throw new JeecgBootException("当前步骤已被操作，禁止重复审批: code=" + approval.getCode()
                    + ", stepNo=" + step.getStepNo() + ", action=" + step.getAction());
        }
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser != null) {
            boolean matched;
            if (StringUtils.isNotBlank(step.getApproverId())) {
                matched = step.getApproverId().equals(loginUser.getId());
            } else {
                matched = StringUtils.isNotBlank(step.getApproverName())
                        && step.getApproverName().equals(loginUser.getRealname());
            }
            if (!matched) {
                throw new JeecgBootException("越权审批: 当前步骤审批人为 " + step.getApproverName()
                        + "，登录人为 " + loginUser.getRealname() + "（code=" + approval.getCode() + "）");
            }
            if (StringUtils.isNotBlank(approval.getApplicantId())
                    && approval.getApplicantId().equals(loginUser.getId())) {
                throw new JeecgBootException("申请人不能审批自己的单据: code=" + approval.getCode());
            }
        }
        return step;
    }

    private TeamApprovalStep currentStep(TeamApproval approval) {
        LambdaQueryWrapper<TeamApprovalStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamApprovalStep::getApprovalId, approval.getId())
                .eq(TeamApprovalStep::getStepNo, approval.getCurrentStep());
        TeamApprovalStep step = stepMapper.selectOne(wrapper);
        if (step == null) {
            throw new JeecgBootException("审批步骤数据异常：找不到当前步骤");
        }
        return step;
    }

    private long countSteps(String approvalId) {
        LambdaQueryWrapper<TeamApprovalStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamApprovalStep::getApprovalId, approvalId);
        return stepMapper.selectCount(wrapper);
    }

    private long countStepsByActionNot(String approvalId, String action) {
        LambdaQueryWrapper<TeamApprovalStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamApprovalStep::getApprovalId, approvalId).ne(TeamApprovalStep::getAction, action);
        return stepMapper.selectCount(wrapper);
    }

    /** 审计留痕的步骤快照格式（from/to 值统一口径） */
    private String stepSnapshot(int stepNo) {
        return "step:" + stepNo;
    }

    /**
     * 事件日志（技术/运营面）：审批状态迁移，含并发冲突拒绝（result=conflict）。
     * 审计入库仍走 TeamAuditLogger，双轨不合并（规划稿 §2.3）。
     */
    private void logTransition(TeamApproval approval, String from, String to, String action, String result) {
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        EventLoggers.info(EventLoggers.TEAM_EVENT, EVENT_APPROVAL_TRANSITION,
                "code", approval.getCode(),
                "from", from,
                "to", to,
                "action", action,
                "result", result,
                "operator", loginUser == null ? null : loginUser.getUsername());
    }

    /**
     * W3 引擎驱动态（jeecg.team.approval.engine-driven=true）：守卫通过后先同步驱动引擎，
     * team 表随后被动更新；引擎异常上抛使 team 事务回滚（与观测态的「失败不阻断」相反）。
     * 无引擎镜像的单据（非二级链/历史单）自动回退 team 表自驱 + 观测态同步。
     *
     * @return true=已由引擎驱动（调用方不得再注册观测态同步）；false=回退观测态
     */
    private boolean driveFirstIfEnabled(String approvalId, TeamApproval approval, String action) {
        if (!engineDriven || StringUtils.isBlank(approval.getProcessInstanceId())) {
            return false;
        }
        switch (action) {
            case "approve":
                flowSyncer.driveApprove(approvalId);
                break;
            case "reject":
            case "return":
                flowSyncer.driveReject(approvalId);
                break;
            case "cancel":
                flowSyncer.driveCancel(approvalId);
                break;
            default:
                throw new IllegalStateException("unknown drive action: " + action);
        }
        return true;
    }

    /**
     * 事务提交后触发双写同步：避免 team 事务回滚留下「引擎已起实例、单据不存在」的孤儿镜像；
     * 无事务上下文（如单元测试直调）时同步执行。
     */
    private void afterCommitSync(Runnable syncAction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    syncAction.run();
                }
            });
            return;
        }
        syncAction.run();
    }

    /** ponytail: 试点级单号生成，并发问题（P1）另行处理 */
    private String generateCode() {
        String day = new SimpleDateFormat("yyyyMMdd").format(new Date());
        LambdaQueryWrapper<TeamApproval> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(TeamApproval::getCode, "OA-" + day);
        long count = count(wrapper);
        return String.format("OA-%s-%03d", day, count + 1);
    }
}
