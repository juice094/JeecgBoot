package org.jeecg.modules.team.enums;

/**
 * @Description: TeamApprovalStatus 状态迁移矩阵纯 Java 自测（无 Spring 依赖）
 * @Author: juice094
 * @Date: 2026-11-18
 * @Version: V1.0
 * <p>
 * 无 JUnit 依赖（模块 pom 未引入），main 方法直接跑；失败抛 AssertionError。
 * 覆盖：合法迁移、非法迁移、终态判定、fromCode 解析、null 安全。
 */
public final class TeamApprovalStatusSelfTest {

    public static void main(String[] args) {
        legalTransitions();
        illegalTransitions();
        terminalStates();
        fromCodeSafe();
        System.out.println("TeamApprovalStatusSelfTest OK");
    }

    private static void legalTransitions() {
        assertTransit(TeamApprovalStatus.DRAFT, TeamApprovalStatus.PENDING);
        assertTransit(TeamApprovalStatus.PENDING, TeamApprovalStatus.APPROVED);
        assertTransit(TeamApprovalStatus.PENDING, TeamApprovalStatus.REJECTED);
        assertTransit(TeamApprovalStatus.PENDING, TeamApprovalStatus.RETURNED);
        assertTransit(TeamApprovalStatus.PENDING, TeamApprovalStatus.CANCELED);
        assertTransit(TeamApprovalStatus.RETURNED, TeamApprovalStatus.PENDING);
        assertTransit(TeamApprovalStatus.RETURNED, TeamApprovalStatus.CANCELED);
    }

    private static void illegalTransitions() {
        assertNotTransit(TeamApprovalStatus.APPROVED, TeamApprovalStatus.PENDING);
        assertNotTransit(TeamApprovalStatus.REJECTED, TeamApprovalStatus.PENDING);
        assertNotTransit(TeamApprovalStatus.CANCELED, TeamApprovalStatus.PENDING);
        assertNotTransit(TeamApprovalStatus.PENDING, TeamApprovalStatus.DRAFT);
        assertNotTransit(TeamApprovalStatus.DRAFT, TeamApprovalStatus.APPROVED);
        assertNotTransit(TeamApprovalStatus.PENDING, null);
    }

    private static void terminalStates() {
        if (!TeamApprovalStatus.APPROVED.isTerminal()) {
            throw new AssertionError("APPROVED 应为终态");
        }
        if (!TeamApprovalStatus.REJECTED.isTerminal()) {
            throw new AssertionError("REJECTED 应为终态");
        }
        if (!TeamApprovalStatus.CANCELED.isTerminal()) {
            throw new AssertionError("CANCELED 应为终态");
        }
        if (TeamApprovalStatus.PENDING.isTerminal()) {
            throw new AssertionError("PENDING 不应为终态");
        }
        if (TeamApprovalStatus.RETURNED.isTerminal()) {
            throw new AssertionError("RETURNED 不应为终态");
        }
    }

    private static void fromCodeSafe() {
        if (TeamApprovalStatus.fromCode("pending") != TeamApprovalStatus.PENDING) {
            throw new AssertionError("fromCode(pending) 失败");
        }
        if (TeamApprovalStatus.fromCode(" PENDING ") != TeamApprovalStatus.PENDING) {
            throw new AssertionError("fromCode 应容忍空白");
        }
        if (TeamApprovalStatus.fromCode("unknown") != null) {
            throw new AssertionError("fromCode 非法值应返回 null");
        }
        if (TeamApprovalStatus.fromCode(null) != null) {
            throw new AssertionError("fromCode null 应返回 null");
        }
    }

    private static void assertTransit(TeamApprovalStatus from, TeamApprovalStatus to) {
        if (!from.canTransitTo(to)) {
            throw new AssertionError("应允许迁移: " + from + " → " + to);
        }
    }

    private static void assertNotTransit(TeamApprovalStatus from, TeamApprovalStatus to) {
        if (from.canTransitTo(to)) {
            throw new AssertionError("不应允许迁移: " + from + " → " + to);
        }
    }

    private TeamApprovalStatusSelfTest() {
    }
}
