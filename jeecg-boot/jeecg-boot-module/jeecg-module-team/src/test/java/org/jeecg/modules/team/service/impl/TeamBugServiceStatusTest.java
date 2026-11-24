package org.jeecg.modules.team.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.team.entity.TeamBug;
import org.jeecg.modules.team.enums.TeamAuditAction;
import org.jeecg.modules.team.enums.TeamAuditBizType;
import org.jeecg.modules.team.mapper.TeamBugMapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bug 状态流转单元测试（Mockito，无 Spring 上下文）。
 *
 * 覆盖需求 §9 验收第 4 条：open→resolved 必须被拒（须先 processing）；closed 后任何流转被拒。
 * 同时断言 changeStatus 的 @Transactional(rollbackFor=Exception) 契约。
 * id 用反射写父类字段；不 verify BaseMapper.updateById（3.5.16 双重载歧义）。
 */
class TeamBugServiceStatusTest {

    private TeamBugServiceImpl newService(TeamBugMapper mapper, TeamAuditLogger logger) {
        TeamBugServiceImpl svc = new TeamBugServiceImpl();
        ReflectionTestUtils.setField(svc, "baseMapper", mapper);
        ReflectionTestUtils.setField(svc, "auditLogger", logger);
        return svc;
    }

    private TeamBug bug(String id, String status) {
        TeamBug b = new TeamBug();
        ReflectionTestUtils.setField(b, "id", id);
        b.setDescription("按钮点击无响应");
        b.setStatus(status);
        return b;
    }

    private MockedStatic<SecurityUtils> mockShiroLoggedOut() {
        MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class);
        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(null);
        ms.when(SecurityUtils::getSubject).thenReturn(subject);
        return ms;
    }

    @Test
    void openToProcessingLegalAndAudited() {
        TeamBugMapper mapper = mock(TeamBugMapper.class);
        TeamAuditLogger logger = mock(TeamAuditLogger.class);
        TeamBugServiceImpl svc = newService(mapper, logger);
        TeamBug b = bug("b1", "open");
        when(mapper.selectById("b1")).thenReturn(b);

        try (MockedStatic<SecurityUtils> ignored = mockShiroLoggedOut()) {
            svc.changeStatus("b1", "processing");
        }

        assertEquals("processing", b.getStatus());
        verify(logger).log(TeamAuditBizType.BUG, "b1", "按钮点击无响应",
                TeamAuditAction.CHANGE_STATUS, "open", "processing", null);
    }

    @Test
    void openToResolvedRejected() {
        TeamBugMapper mapper = mock(TeamBugMapper.class);
        TeamAuditLogger logger = mock(TeamAuditLogger.class);
        TeamBugServiceImpl svc = newService(mapper, logger);
        TeamBug b = bug("b2", "open");
        when(mapper.selectById("b2")).thenReturn(b);

        try (MockedStatic<SecurityUtils> ignored = mockShiroLoggedOut()) {
            JeecgBootException ex = assertThrows(JeecgBootException.class,
                    () -> svc.changeStatus("b2", "resolved"));
            assertTrue(ex.getMessage().contains("非法状态流转"), ex.getMessage());
            assertTrue(ex.getMessage().contains("open → resolved"), ex.getMessage());
        }
        assertEquals("open", b.getStatus(), "非法流转不应改写状态");
        verify(logger, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void closedIsTerminal() {
        TeamBugMapper mapper = mock(TeamBugMapper.class);
        TeamBugServiceImpl svc = newService(mapper, mock(TeamAuditLogger.class));
        TeamBug b = bug("b3", "closed");
        when(mapper.selectById("b3")).thenReturn(b);

        try (MockedStatic<SecurityUtils> ignored = mockShiroLoggedOut()) {
            JeecgBootException ex = assertThrows(JeecgBootException.class,
                    () -> svc.changeStatus("b3", "open"));
            assertTrue(ex.getMessage().contains("closed → open"), ex.getMessage());
        }
        assertEquals("closed", b.getStatus());
    }

    @Test
    void changeStatusIsTransactionalWithRollbackForException() throws Exception {
        Transactional tx = TeamBugServiceImpl.class
                .getMethod("changeStatus", String.class, String.class)
                .getAnnotation(Transactional.class);
        assertNotNull(tx, "changeStatus 必须标注 @Transactional");
        boolean rollbackException = false;
        for (Class<? extends Throwable> c : tx.rollbackFor()) {
            if (c == Exception.class) {
                rollbackException = true;
                break;
            }
        }
        assertTrue(rollbackException, "@Transactional 必须声明 rollbackFor = Exception.class");
    }

    @Test
    void nextBugNoUsesGlobalMaxIncludingLogicallyDeleted() {
        TeamBugMapper mapper = mock(TeamBugMapper.class);
        TeamBugServiceImpl svc = newService(mapper, mock(TeamAuditLogger.class));

        when(mapper.selectMaxBugNo()).thenReturn(7);
        assertEquals(8, svc.nextBugNo(), "取全表最大序号 + 1（含逻辑删行）");

        when(mapper.selectMaxBugNo()).thenReturn(null);
        assertEquals(1, svc.nextBugNo(), "空表从 1 开始");
    }
}
