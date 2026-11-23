package org.jeecg.modules.team.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.entity.TeamTaskDependency;
import org.jeecg.modules.team.mapper.TeamTaskDependencyMapper;
import org.jeecg.modules.team.service.ITeamTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 任务依赖/阻塞单元测试（Mockito，无 Spring 上下文）。
 *
 * 覆盖：
 * 1) addDependency：自依赖/重复/任务不存在拒绝；成功插入并重算 blocked（被依赖未完成 → blocked=1）；
 * 2) removeDependency：删除后重算解除 blocked；
 * 3) hasUnfinishedDependency：无依赖=false，全完成=false，有未完成=true；
 * 4) refreshDependentsBlocked：被依赖任务完成后逐个重算依赖方。
 *
 * selectList mock 按 wrapper sqlSegment 区分查询语义：含 depends_on_task_id 的是
 * 「查依赖方」（refreshDependentsBlocked），否则是「查 taskId 的依赖列表」。
 */
class TeamTaskDependencyServiceTest {

    private TeamTask taskOf(String id, String status, Integer blocked) {
        TeamTask t = new TeamTask();
        ReflectionTestUtils.setField(t, "id", id);
        t.setTitle("任务-" + id);
        t.setStatus(status);
        t.setBlocked(blocked);
        return t;
    }

    private TeamTaskDependency link(String taskId, String dependsOn) {
        TeamTaskDependency row = new TeamTaskDependency();
        row.setTaskId(taskId);
        row.setDependsOnTaskId(dependsOn);
        return row;
    }

    private TeamTaskDependencyServiceImpl newService(TeamTaskDependencyMapper mapper,
            ITeamTaskService taskService, List<TeamTaskDependency> ownLinks,
            List<TeamTaskDependency> dependentLinks) {
        when(mapper.selectList(any())).thenAnswer(inv -> {
            LambdaQueryWrapper<TeamTaskDependency> w = inv.getArgument(0);
            String segment = w.getSqlSegment();
            return segment != null && segment.contains("depends_on_task_id")
                    ? dependentLinks
                    : ownLinks;
        });
        TeamTaskDependencyServiceImpl svc = new TeamTaskDependencyServiceImpl();
        ReflectionTestUtils.setField(svc, "baseMapper", mapper);
        ReflectionTestUtils.setField(svc, "teamTaskService", taskService);
        return svc;
    }

    @Test
    void addDependencyMarksBlockedWhenDependsOnUnfinished() {
        TeamTaskDependencyMapper mapper = mock(TeamTaskDependencyMapper.class);
        ITeamTaskService taskService = mock(ITeamTaskService.class);
        when(mapper.selectCount(any())).thenReturn(0L);
        when(taskService.getById("t1")).thenReturn(taskOf("t1", "todo", 0));
        when(taskService.getById("t2")).thenReturn(taskOf("t2", "doing", 0));
        // t1 依赖 t2，t2 未完成 → count(ne done) = 1
        when(taskService.count(any())).thenReturn(1L);

        TeamTaskDependencyServiceImpl svc = newService(mapper, taskService,
                Collections.singletonList(link("t1", "t2")), Collections.emptyList());
        svc.addDependency("t1", "t2");

        ArgumentCaptor<TeamTaskDependency> captor = ArgumentCaptor.forClass(TeamTaskDependency.class);
        verify(mapper).insert(captor.capture());
        assertEquals("t1", captor.getValue().getTaskId());
        assertEquals("t2", captor.getValue().getDependsOnTaskId());

        verify(taskService, times(1)).update(any());
    }

    @Test
    void addDependencySkipsUpdateWhenBlockedUnchanged() {
        TeamTaskDependencyMapper mapper = mock(TeamTaskDependencyMapper.class);
        ITeamTaskService taskService = mock(ITeamTaskService.class);
        when(mapper.selectCount(any())).thenReturn(0L);
        // t1 本身已 blocked=1，新增依赖后仍是 1 → 不重复 update
        when(taskService.getById("t1")).thenReturn(taskOf("t1", "todo", 1));
        when(taskService.getById("t2")).thenReturn(taskOf("t2", "doing", 0));
        when(taskService.count(any())).thenReturn(1L);

        TeamTaskDependencyServiceImpl svc = newService(mapper, taskService,
                Collections.singletonList(link("t1", "t2")), Collections.emptyList());
        svc.addDependency("t1", "t2");

        verify(taskService, never()).update(any());
    }

