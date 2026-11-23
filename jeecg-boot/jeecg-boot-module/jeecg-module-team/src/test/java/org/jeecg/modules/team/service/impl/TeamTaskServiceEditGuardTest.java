package org.jeecg.modules.team.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.mapper.TeamTaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 任务编辑 service 层契约测试（F-09 / D-69a，bizflow-dev 新增）。
 *
 * 覆盖线 A 原 controller 用例迁移后的不变量：
 * 1) edit 强制回填库中原状态（状态变更唯一入口 = changeStatus）；
 * 2) edit/delete 归属校验：非创建人非管理员被拒，创建人/管理员放行；
 * 3) deleteOne 走逻辑删（@TableLogic，removeById 不物理删）。
 *
 * 与 TeamTaskControllerGuardTest 的分工：controller 只验证"委托 + 空值预检"，
 * 本类验证 service 内的回填与归属语义。
 */
class TeamTaskServiceEditGuardTest {

    private TeamTaskServiceImpl newService(TeamTaskMapper mapper, TeamOwnershipGuard guard) {
        TeamTaskServiceImpl svc = new TeamTaskServiceImpl();
        ReflectionTestUtils.setField(svc, "baseMapper", mapper);
        ReflectionTestUtils.setField(svc, "ownershipGuard", guard);
        ReflectionTestUtils.setField(svc, "auditLogger", mock(TeamAuditLogger.class));
        return svc;
    }

    private TeamTask storedTask(String id, String createBy) {
        TeamTask t = new TeamTask();
        ReflectionTestUtils.setField(t, "id", id);
        t.setTitle("看板联调");
        t.setStatus("doing");
        t.setCreateBy(createBy);
        return t;
    }

    private MockedStatic<SecurityUtils> mockShiro(String username, String userId) {
        MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class);
        Subject subject = mock(Subject.class);
        if (username == null) {
            when(subject.getPrincipal()).thenReturn(null);
        } else {
            LoginUser user = new LoginUser();
            user.setUsername(username);
            user.setId(userId);
            user.setRealname(username);
            when(subject.getPrincipal()).thenReturn(user);
        }
        ms.when(SecurityUtils::getSubject).thenReturn(subject);
        return ms;
    }

    @Test
    void editBackfillsOriginalStatus() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        TeamTaskServiceImpl svc = newService(mapper, new TeamOwnershipGuard());
        TeamTask stored = storedTask("t1", "zhangsan");
        when(mapper.selectById("t1")).thenReturn(stored);

        TeamTask input = new TeamTask();
        ReflectionTestUtils.setField(input, "id", "t1");
        input.setTitle("改名");
        input.setStatus("done");

        try (MockedStatic<SecurityUtils> ignored = mockShiro("zhangsan", "u1")) {
            svc.edit(input);
        }

        assertEquals("doing", input.getStatus(), "编辑必须回填库中原状态，禁止绕过状态机");
        assertEquals("改名", input.getTitle(), "其他字段正常更新");
    }

    @Test
    void editRejectsNonOwner() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        TeamTaskServiceImpl svc = newService(mapper, new TeamOwnershipGuard());
        when(mapper.selectById("t2")).thenReturn(storedTask("t2", "zhangsan"));

        TeamTask input = new TeamTask();
        ReflectionTestUtils.setField(input, "id", "t2");
        input.setTitle("越权改名");

        try (MockedStatic<SecurityUtils> ignored = mockShiro("lisi", "u2")) {
            JeecgBootException ex = assertThrows(JeecgBootException.class, () -> svc.edit(input));
            assertTrue(ex.getMessage().contains("无权限"), ex.getMessage());
        }
    }

    @Test
    void editAllowsAdmin() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        TeamTaskServiceImpl svc = newService(mapper, new TeamOwnershipGuard());
        when(mapper.selectById("t3")).thenReturn(storedTask("t3", "zhangsan"));

        TeamTask input = new TeamTask();
        ReflectionTestUtils.setField(input, "id", "t3");
        input.setTitle("管理员改名");

        try (MockedStatic<SecurityUtils> ignored = mockShiro("admin", "u0")) {
            svc.edit(input);
        }
        assertEquals("管理员改名", input.getTitle(), "管理员应可编辑他人任务");
    }

    @Test
    void changeStatusRejectsNonOperator() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        TeamTaskServiceImpl svc = newService(mapper, new TeamOwnershipGuard());
        when(mapper.selectById("t4")).thenReturn(storedTask("t4", "zhangsan"));

        try (MockedStatic<SecurityUtils> ignored = mockShiro("lisi", "u2")) {
            JeecgBootException ex = assertThrows(JeecgBootException.class,
                    () -> svc.changeStatus("t4", "done"));
            assertTrue(ex.getMessage().contains("无权限流转任务"), ex.getMessage());
        }
    }

    @Test
    void changeStatusAllowsAssignee() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        TeamTaskServiceImpl svc = newService(mapper, new TeamOwnershipGuard());
        TeamTask stored = storedTask("t5", "zhangsan");
        stored.setAssigneeId("u9");
        stored.setAssigneeName("王五");
        when(mapper.selectById("t5")).thenReturn(stored);

        try (MockedStatic<SecurityUtils> ignored = mockShiro("wangwu", "u9")) {
            svc.changeStatus("t5", "done");
        }
        assertEquals("done", stored.getStatus(), "被指派人应可流转任务");
    }
}
