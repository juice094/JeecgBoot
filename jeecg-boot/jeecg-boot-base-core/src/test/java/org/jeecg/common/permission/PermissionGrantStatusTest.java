package org.jeecg.common.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * @Description: PermissionGrantStatus 状态迁移矩阵测试
 * @Author: juice094
 * @Date: 2026-11-19
 * @Version: V1.0
 */
class PermissionGrantStatusTest {

    @Test
    void legalTransitions() {
        assertTrue(PermissionGrantStatus.DRAFT.canTransitTo(PermissionGrantStatus.PENDING));
        assertTrue(PermissionGrantStatus.DRAFT.canTransitTo(PermissionGrantStatus.CANCELED));
        assertTrue(PermissionGrantStatus.PENDING.canTransitTo(PermissionGrantStatus.APPROVED));
        assertTrue(PermissionGrantStatus.PENDING.canTransitTo(PermissionGrantStatus.REJECTED));
        assertTrue(PermissionGrantStatus.PENDING.canTransitTo(PermissionGrantStatus.RETURNED));
        assertTrue(PermissionGrantStatus.PENDING.canTransitTo(PermissionGrantStatus.CANCELED));
        assertTrue(PermissionGrantStatus.RETURNED.canTransitTo(PermissionGrantStatus.PENDING));
        assertTrue(PermissionGrantStatus.RETURNED.canTransitTo(PermissionGrantStatus.CANCELED));
        assertTrue(PermissionGrantStatus.APPROVED.canTransitTo(PermissionGrantStatus.ACTIVE));
        assertTrue(PermissionGrantStatus.ACTIVE.canTransitTo(PermissionGrantStatus.EXPIRED));
        assertTrue(PermissionGrantStatus.ACTIVE.canTransitTo(PermissionGrantStatus.REVOKED));
    }

    @Test
    void illegalTransitions() {
        // 生效后不可回退
        assertFalse(PermissionGrantStatus.ACTIVE.canTransitTo(PermissionGrantStatus.PENDING));
        assertFalse(PermissionGrantStatus.ACTIVE.canTransitTo(PermissionGrantStatus.APPROVED));
        // 不可跳过审批直达生效
        assertFalse(PermissionGrantStatus.PENDING.canTransitTo(PermissionGrantStatus.ACTIVE));
        assertFalse(PermissionGrantStatus.DRAFT.canTransitTo(PermissionGrantStatus.ACTIVE));
        // 已拒绝不可重提（需重新申请）
        assertFalse(PermissionGrantStatus.REJECTED.canTransitTo(PermissionGrantStatus.PENDING));
        // null 安全
        assertFalse(PermissionGrantStatus.PENDING.canTransitTo(null));
    }

    @Test
    void terminalStates() {
        assertTrue(PermissionGrantStatus.REJECTED.isTerminal());
        assertTrue(PermissionGrantStatus.CANCELED.isTerminal());
        assertTrue(PermissionGrantStatus.EXPIRED.isTerminal());
        assertTrue(PermissionGrantStatus.REVOKED.isTerminal());
        assertFalse(PermissionGrantStatus.ACTIVE.isTerminal());
        assertFalse(PermissionGrantStatus.PENDING.isTerminal());
        // 终态不可再流转
        for (PermissionGrantStatus status : PermissionGrantStatus.values()) {
            if (status.isTerminal()) {
                assertFalse(status.canTransitTo(PermissionGrantStatus.ACTIVE));
            }
        }
    }

    @Test
    void onlyActiveVisibleToAuthorizer() {
        for (PermissionGrantStatus status : PermissionGrantStatus.values()) {
            assertEquals(status == PermissionGrantStatus.ACTIVE, status.isVisibleToAuthorizer(),
                    "鉴权可见性仅 ACTIVE: " + status.getCode());
        }
    }

    @Test
    void fromCodeSafe() {
        assertEquals(PermissionGrantStatus.ACTIVE, PermissionGrantStatus.fromCode("active"));
        assertEquals(PermissionGrantStatus.PENDING, PermissionGrantStatus.fromCode("PENDING"));
        assertNull(PermissionGrantStatus.fromCode("unknown"));
        assertNull(PermissionGrantStatus.fromCode(null));
    }
}
