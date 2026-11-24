package org.jeecg.modules.team.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jeecg.modules.team.entity.TeamLabel;
import org.jeecg.modules.team.entity.TeamTaskLabel;
import org.jeecg.modules.team.mapper.TeamLabelMapper;
import org.jeecg.modules.team.mapper.TeamTaskLabelMapper;
import org.jeecg.modules.team.service.ITeamLabelService;
import org.jeecg.modules.team.service.ITeamTaskLabelService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 标签体系单元测试（Mockito，无 Spring 上下文）。
 *
 * 覆盖：
 * 1) 删除标签级联清理 team_task_label（单删 + 批量删）；
 * 2) setTaskLabels 全量替换语义（先删后插、去重保序、空值丢弃、空列表=清空）；
 * 3) listLabelsByTaskIds 批量组装（标签已删的悬空关联静默跳过）。
 */
class TeamLabelServiceTest {

    private TeamLabelServiceImpl newLabelService(TeamLabelMapper mapper, ITeamTaskLabelService linkService) {
        TeamLabelServiceImpl svc = new TeamLabelServiceImpl();
        ReflectionTestUtils.setField(svc, "baseMapper", mapper);
        ReflectionTestUtils.setField(svc, "teamTaskLabelService", linkService);
        return svc;
    }

    private TeamTaskLabelServiceImpl newLinkService(TeamTaskLabelMapper mapper, ITeamLabelService labelService) {
        TeamTaskLabelServiceImpl svc = new TeamTaskLabelServiceImpl();
        ReflectionTestUtils.setField(svc, "baseMapper", mapper);
        ReflectionTestUtils.setField(svc, "labelService", labelService);
        return svc;
    }

    @Test
    void deleteWithLinksRemovesAssociationsFirst() {
        TeamLabelMapper mapper = mock(TeamLabelMapper.class);
        ITeamTaskLabelService linkService = mock(ITeamTaskLabelService.class);
        TeamLabelServiceImpl svc = newLabelService(mapper, linkService);

        svc.deleteWithLinks("l1");

        verify(linkService).remove(any(LambdaQueryWrapper.class));
        verify(mapper).deleteById("l1");
    }

    @Test
    void deleteBatchWithLinksRemovesAllAssociations() {
        TeamLabelMapper mapper = mock(TeamLabelMapper.class);
        ITeamTaskLabelService linkService = mock(ITeamTaskLabelService.class);
        TeamLabelServiceImpl svc = newLabelService(mapper, linkService);

        svc.deleteBatchWithLinks(Arrays.asList("l1", "l2"));

        verify(linkService).remove(any(LambdaQueryWrapper.class));
        verify(mapper).deleteByIds(Arrays.asList("l1", "l2"));
    }

    @Test
    void setTaskLabelsReplacesWithDedupAndOrderKept() {
        TeamTaskLabelMapper mapper = mock(TeamTaskLabelMapper.class);
        TeamTaskLabelServiceImpl svc = newLinkService(mapper, mock(ITeamLabelService.class));

        svc.setTaskLabels("t1", Arrays.asList("l2", "l1", "l2", null, "  "));

        verify(mapper).delete(any(LambdaQueryWrapper.class));
        ArgumentCaptor<TeamTaskLabel> captor = ArgumentCaptor.forClass(TeamTaskLabel.class);
        verify(mapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<TeamTaskLabel> inserted = captor.getAllValues();
        assertEquals(2, inserted.size(), "去重后只插 2 行（null/空白丢弃）");
        assertEquals("l2", inserted.get(0).getLabelId(), "保持传入顺序");
        assertEquals("l1", inserted.get(1).getLabelId());
        inserted.forEach(row -> assertEquals("t1", row.getTaskId()));
    }

    @Test
    void setTaskLabelsWithEmptyListClearsAll() {
        TeamTaskLabelMapper mapper = mock(TeamTaskLabelMapper.class);
        TeamTaskLabelServiceImpl svc = newLinkService(mapper, mock(ITeamLabelService.class));

        svc.setTaskLabels("t1", Collections.emptyList());

        verify(mapper).delete(any(LambdaQueryWrapper.class));
        verify(mapper, org.mockito.Mockito.times(0)).insert(any(TeamTaskLabel.class));
    }

    @Test
    void listLabelsByTaskIdsSkipsDanglingLinks() {
        TeamTaskLabelMapper mapper = mock(TeamTaskLabelMapper.class);
        ITeamLabelService labelService = mock(ITeamLabelService.class);

        TeamTaskLabel link1 = new TeamTaskLabel();
        link1.setTaskId("t1");
        link1.setLabelId("l1");
        TeamTaskLabel linkDangling = new TeamTaskLabel();
        linkDangling.setTaskId("t1");
        linkDangling.setLabelId("l-deleted");
        when(mapper.selectList(any())).thenReturn(Arrays.asList(link1, linkDangling));

        TeamLabel label = new TeamLabel();
        ReflectionTestUtils.setField(label, "id", "l1");
        label.setName("bug");
        when(labelService.listByIds(any())).thenReturn(Collections.singletonList(label));

        TeamTaskLabelServiceImpl svc = newLinkService(mapper, labelService);
        Map<String, List<TeamLabel>> result = svc.listLabelsByTaskIds(Arrays.asList("t1", "t2"));

        assertEquals(1, result.size());
        assertEquals(1, result.get("t1").size());
        assertEquals("bug", result.get("t1").get(0).getName(), "悬空关联（标签已删）静默跳过");
    }

    @Test
    void listLabelsByTaskIdsWithEmptyInputReturnsEmptyMap() {
        TeamTaskLabelMapper mapper = mock(TeamTaskLabelMapper.class);
        TeamTaskLabelServiceImpl svc = newLinkService(mapper, mock(ITeamLabelService.class));
        assertEquals(Collections.emptyMap(), svc.listLabelsByTaskIds(Collections.emptyList()));
        org.mockito.Mockito.verifyNoInteractions(mapper);
    }
}
