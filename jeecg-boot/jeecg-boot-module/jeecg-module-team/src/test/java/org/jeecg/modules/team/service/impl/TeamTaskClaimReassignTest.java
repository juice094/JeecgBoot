package org.jeecg.modules.team.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.enums.TeamAuditAction;
import org.jeecg.modules.team.enums.TeamAuditBizType;
import org.jeecg.modules.team.mapper.TeamTaskMapper;
import org.jeecg.modules.team.service.ITeamTaskDependencyService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * claim/reassign + doing 依赖拦截单元测试（Mockito，无 Spring 上下文）。
 *
 * 覆盖：
 * 1) claim：指派人=登录人本人，写审计 CLAIM（from=原指派人，to=本人）；
 * 2) reassign 正路径：创建人改派成功，写审计 REASSIGN；
 * 3) reassign 负路径：非创建人非管理员被 TeamOwnershipGuard 拒绝，指派人不变；
 * 4) changeStatus 进入 doing 前存在未完成依赖 → 拒绝「存在未完成依赖」，状态不改、审计不写。
 */
class TeamTaskClaimReassignTest {

    private LoginUser loginUser(String id, String username, String realname) {
        LoginUser user = new LoginUser();
        user.setId(id);
        user.setUsername(username);
        user.setRealname(realname);
        return user;
    }

