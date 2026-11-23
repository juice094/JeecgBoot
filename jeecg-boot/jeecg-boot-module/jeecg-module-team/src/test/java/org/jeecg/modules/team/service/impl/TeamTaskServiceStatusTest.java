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
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.enums.TeamAuditAction;
import org.jeecg.modules.team.enums.TeamAuditBizType;
import org.jeecg.modules.team.mapper.TeamTaskMapper;
import org.jeecg.modules.team.service.ITeamTaskDependencyService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务状态流转单元测试（Mockito，无 Spring 上下文）。
 *
 * 覆盖：合法流转更新状态并写审计；自流转/非法目标码拒绝且不写审计；
 * changeStatus 具备 @Transactional(rollbackFor=Exception) 契约（保证状态与审计同事务）。
 *
 * 说明：id 用 ReflectionTestUtils 直接写父类字段，规避 Lombok 继承 setter 在测试编译期的解析问题；
 * 不 verify BaseMapper.updateById（MyBatis-Plus 3.5.16 存在 T/Collection<T> 双重载，Mockito verify 会产生歧义），
 * 以“状态是否被改写”间接断言未落库。
 */
class TeamTaskServiceStatusTest {

    private TeamTaskServiceImpl newService(TeamTaskMapper mapper, TeamAuditLogger logger) {
        TeamTaskServiceImpl svc = new TeamTaskServiceImpl();
        ReflectionTestUtils.setField(svc, "baseMapper", mapper);
        ReflectionTestUtils.setField(svc, "auditLogger", logger);
        // P1 依赖线引入：changeStatus 会调用依赖服务（doing 前置校验 + 依赖方 blocked 重算）
        ReflectionTestUtils.setField(svc, "dependencyService", mock(ITeamTaskDependencyService.class));
        return svc;
    }

    private TeamTask task(String id, String status) {
        TeamTask t = new TeamTask();
        ReflectionTestUtils.setField(t, "id", id);
        t.setTitle("看板联调");
        t.setStatus(status);
        return t;
    }

    private MockedStatic<SecurityUtils> mockShiroLoggedOut() {
        MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class);
        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(null);
        ms.when(SecurityUtils::getSubject).thenReturn(subject);
        return ms;
    }

    @Test
    void legalTransitUpdatesStatusAndWritesAudit() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        TeamAuditLogger logger = mock(TeamAuditLogger.class);
        TeamTaskServiceImpl svc = newService(mapper, logger);
        TeamTask t = task("t1", "todo");
        when(mapper.selectById("t1")).thenReturn(t);

        try (MockedStatic<SecurityUtils> ignored = mockShiroLoggedOut()) {
            svc.changeStatus("t1", "doing");
        }

        assertEquals("doing", t.getStatus(), "状态应更新为目标值");
        verify(logger).log(TeamAuditBizType.TASK, "t1", "看板联调",
                TeamAuditAction.CHANGE_STATUS, "todo", "doing", null);
    }

    @Test
    void selfTransitRejectedWithoutAudit() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        TeamAuditLogger logger = mock(TeamAuditLogger.class);
        TeamTaskServiceImpl svc = newService(mapper, logger);
        TeamTask t = task("t2", "todo");
        when(mapper.selectById("t2")).thenReturn(t);

        try (MockedStatic<SecurityUtils> ignored = mockShiroLoggedOut()) {
            JeecgBootException ex = assertThrows(JeecgBootException.class,
                    () -> svc.changeStatus("t2", "todo"));
            assertTrue(ex.getMessage().contains("非法状态流转"), ex.getMessage());
            assertTrue(ex.getMessage().contains("todo → todo"), ex.getMessage());
        }

        assertEquals("todo", t.getStatus(), "非法流转不应改写状态");
        verify(logger, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void illegalTargetCodeRejected() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        TeamAuditLogger logger = mock(TeamAuditLogger.class);
        TeamTaskServiceImpl svc = newService(mapper, logger);
        TeamTask t = task("t3", "todo");
        when(mapper.selectById("t3")).thenReturn(t);

        try (MockedStatic<SecurityUtils> ignored = mockShiroLoggedOut()) {
            JeecgBootException ex = assertThrows(JeecgBootException.class,
                    () -> svc.changeStatus("t3", "junk"));
            assertTrue(ex.getMessage().contains("非法的目标状态"), ex.getMessage());
        }
        assertEquals("todo", t.getStatus());
    }

    @Test
    void missingTaskRejected() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        TeamTaskServiceImpl svc = newService(mapper, mock(TeamAuditLogger.class));
        when(mapper.selectById("nope")).thenReturn(null);

        try (MockedStatic<SecurityUtils> ignored = mockShiroLoggedOut()) {
            JeecgBootException ex = assertThrows(JeecgBootException.class,
                    () -> svc.changeStatus("nope", "doing"));
            assertTrue(ex.getMessage().contains("任务不存在"), ex.getMessage());
        }
    }

    @Test
    void changeStatusIsTransactionalWithRollbackForException() throws Exception {
        Transactional tx = TeamTaskServiceImpl.class
                .getMethod("changeStatus", String.class, String.class)
                .getAnnotation(Transactional.class);
        assertNotNull(tx, "changeStatus 必须标注 @Transactional，保证状态与审计同事务");
        boolean rollbackException = false;
        for (Class<? extends Throwable> c : tx.rollbackFor()) {
            if (c == Exception.class) {
                rollbackException = true;
                break;
            }
        }
        assertTrue(rollbackException, "@Transactional 必须声明 rollbackFor = Exception.class");
    }
}
