package org.jeecg.modules.team.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jeecg.modules.team.entity.TeamApproval;
import org.jeecg.modules.team.entity.TeamApprovalStep;

/**
 * @Description: 审批"待我审批"两步走匹配的纯逻辑（便于无库自测）
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 *
 * 第一步查出的 wait 步骤与第二步查出的候选单据在内存中匹配：
 * 仅当单据的 current_step 等于该单据在 wait 步骤表中的步骤序号才算"待我审批"，
 * 防止已流转到下一步/已被他人操作的步骤被误列。
 */
public final class ApprovalTodoMatcher {

    /**
     * 从候选单据中筛出当前待审步骤确属于给定 wait 步骤的单据，按创建时间倒序
     *
     * @param waitSteps  第一步结果：action='wait' 且 approver_name=当前用户 的步骤
     * @param candidates 第二步结果：主键命中且 status=pending 的单据
     * @return 匹配成功的单据（倒序，新单在前）
     */
    public static List<TeamApproval> match(List<TeamApprovalStep> waitSteps, List<TeamApproval> candidates) {
        if (waitSteps == null || waitSteps.isEmpty() || candidates == null || candidates.isEmpty()) {
            return new ArrayList<>();
        }
        // 同一单据可能有多条 wait 步骤（数据异常时），取最小 stepNo 作为待审步骤
        Map<String, Integer> waitStepNoByApprovalId = new HashMap<>();
        for (TeamApprovalStep step : waitSteps) {
            waitStepNoByApprovalId.merge(step.getApprovalId(), step.getStepNo(), Math::min);
        }
        return candidates.stream()
                .filter(approval -> approval.getCurrentStep() != null
                        && approval.getCurrentStep().equals(waitStepNoByApprovalId.get(approval.getId())))
                .sorted(Comparator.comparing(TeamApproval::getCreateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    private ApprovalTodoMatcher() {
    }
}
