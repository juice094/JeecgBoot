package org.jeecg.modules.team.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private TeamApprovalStepMapper stepMapper;

    @Autowired
    private TeamAuditLogger auditLogger;

    @Autowired
    private ITeamApprovalTemplateService templateService;

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
        return approval;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String approvalId, String comment) {
        TeamApproval approval = requireStatus(approvalId, TeamApprovalStatus.PENDING);
        TeamApprovalStep step = requireActionableStep(approval);

        Date now = new Date();
        int stepUpdated = stepMapper.update(null, new LambdaUpdateWrapper<TeamApprovalStep>()
                .eq(TeamApprovalStep::getId, step.getId())
                .eq(TeamApprovalStep::getAction, ACTION_WAIT)
                .set(TeamApprovalStep::getAction, ACTION_APPROVED)
                .set(TeamApprovalStep::getComment, comment)
                .set(TeamApprovalStep::getActedAt, now));
        if (stepUpdated == 0) {
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
            throw new JeecgBootException("并发冲突：审批单状态已变化: code=" + approval.getCode());
        }

        auditLogger.log(TeamAuditBizType.APPROVAL, approval.getId(), approval.getCode(),
                TeamAuditAction.APPROVE, stepSnapshot,
                isLast ? TeamApprovalStatus.APPROVED.getCode() : stepSnapshot(newStep),
                comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String approvalId, String comment) {
        TeamApproval approval = requireStatus(approvalId, TeamApprovalStatus.PENDING);
        TeamApprovalStep step = requireActionableStep(approval);

        Date now = new Date();
        int stepUpdated = stepMapper.update(null, new LambdaUpdateWrapper<TeamApprovalStep>()
                .eq(TeamApprovalStep::getId, step.getId())
                .eq(TeamApprovalStep::getAction, ACTION_WAIT)
                .set(TeamApprovalStep::getAction, ACTION_REJECTED)
                .set(TeamApprovalStep::getComment, comment)
                .set(TeamApprovalStep::getActedAt, now));
        if (stepUpdated == 0) {
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
            throw new JeecgBootException("并发冲突：审批单状态已变化: code=" + approval.getCode());
        }

        auditLogger.log(TeamAuditBizType.APPROVAL, approval.getId(), approval.getCode(),
                TeamAuditAction.REJECT, stepSnapshot(step.getStepNo()),
                TeamApprovalStatus.REJECTED.getCode(), comment);
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

        int updated = baseMapper.update(null, new LambdaUpdateWrapper<TeamApproval>()
                .eq(TeamApproval::getId, approval.getId())
                .eq(TeamApproval::getStatus, TeamApprovalStatus.PENDING.getCode())
                .set(TeamApproval::getStatus, TeamApprovalStatus.CANCELED.getCode())
                .set(TeamApproval::getUpdateTime, new Date())
                .set(TeamApproval::getUpdateBy, loginUser.getUsername()));
        if (updated == 0) {
            throw new JeecgBootException("并发冲突：审批单状态已变化: code=" + approval.getCode());
        }

        auditLogger.log(TeamAuditBizType.APPROVAL, approval.getId(), approval.getCode(),
                TeamAuditAction.CANCEL, TeamApprovalStatus.PENDING.getCode(),
                TeamApprovalStatus.CANCELED.getCode(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnToApplicant(String approvalId, String comment) {
        TeamApproval approval = requireStatus(approvalId, TeamApprovalStatus.PENDING);
        TeamApprovalStep step = requireActionableStep(approval);

        Date now = new Date();
        int stepUpdated = stepMapper.update(null, new LambdaUpdateWrapper<TeamApprovalStep>()
                .eq(TeamApprovalStep::getId, step.getId())
                .eq(TeamApprovalStep::getAction, ACTION_WAIT)
                .set(TeamApprovalStep::getAction, ACTION_RETURNED)
                .set(TeamApprovalStep::getComment, comment)
                .set(TeamApprovalStep::getActedAt, now));
        if (stepUpdated == 0) {
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
            throw new JeecgBootException("并发冲突：审批单状态已变化: code=" + approval.getCode());
        }

        auditLogger.log(TeamAuditBizType.APPROVAL, approval.getId(), approval.getCode(),
                TeamAuditAction.RETURN, stepSnapshot(step.getStepNo()),
                TeamApprovalStatus.RETURNED.getCode(), comment);
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
            throw new JeecgBootException("并发冲突：审批单状态已变化: code=" + approval.getCode());
        }

        auditLogger.log(TeamAuditBizType.APPROVAL, approval.getId(), approval.getCode(),
                TeamAuditAction.RESUBMIT, TeamApprovalStatus.RETURNED.getCode(),
                TeamApprovalStatus.PENDING.getCode(), "新步骤数=" + effectiveSteps.size());

        approval.setStatus(TeamApprovalStatus.PENDING.getCode());
        approval.setCurrentStep(1);
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
        ApprovalFormValidator.Result result = ApprovalFormValidator.validate(schema, approval.getFormData());
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

    /** ponytail: 试点级单号生成，并发问题（P1）另行处理 */
    private String generateCode() {
        String day = new SimpleDateFormat("yyyyMMdd").format(new Date());
        LambdaQueryWrapper<TeamApproval> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(TeamApproval::getCode, "OA-" + day);
        long count = count(wrapper);
        return String.format("OA-%s-%03d", day, count + 1);
    }
}