    private MockedStatic<SecurityUtils> mockShiro(LoginUser principal) {
        MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class);
        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(principal);
        ms.when(SecurityUtils::getSubject).thenReturn(subject);
        return ms;
    }

    private TeamTaskServiceImpl newService(TeamTaskMapper mapper, TeamAuditLogger logger,
            TeamOwnershipGuard guard, ITeamTaskDependencyService dependencyService) {
        TeamTaskServiceImpl svc = new TeamTaskServiceImpl();
        ReflectionTestUtils.setField(svc, "baseMapper", mapper);
        ReflectionTestUtils.setField(svc, "auditLogger", logger);
        ReflectionTestUtils.setField(svc, "ownershipGuard", guard);
        ReflectionTestUtils.setField(svc, "dependencyService", dependencyService);
        return svc;
    }

    private TeamTask storedTask(String id, String status, String createBy,
            String assigneeId, String assigneeName) {
        TeamTask t = new TeamTask();
        ReflectionTestUtils.setField(t, "id", id);
        t.setTitle("P1 任务");
        t.setStatus(status);
        t.setCreateBy(createBy);
        t.setAssigneeId(assigneeId);
        t.setAssigneeName(assigneeName);
        return t;
    }

    @Test
    void claimAssignsToSelfAndWritesClaimAudit() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        TeamAuditLogger logger = mock(TeamAuditLogger.class);
        TeamTask t = storedTask("t1", "todo", "boss", null, null);
        when(mapper.selectById("t1")).thenReturn(t);

        TeamTaskServiceImpl svc = newService(mapper, logger, new TeamOwnershipGuard(),
                mock(ITeamTaskDependencyService.class));
        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u9", "zhangsan", "张三"))) {
            svc.claim("t1");
        }

        assertEquals("u9", t.getAssigneeId(), "认领后指派人=本人");
        assertEquals("张三", t.getAssigneeName());
        assertEquals("zhangsan", t.getUpdateBy());
        verify(logger).log(TeamAuditBizType.TASK, "t1", "P1 任务",
                TeamAuditAction.CLAIM, null, "张三", null);
    }

    @Test
    void claimRequiresLoginContext() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        TeamTask t = storedTask("t1", "todo", "boss", null, null);
        when(mapper.selectById("t1")).thenReturn(t);
        TeamTaskServiceImpl svc = newService(mapper, mock(TeamAuditLogger.class),
                new TeamOwnershipGuard(), mock(ITeamTaskDependencyService.class));

        try (MockedStatic<SecurityUtils> ignored = mockShiro(null)) {
            JeecgBootException ex = assertThrows(JeecgBootException.class, () -> svc.claim("t1"));
            assertTrue(ex.getMessage().contains("登录"), ex.getMessage());
        }
    }

    @Test
    void reassignByCreatorSucceedsAndWritesAudit() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        TeamAuditLogger logger = mock(TeamAuditLogger.class);
        TeamTask t = storedTask("t1", "todo", "zhangsan", "u8", "旧人");
        when(mapper.selectById("t1")).thenReturn(t);

        TeamTaskServiceImpl svc = newService(mapper, logger, new TeamOwnershipGuard(),
                mock(ITeamTaskDependencyService.class));
        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u1", "zhangsan", "张三"))) {
            svc.reassign("t1", "u7", "李四");
        }

        assertEquals("u7", t.getAssigneeId());
        assertEquals("李四", t.getAssigneeName());
        verify(logger).log(TeamAuditBizType.TASK, "t1", "P1 任务",
                TeamAuditAction.REASSIGN, "旧人", "李四", null);
    }

    @Test
    void reassignByNonCreatorRejected() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        TeamAuditLogger logger = mock(TeamAuditLogger.class);
        TeamTask t = storedTask("t1", "todo", "boss", "u8", "旧人");
        when(mapper.selectById("t1")).thenReturn(t);

        TeamTaskServiceImpl svc = newService(mapper, logger, new TeamOwnershipGuard(),
                mock(ITeamTaskDependencyService.class));
        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u1", "zhangsan", "张三"))) {
            JeecgBootException ex = assertThrows(JeecgBootException.class,
                    () -> svc.reassign("t1", "u7", "李四"));
            assertTrue(ex.getMessage().contains("无权限"), ex.getMessage());
        }

        assertEquals("u8", t.getAssigneeId(), "被拒后指派人不变");
        assertEquals("旧人", t.getAssigneeName());
        verify(logger, never()).log(any(), anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void reassignRejectsBlankAssignee() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        TeamTask t = storedTask("t1", "todo", "zhangsan", null, null);
        when(mapper.selectById("t1")).thenReturn(t);
        TeamTaskServiceImpl svc = newService(mapper, mock(TeamAuditLogger.class),
                new TeamOwnershipGuard(), mock(ITeamTaskDependencyService.class));
        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u1", "zhangsan", "张三"))) {
            JeecgBootException ex = assertThrows(JeecgBootException.class,
                    () -> svc.reassign("t1", " ", "李四"));
            assertTrue(ex.getMessage().contains("不能为空"), ex.getMessage());
        }
    }

    @Test
    void changeStatusToDoingBlockedByUnfinishedDependency() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        TeamAuditLogger logger = mock(TeamAuditLogger.class);
        ITeamTaskDependencyService dependencyService = mock(ITeamTaskDependencyService.class);
        TeamTask t = storedTask("t1", "todo", "boss", "u8", "旧人");
        when(mapper.selectById("t1")).thenReturn(t);
        when(dependencyService.hasUnfinishedDependency("t1")).thenReturn(true);

        TeamTaskServiceImpl svc = newService(mapper, logger, new TeamOwnershipGuard(), dependencyService);
        try (MockedStatic<SecurityUtils> ignored = mockShiro(null)) {
            JeecgBootException ex = assertThrows(JeecgBootException.class,
                    () -> svc.changeStatus("t1", "doing"));
            assertTrue(ex.getMessage().contains("存在未完成依赖"), ex.getMessage());
        }

        assertEquals("todo", t.getStatus(), "拦截后状态不变");
        verify(mapper, never()).updateById(any(TeamTask.class));
        verify(logger, never()).log(any(), anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void changeStatusToDoneStillRefreshesDependents() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        TeamAuditLogger logger = mock(TeamAuditLogger.class);
        ITeamTaskDependencyService dependencyService = mock(ITeamTaskDependencyService.class);
        TeamTask t = storedTask("t1", "doing", "boss", "u8", "旧人");
        when(mapper.selectById("t1")).thenReturn(t);

        TeamTaskServiceImpl svc = newService(mapper, logger, new TeamOwnershipGuard(), dependencyService);
        try (MockedStatic<SecurityUtils> ignored = mockShiro(null)) {
            svc.changeStatus("t1", "done");
        }

        assertEquals("done", t.getStatus());
        verify(dependencyService).refreshDependentsBlocked("t1");
    }
}
