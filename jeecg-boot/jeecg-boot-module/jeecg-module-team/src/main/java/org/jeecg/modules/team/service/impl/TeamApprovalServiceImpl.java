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
import org.jeecg.modules.team.enums.TeamAuditAction;
import org.jeecg.modules.team.enums.TeamAuditBizType;
import org.jeecg.modules.team.mapper.TeamApprovalMapper;
import org.jeecg.modules.team.mapper.TeamApprovalStepMapper;
import org.jeecg.modules.team.service.ITeamApprovalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 审批单状态机实现
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 */
@Service
public class TeamApprovalServiceImpl extends ServiceImpl<TeamApprovalMapper, TeamApproval> implements ITeamApprovalService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";
    private static final String ACTION_WAIT = "wait";
    private static final String ACTION_APPROVED = "approved";
    private static final String ACTION_REJECTED = "rejected";

    @Autowired
    private TeamApprovalStepMapper stepMapper;

    @Autowired
    private TeamAuditLogger auditLogger;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamApproval submit(TeamApproval approval, List<TeamApprovalStep> stepTemplate) {
        if (StringUtils.isBlank(approval.getTitle())) {
            throw new JeecgBootException("事项标题不能为空");
        }
        if (approval.getStartDate() != null && approval.getEndDate() != null
                && approval.getStartDate().after(approval.getEndDate())) {
            throw new JeecgBootException("开始日期不能晚于结束日期: " + approval.getStartDate() + " ~ " + approval.getEndDate());
        }
        if (stepTemplate == null || stepTemplate.isEmpty()) {
            throw new JeecgBootException("审批流程至少需要一个步骤");
        }
        for (TeamApprovalStep step : stepTemplate) {
            if (StringUtils.isBlank(step.getApproverName())) {
                throw new JeecgBootException("审批步骤缺少审批人: 第 " + (stepTemplate.indexOf(step) + 1) + " 步");
            }
        }
        approval.setCode(generateCode());
        approval.setStatus(STATUS_PENDING);
        approval.setCurrentStep(1);
        approval.setCreateTime(new Date());
        save(approval);

        int stepNo = 1;
        for (TeamApprovalStep step : stepTemplate) {
            step.setApprovalId(approval.getId());
            step.setStepNo(stepNo++);
            step.setAction(ACTION_WAIT);
            step.setCreateTime(new Date());
            stepMapper.insert(step);
        }

        auditLogger.log(TeamAuditBizType.APPROVAL, approval.getId(), approval.getCode(),
                TeamAuditAction.SUBMIT, null, STATUS_PENDING,
                "步骤数=" + stepTemplate.size() + ", 类别=" + approval.getCategory());
        return approval;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String approvalId, String comment) {
        TeamApproval approval = requirePending(approvalId);
        TeamApprovalStep step = requireActionableStep(approval);
        step.setAction(ACTION_APPROVED);
        step.setComment(comment);
        step.setActedAt(new Date());
        stepMapper.updateById(step);

        long total = countSteps(approvalId);
        String stepSnapshot = stepSnapshot(step.getStepNo());
        if (step.getStepNo() >= total) {
            approval.setStatus(STATUS_APPROVED);
        } else {
            approval.setCurrentStep(step.getStepNo() + 1);
        }
        approval.setUpdateTime(new Date());
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser != null) {
            approval.setUpdateBy(loginUser.getUsername());
        }
        updateById(approval);

        auditLogger.log(TeamAuditBizType.APPROVAL, approval.getId(), approval.getCode(),
                TeamAuditAction.APPROVE, stepSnapshot,
                STATUS_APPROVED.equals(approval.getStatus()) ? STATUS_APPROVED : stepSnapshot(approval.getCurrentStep()),
                comment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String approvalId, String comment) {
        TeamApproval approval = requirePending(approvalId);
        TeamApprovalStep step = requireActionableStep(approval);
        step.setAction(ACTION_REJECTED);
        step.setComment(comment);
        step.setActedAt(new Date());
        stepMapper.updateById(step);

        approval.setStatus(STATUS_REJECTED);
        approval.setUpdateTime(new Date());
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser != null) {
            approval.setUpdateBy(loginUser.getUsername());
        }
        updateById(approval);

        auditLogger.log(TeamAuditBizType.APPROVAL, approval.getId(), approval.getCode(),
                TeamAuditAction.REJECT, stepSnapshot(step.getStepNo()), STATUS_REJECTED, comment);
    }

    /** 审计留痕的步骤快照格式（from/to 值统一口径） */
    private String stepSnapshot(int stepNo) {
        return "step:" + stepNo;
    }

    @Override
    public List<TeamApprovalStep> listSteps(String approvalId) {
        LambdaQueryWrapper<TeamApprovalStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeamApprovalStep::getApprovalId, approvalId).orderByAsc(TeamApprovalStep::getStepNo);
        return stepMapper.selectList(wrapper);
    }

    private TeamApproval requirePending(String approvalId) {
        TeamApproval approval = getById(approvalId);
        if (approval == null) {
            throw new JeecgBootException("审批单不存在: id=" + approvalId);
        }
        if (!STATUS_PENDING.equals(approval.getStatus())) {
            throw new JeecgBootException("审批单当前状态为 " + approval.getStatus() + "，不可审批: code=" + approval.getCode());
        }
        return approval;
    }

    @Override
    public IPage<TeamApproval> todo(String approverName, int pageNo, int pageSize) {
        if (StringUtils.isBlank(approverName)) {
            throw new JeecgBootException("无法识别当前登录人姓名，不能查询待我审批");
        }
        // 第一步：step 表按审批人+待操作查（走 idx_step_approval，不 join）
        LambdaQueryWrapper<TeamApprovalStep> stepWrapper = new LambdaQueryWrapper<>();
        stepWrapper.eq(TeamApprovalStep::getAction, ACTION_WAIT)
                .eq(TeamApprovalStep::getApproverName, approverName);
        List<TeamApprovalStep> waitSteps = stepMapper.selectList(stepWrapper);
        Page<TeamApproval> page = new Page<>(pageNo, pageSize);
        if (waitSteps.isEmpty()) {
            page.setTotal(0);
            page.setRecords(new ArrayList<>());
            return page;
        }
        // 第二步：回主表按 id + pending 过滤，current_step 匹配交给纯逻辑（可自测）
        List<String> ids = waitSteps.stream()
                .map(TeamApprovalStep::getApprovalId).distinct().collect(Collectors.toList());
        LambdaQueryWrapper<TeamApproval> approvalWrapper = new LambdaQueryWrapper<>();
        approvalWrapper.in(TeamApproval::getId, ids).eq(TeamApproval::getStatus, STATUS_PENDING);
        List<TeamApproval> matched = ApprovalTodoMatcher.match(waitSteps, list(approvalWrapper));

        // ponytail: 匹配后的内存分页；数据量上来后换 SQL 窗口函数方案
        int total = matched.size();
        int fromIndex = Math.min(Math.max((pageNo - 1) * pageSize, 0), total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        page.setTotal(total);
        page.setRecords(matched.subList(fromIndex, toIndex));
        return page;
    }

    /**
     * 取当前步骤并做操作合法性守卫（异常均带单据上下文）：
     * 1. 步骤存在性；
     * 2. 步骤未被操作过（防双击/并发重复审批把 currentStep 连推两步）；
     * 3. 越权校验：登录人必须是本步骤审批人（step.approverName 非空时按姓名比对，
     *    试点级方案——步骤表只冗余姓名无 approverId，严格校验需后续加列）；
     * 4. 自审拦截：申请人不能审批/拒绝自己的单子。
     */
    private TeamApprovalStep requireActionableStep(TeamApproval approval) {
        TeamApprovalStep step = currentStep(approval);
        if (!ACTION_WAIT.equals(step.getAction())) {
            throw new JeecgBootException("当前步骤已被操作，禁止重复审批: code=" + approval.getCode()
                    + ", stepNo=" + step.getStepNo() + ", action=" + step.getAction());
        }
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser != null) {
            if (StringUtils.isNotBlank(step.getApproverName())
                    && !step.getApproverName().equals(loginUser.getRealname())) {
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

    /** ponytail: 试点级单号生成（日期+当日序号3位），高并发时需换 Redis 发号 */
    private String generateCode() {
        String day = new SimpleDateFormat("yyyyMMdd").format(new Date());
        LambdaQueryWrapper<TeamApproval> wrapper = new LambdaQueryWrapper<>();
        wrapper.likeRight(TeamApproval::getCode, "OA-" + day);
        long count = count(wrapper);
        return String.format("OA-%s-%03d", day, count + 1);
    }
}
