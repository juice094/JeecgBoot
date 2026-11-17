package org.jeecg.modules.mail.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * @Description: MailSendStatus 状态机纯单元测试（零 Spring 依赖）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 锁定口径（需求文档 §4.3 / 接口文档 §3.4）：
 * 合法迁移 0→1、1→2、1→3、3→1、3→4；终态（2 成功 / 4 死信）不允许任何迁出。
 */
class MailSendStatusTest {

    @Test
    void codes_matchContract() {
        assertEquals(0, MailSendStatus.PENDING.getCode());
        assertEquals(1, MailSendStatus.SENDING.getCode());
        assertEquals(2, MailSendStatus.SUCCESS.getCode());
        assertEquals(3, MailSendStatus.FAILED.getCode());
        assertEquals(4, MailSendStatus.DEAD.getCode());
    }

    @Test
    void legalTransitions_allowed() {
        assertTrue(MailSendStatus.PENDING.canTransitTo(MailSendStatus.SENDING));
        assertTrue(MailSendStatus.SENDING.canTransitTo(MailSendStatus.SUCCESS));
        assertTrue(MailSendStatus.SENDING.canTransitTo(MailSendStatus.FAILED));
        assertTrue(MailSendStatus.FAILED.canTransitTo(MailSendStatus.SENDING));
        assertTrue(MailSendStatus.FAILED.canTransitTo(MailSendStatus.DEAD));
    }

    @Test
    void illegalTransitions_rejected() {
        // 待发送：只能进发送中
        assertFalse(MailSendStatus.PENDING.canTransitTo(MailSendStatus.PENDING));
        assertFalse(MailSendStatus.PENDING.canTransitTo(MailSendStatus.SUCCESS));
        assertFalse(MailSendStatus.PENDING.canTransitTo(MailSendStatus.FAILED));
        assertFalse(MailSendStatus.PENDING.canTransitTo(MailSendStatus.DEAD));
        // 发送中：不允许自迁移、回退或跳死信
        assertFalse(MailSendStatus.SENDING.canTransitTo(MailSendStatus.PENDING));
        assertFalse(MailSendStatus.SENDING.canTransitTo(MailSendStatus.SENDING));
        assertFalse(MailSendStatus.SENDING.canTransitTo(MailSendStatus.DEAD));
        // 失败：不允许自迁移或直达成功
        assertFalse(MailSendStatus.FAILED.canTransitTo(MailSendStatus.FAILED));
        assertFalse(MailSendStatus.FAILED.canTransitTo(MailSendStatus.PENDING));
        assertFalse(MailSendStatus.FAILED.canTransitTo(MailSendStatus.SUCCESS));
    }

    @Test
    void terminalStates_noOutgoingTransition() {
        for (MailSendStatus target : MailSendStatus.values()) {
            assertFalse(MailSendStatus.SUCCESS.canTransitTo(target), "SUCCESS 不应允许迁出到 " + target);
            assertFalse(MailSendStatus.DEAD.canTransitTo(target), "DEAD 不应允许迁出到 " + target);
        }
    }

    @Test
    void canTransitTo_nullTarget_rejected() {
        for (MailSendStatus status : MailSendStatus.values()) {
            assertFalse(status.canTransitTo(null));
        }
    }

    @Test
    void fromCode_roundTrip_allValues() {
        for (MailSendStatus status : MailSendStatus.values()) {
            assertEquals(status, MailSendStatus.fromCode(status.getCode()));
        }
    }

    @Test
    void fromCode_nullAndUnknown_returnsNull() {
        assertNull(MailSendStatus.fromCode(null));
        assertNull(MailSendStatus.fromCode(99));
        assertNull(MailSendStatus.fromCode(-1));
    }
}
