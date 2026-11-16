package org.jeecg.modules.team.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.team.entity.TeamApproval;
import org.jeecg.modules.team.entity.TeamApprovalStep;
import org.jeecg.modules.team.mapper.TeamApprovalMapper;
import org.jeecg.modules.team.mapper.TeamApprovalStepMapper;
import org.jeecg.modules.team.service.ITeamApprovalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: 审批单状态机实现
 * @Author: Kimi Code
 * @Date: 2026-11-17
 * @Version: V1.0
 */
@Service
public class TeamApprovalServiceImpl extends ServiceImpl<TeamApprovalMapper, TeamApproval> implements ITeamApprovalService {

    @Autowired
    private TeamApprovalStepMapper stepMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeamApproval submit(TeamApproval approval, List<TeamApprovalStep> stepTemplate) {
        if (StringUtils.isBlank(approval.getTitle())) {
            throw new JeecgBootException("事项标题不能为空");
        }
        if (stepTemplate == null || stepTemplate.isEmpty()) {
            throw new JeecgBootException("审批流程至少需要一个步骤");
        }
        approval.setCode(generateCode());
        approval.setStatus("pending");
        approval.setCurrentStep(1);
        approval.setCreateTime(new Date());
        save(approval);

        int stepNo = 1;
        for (TeamApprovalStep step : stepTemplate) {
            step.setApprovalId(approval.getId());
            step.setStepNo(stepNo++);
            step.setAction("wait");
            step.setCreateTime(new Date());
            stepMapper.insert(step);
        }
        return approval;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(String approvalId, String comment) {
        TeamApproval approval = requirePending(approvalId);
        TeamApprovalStep step = currentStep(approval);
        step.setAction("approved");
        step.setComment(comment);
        step.setActedAt(new Date());
        stepMapper.updateById(step);

        long total = countSteps(approvalId);
        if (step.getStepNo() >= total) {
            approval.setStatus("approved");
        } else {
            approval.setCurrentStep(step.getStepNo() + 1);
        }
        approval.setUpdateTime(new Date());
        updateById(approval);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(String approvalId, String comment) {
        TeamApproval approval = requirePending(approvalId);
        TeamApprovalStep step = currentStep(approval);
        step.setAction("rejected");
        step.setComment(comment);
        step.setActedAt(new Date());
        stepMapper.updateById(step);

        approval.setStatus("rejected");
        approval.setUpdateTime(new Date());
        updateById(approval);
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
            throw new JeecgBootException("审批单不存在");
        }
        if (!"pending".equals(approval.getStatus())) {
            throw new JeecgBootException("单据已终审，不可再操作");
        }
        return approval;
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