    @Test
    void addDependencyRejectsSelfDependency() {
        TeamTaskDependencyMapper mapper = mock(TeamTaskDependencyMapper.class);
        ITeamTaskService taskService = mock(ITeamTaskService.class);
        TeamTaskDependencyServiceImpl svc = newService(mapper, taskService,
                Collections.emptyList(), Collections.emptyList());

        JeecgBootException ex = assertThrows(JeecgBootException.class,
                () -> svc.addDependency("t1", "t1"));
        assertTrue(ex.getMessage().contains("不能依赖自身"), ex.getMessage());
        verify(mapper, never()).insert(any(TeamTaskDependency.class));
    }

    @Test
    void addDependencyRejectsDuplicate() {
        TeamTaskDependencyMapper mapper = mock(TeamTaskDependencyMapper.class);
        ITeamTaskService taskService = mock(ITeamTaskService.class);
        when(mapper.selectCount(any())).thenReturn(1L);
        when(taskService.getById("t1")).thenReturn(taskOf("t1", "todo", 0));
        when(taskService.getById("t2")).thenReturn(taskOf("t2", "done", 0));
        TeamTaskDependencyServiceImpl svc = newService(mapper, taskService,
                Collections.emptyList(), Collections.emptyList());

        JeecgBootException ex = assertThrows(JeecgBootException.class,
                () -> svc.addDependency("t1", "t2"));
        assertTrue(ex.getMessage().contains("依赖已存在"), ex.getMessage());
        verify(mapper, never()).insert(any(TeamTaskDependency.class));
    }

    @Test
    void addDependencyRejectsMissingTask() {
        TeamTaskDependencyMapper mapper = mock(TeamTaskDependencyMapper.class);
        ITeamTaskService taskService = mock(ITeamTaskService.class);
        when(mapper.selectCount(any())).thenReturn(0L);
        when(taskService.getById(anyString())).thenReturn(null);
        TeamTaskDependencyServiceImpl svc = newService(mapper, taskService,
                Collections.emptyList(), Collections.emptyList());

        JeecgBootException ex = assertThrows(JeecgBootException.class,
                () -> svc.addDependency("t1", "ghost"));
        assertTrue(ex.getMessage().contains("不存在"), ex.getMessage());
        verify(mapper, never()).insert(any(TeamTaskDependency.class));
    }

    @Test
    void removeDependencyRecalculatesAndClearsBlocked() {
        TeamTaskDependencyMapper mapper = mock(TeamTaskDependencyMapper.class);
        ITeamTaskService taskService = mock(ITeamTaskService.class);
        // 删除后无剩余依赖 → 解除 blocked（当前 blocked=1 → 触发 update）
        when(taskService.getById("t1")).thenReturn(taskOf("t1", "todo", 1));
        TeamTaskDependencyServiceImpl svc = newService(mapper, taskService,
                Collections.emptyList(), Collections.emptyList());

        svc.removeDependency("t1", "t2");

        verify(mapper).delete(any(LambdaQueryWrapper.class));
        verify(taskService, times(1)).update(any());
    }

    @Test
    void hasUnfinishedDependencySemantics() {
        TeamTaskDependencyMapper mapper = mock(TeamTaskDependencyMapper.class);
        ITeamTaskService taskService = mock(ITeamTaskService.class);

        // 无依赖 → false
        TeamTaskDependencyServiceImpl noDeps = newService(mapper, taskService,
                Collections.emptyList(), Collections.emptyList());
        assertFalse(noDeps.hasUnfinishedDependency("t1"));

        // 有依赖且存在未完成 → true
        when(taskService.count(any())).thenReturn(1L);
        TeamTaskDependencyServiceImpl unfinished = newService(mock(TeamTaskDependencyMapper.class), taskService,
                Collections.singletonList(link("t1", "t2")), Collections.emptyList());
        assertTrue(unfinished.hasUnfinishedDependency("t1"));

        // 有依赖但全部完成 → false
        when(taskService.count(any())).thenReturn(0L);
        TeamTaskDependencyServiceImpl allDone = newService(mock(TeamTaskDependencyMapper.class), taskService,
                Collections.singletonList(link("t1", "t2")), Collections.emptyList());
        assertFalse(allDone.hasUnfinishedDependency("t1"));
    }

