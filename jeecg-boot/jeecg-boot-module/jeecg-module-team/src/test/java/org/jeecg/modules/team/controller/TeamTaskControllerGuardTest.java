package org.jeecg.modules.team.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.service.ITeamTaskService;
import org.jeecg.modules.team.service.impl.TeamAuditLogger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 任务控制器“状态只能走 /status”契约测试（Mockito，无 Spring MVC）。
 *
 * 覆盖本次修复：
 * 1) add 强制 status=todo（客户端传 done 也无效），防建单即完成绕过状态机；
 * 2) edit 整体委托 service.edit（F-09 后归属校验与状态回填在 service 层，见
 *    TeamTaskServiceEditGuardTest），controller 只做空值预检；
 * 3) 空标题仍拒绝。
 * 2026-11-17 适配记录：editCannotChangeStatus 改为 editDelegatesToServiceWithWholePayload
 *（F-09 契约变更，原"状态回填在 controller"的假设不再成立）。
 *
 * 层级说明：验证 controller 方法逻辑，不覆盖 HTTP 序列化/鉴权拦截器。
 * id 用 ReflectionTestUtils 写父类字段，规避 Lombok 继承 setter 在测试编译期的解析问题。
 */
class TeamTaskControllerGuardTest {

    private TeamTaskController newController(ITeamTaskService svc) {
        TeamTaskController c = new TeamTaskController();
        ReflectionTestUtils.setField(c, "teamTaskService", svc);
        // M3 起 add() 在建单后写审计时间线（controller 持有 auditLogger），未注入即 NPE
        ReflectionTestUtils.setField(c, "auditLogger", mock(TeamAuditLogger.class));
        return c;
    }

    private MockedStatic<SecurityUtils> mockShiroLoggedOut() {
        MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class);
        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(null);
        ms.when(SecurityUtils::getSubject).thenReturn(subject);
        return ms;
    }

    @Test
    void addForcesTodoEvenIfClientSendsDone() {
        ITeamTaskService svc = mock(ITeamTaskService.class);
        TeamTaskController c = newController(svc);
        TeamTask input = new TeamTask();
        input.setTitle("看板联调");
        input.setStatus("done");

        try (MockedStatic<SecurityUtils> ignored = mockShiroLoggedOut()) {
            Result<String> r = c.add(input);
            assertTrue(r.isSuccess(), "正常新增应成功");
        }

        ArgumentCaptor<TeamTask> cap = ArgumentCaptor.forClass(TeamTask.class);
        verify(svc).save(cap.capture());
        assertEquals("todo", cap.getValue().getStatus(), "新增任务状态必须强制为 todo");
        assertEquals("中", cap.getValue().getPriority(), "优先级缺省应为中");
    }

    @Test
    void addRejectsBlankTitle() {
        ITeamTaskService svc = mock(ITeamTaskService.class);
        TeamTaskController c = newController(svc);
        TeamTask input = new TeamTask();
        input.setTitle("  ");

        try (MockedStatic<SecurityUtils> ignored = mockShiroLoggedOut()) {
            Result<String> r = c.add(input);
            assertFalse(r.isSuccess(), "空标题应返回错误");
        }
        verify(svc, never()).save(any());
    }

    /**
     * 编辑请求必须整体委托 service.edit（F-09 后归属校验与状态回填都在 service 层，
     * controller 只做空值预检）；状态不得直改的断言移至 TeamTaskServiceEditGuardTest。
     */
    @Test
    void editDelegatesToServiceWithWholePayload() {
        ITeamTaskService svc = mock(ITeamTaskService.class);
        TeamTaskController c = newController(svc);
        TeamTask stored = new TeamTask();
        ReflectionTestUtils.setField(stored, "id", "t1");
        stored.setTitle("看板联调");
        stored.setStatus("doing");
        when(svc.getById("t1")).thenReturn(stored);

        TeamTask input = new TeamTask();
        ReflectionTestUtils.setField(input, "id", "t1");
        input.setTitle("看板联调-改名");
        input.setStatus("done");

        try (MockedStatic<SecurityUtils> ignored = mockShiroLoggedOut()) {
            Result<String> r = c.edit(input);
            assertTrue(r.isSuccess());
        }

        ArgumentCaptor<TeamTask> cap = ArgumentCaptor.forClass(TeamTask.class);
        verify(svc).edit(cap.capture());
        assertEquals("看板联调-改名", cap.getValue().getTitle(), "其他字段应原样透传给 service");
        assertEquals("t1", cap.getValue().getId(), "主键应透传");
    }

    @Test
    void editRejectsMissingRecord() {
        ITeamTaskService svc = mock(ITeamTaskService.class);
        TeamTaskController c = newController(svc);
        when(svc.getById("nope")).thenReturn(null);
        TeamTask input = new TeamTask();
        ReflectionTestUtils.setField(input, "id", "nope");

        try (MockedStatic<SecurityUtils> ignored = mockShiroLoggedOut()) {
            Result<String> r = c.edit(input);
            assertFalse(r.isSuccess());
        }
        verify(svc, never()).updateById(any());
    }
}
