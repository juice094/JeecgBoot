package org.jeecg.modules.team.service.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.jeecg.modules.team.entity.TeamApproval;
import org.jeecg.modules.team.entity.TeamApprovalStep;

/**
 * 纯 Java 自测（无 JUnit 依赖前提下的最小可运行检查）：
 * ApprovalTodoMatcher 两步走匹配逻辑——current_step 匹配、stepNo 取最小、倒序排序。
 *
 * 运行方式：mvn test-compile 后
 *   java -ea -cp <module>/target/classes;<module>/target/test-classes org.jeecg.modules.team.service.impl.ApprovalTodoMatcherSelfTest
 */
public final class ApprovalTodoMatcherSelfTest {

    private static int checked = 0;

    public static void main(String[] args) {
        // a1 当前步骤=1 有 wait 步骤 → 命中；a2 当前步骤=1 但 wait 步骤是第2步 → 不命中
        // a3 命中；a1 的异常重复 wait 步骤（第2步）不干扰（取最小 stepNo=1，仍命中）
        TeamApprovalStep s1 = step("a1", 1);
        TeamApprovalStep s1Anomaly = step("a1", 2);
        TeamApprovalStep s2 = step("a2", 2);
        TeamApprovalStep s3 = step("a3", 1);

        TeamApproval a1 = approval("a1", "pending", 1, 1_000L);
        TeamApproval a2 = approval("a2", "pending", 1, 3_000L);
        TeamApproval a3 = approval("a3", "pending", 1, 2_000L);

        List<TeamApproval> matched = ApprovalTodoMatcher.match(
                Arrays.asList(s1, s1Anomaly, s2, s3), Arrays.asList(a1, a2, a3));

        assertTrue(matched.size() == 2, "应命中 2 单，实际 " + matched.size());
        assertTrue(matched.get(0).getId().equals("a3"), "倒序第1应为 a3(创建晚于a1)，实际 " + matched.get(0).getId());
        assertTrue(matched.get(1).getId().equals("a1"), "倒序第2应为 a1，实际 " + matched.get(1).getId());

        // 边界：空入参
        assertTrue(ApprovalTodoMatcher.match(null, Arrays.asList(a1)).isEmpty(), "空步骤应返回空");
        assertTrue(ApprovalTodoMatcher.match(Arrays.asList(s1), null).isEmpty(), "空单据应返回空");
        assertTrue(ApprovalTodoMatcher.match(Collections.emptyList(), Collections.emptyList()).isEmpty(),
                "全空应返回空");

        // current_step 为 null 的单据不命中、不抛异常
        TeamApproval nullStep = approval("a4", "pending", null, 9_000L);
        assertTrue(ApprovalTodoMatcher.match(Arrays.asList(step("a4", 1)), Arrays.asList(nullStep)).isEmpty(),
                "current_step 为 null 不应命中");

        System.out.println("ApprovalTodoMatcherSelfTest PASSED, assertions=" + checked);
    }

    private static TeamApprovalStep step(String approvalId, int stepNo) {
        TeamApprovalStep step = new TeamApprovalStep();
        step.setApprovalId(approvalId);
        step.setStepNo(stepNo);
        return step;
    }

    private static TeamApproval approval(String id, String status, Integer currentStep, long createTimeMillis) {
        TeamApproval approval = new TeamApproval();
        approval.setId(id);
        approval.setStatus(status);
        approval.setCurrentStep(currentStep);
        approval.setCreateTime(new Date(createTimeMillis));
        return approval;
    }

    private static void assertTrue(boolean condition, String message) {
        checked++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private ApprovalTodoMatcherSelfTest() {
    }
}
