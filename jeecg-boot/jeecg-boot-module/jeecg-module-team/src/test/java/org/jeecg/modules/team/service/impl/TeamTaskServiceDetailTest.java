package org.jeecg.modules.team.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.mapper.TeamAuditLogMapper;
import org.jeecg.modules.team.mapper.TeamTaskMapper;
import org.jeecg.modules.team.service.ITeamTaskCommentService;
import org.jeecg.modules.team.service.ITeamTaskDependencyService;
import org.jeecg.modules.team.vo.TeamTaskDetail;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * /team/task/detail 响应内嵌 blockedBy 用例（A/D-96）：
 * 详情一次拉取未完成依赖任务 id 列表，无依赖时恒为空数组。
 */
class TeamTaskServiceDetailTest {

    private TeamTaskServiceImpl newService(TeamTaskMapper mapper, ITeamTaskDependencyService depService) {
        TeamTaskServiceImpl svc = new TeamTaskServiceImpl();
        ReflectionTestUtils.setField(svc, "baseMapper", mapper);
        ReflectionTestUtils.setField(svc, "dependencyService", depService);
        ITeamTaskCommentService commentService = mock(ITeamTaskCommentService.class);
        when(commentService.listByTask(any())).thenReturn(Collections.emptyList());
        ReflectionTestUtils.setField(svc, "commentService", commentService);
        TeamAuditLogMapper auditLogMapper = mock(TeamAuditLogMapper.class);
        when(auditLogMapper.selectList(any())).thenReturn(Collections.emptyList());
        ReflectionTestUtils.setField(svc, "auditLogMapper", auditLogMapper);
        return svc;
    }

    private TeamTask task(String id) {
        TeamTask t = new TeamTask();
        ReflectionTestUtils.setField(t, "id", id);
        t.setTitle("任务-" + id);
        t.setStatus("todo");
        return t;
    }

    @Test
    void detailCarriesUnfinishedDependencyIds() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        when(mapper.selectById("t1")).thenReturn(task("t1"));
        ITeamTaskDependencyService depService = mock(ITeamTaskDependencyService.class);
        Map<String, java.util.List<String>> blockedByMap = new HashMap<>();
        blockedByMap.put("t1", Collections.singletonList("t9"));
        when(depService.mapUnfinishedDependsOnIds(any())).thenReturn(blockedByMap);

        TeamTaskDetail detail = newService(mapper, depService).detail("t1");

        assertEquals(Collections.singletonList("t9"), detail.getBlockedBy());
    }

    @Test
    void detailBlockedByDefaultsToEmptyWhenNoUnfinishedDependency() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        when(mapper.selectById("t1")).thenReturn(task("t1"));
        ITeamTaskDependencyService depService = mock(ITeamTaskDependencyService.class);
        when(depService.mapUnfinishedDependsOnIds(any())).thenReturn(Collections.emptyMap());

        TeamTaskDetail detail = newService(mapper, depService).detail("t1");

        assertTrue(detail.getBlockedBy().isEmpty(), "无未完成依赖 → 空数组");
    }
}