    @Test
    void refreshDependentsBlockedRecomputesEachDependent() {
        TeamTaskDependencyMapper mapper = mock(TeamTaskDependencyMapper.class);
        ITeamTaskService taskService = mock(ITeamTaskService.class);
        // t3 完成 → 依赖方 t1/t2 重算；两者都无其它依赖 → blocked 置 0（当前 1 → 各触发一次 update）
        when(taskService.getById("t1")).thenReturn(taskOf("t1", "todo", 1));
        when(taskService.getById("t2")).thenReturn(taskOf("t2", "doing", 1));

        TeamTaskDependencyServiceImpl svc = newService(mapper, taskService,
                Collections.emptyList(), Arrays.asList(link("t1", "t3"), link("t2", "t3")));
        svc.refreshDependentsBlocked("t3");

        verify(taskService, times(2)).update(any());
    }

    @Test
    void listDependsOnTasksReturnsDependencySnapshots() {
        TeamTaskDependencyMapper mapper = mock(TeamTaskDependencyMapper.class);
        ITeamTaskService taskService = mock(ITeamTaskService.class);
        when(taskService.listByIds(any())).thenAnswer(inv -> {
            List<String> ids = inv.getArgument(0);
            return Arrays.asList(taskOf(ids.get(0), "done", 0));
        });
        TeamTaskDependencyServiceImpl svc = newService(mapper, taskService,
                Collections.singletonList(link("t1", "t2")), Collections.emptyList());

        List<TeamTask> dependsOn = svc.listDependsOnTasks("t1");
        assertEquals(1, dependsOn.size());
        assertEquals("t2", dependsOn.get(0).getId());
        assertEquals("done", dependsOn.get(0).getStatus());
    }

    @Test
    void listDependsOnTasksEmptyReturnsEmpty() {
        TeamTaskDependencyMapper mapper = mock(TeamTaskDependencyMapper.class);
        ITeamTaskService taskService = mock(ITeamTaskService.class);
        TeamTaskDependencyServiceImpl svc = newService(mapper, taskService,
                Collections.emptyList(), Collections.emptyList());
        assertTrue(svc.listDependsOnTasks("t1").isEmpty());
        verify(taskService, never()).listByIds(any());
    }

    @Test
    void mapUnfinishedDependsOnIdsFiltersDoneAndGroupsByTask() {
        TeamTaskDependencyMapper mapper = mock(TeamTaskDependencyMapper.class);
        ITeamTaskService taskService = mock(ITeamTaskService.class);
        // t1→t2,t3；t2→t3。被依赖任务批查只返回未完成的 t3（t2 已 done 由 SQL 过滤，不回表）
        when(taskService.list(any(LambdaQueryWrapper.class))).thenAnswer(inv -> {
            LambdaQueryWrapper<TeamTask> w = inv.getArgument(0);
            assertTrue(w.getSqlSegment().contains("status"), "须下发 status != done 条件: " + w.getSqlSegment());
            return Collections.singletonList(taskOf("t3", "doing", 0));
        });
        TeamTaskDependencyServiceImpl svc = newService(mapper, taskService,
                Arrays.asList(link("t1", "t2"), link("t1", "t3"), link("t2", "t3")),
                Collections.emptyList());

        Map<String, List<String>> result = svc.mapUnfinishedDependsOnIds(Arrays.asList("t1", "t2"));

        assertEquals(2, result.size());
        assertEquals(Collections.singletonList("t3"), result.get("t1"), "已完成的 t2 不得出现在 blockedBy");
        assertEquals(Collections.singletonList("t3"), result.get("t2"));
    }

    @Test
    void mapUnfinishedDependsOnIdsEmptyInputs() {
        TeamTaskDependencyMapper mapper = mock(TeamTaskDependencyMapper.class);
        ITeamTaskService taskService = mock(ITeamTaskService.class);
        TeamTaskDependencyServiceImpl svc = newService(mapper, taskService,
                Collections.emptyList(), Collections.emptyList());

        assertTrue(svc.mapUnfinishedDependsOnIds(Collections.emptyList()).isEmpty());
        assertTrue(svc.mapUnfinishedDependsOnIds(null).isEmpty());
        // 无依赖行时不查被依赖任务（第二查跳过）
        assertTrue(svc.mapUnfinishedDependsOnIds(Collections.singletonList("t1")).isEmpty());
        verify(taskService, never()).list(any(LambdaQueryWrapper.class));
    }
}
