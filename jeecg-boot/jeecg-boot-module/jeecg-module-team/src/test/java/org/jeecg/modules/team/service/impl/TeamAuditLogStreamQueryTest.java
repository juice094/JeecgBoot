package org.jeecg.modules.team.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jeecg.modules.team.entity.TeamAuditLog;
import org.jeecg.modules.team.mapper.TeamAuditLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * 事件流增量查询单元测试（Mockito，无 Spring 上下文）。
 *
 * 覆盖：
 * 1) listLatestForStream——倒序取数后内存翻转为正序（回放按时间线顺序推送）；
 * 2) bizType 过滤——非空过滤拼 IN 条件，空列表不拼（全量）；
 * 3) listSinceId 增量边界——gt(lastId) 游标、id 正序、单轮 LIMIT；
 * 4) findMaxId——空表返回 null 作为增量起点。
 */
class TeamAuditLogStreamQueryTest {

    private TeamAuditLogService newService(TeamAuditLogMapper mapper) {
        TeamAuditLogService svc = new TeamAuditLogService();
        ReflectionTestUtils.setField(svc, "baseMapper", mapper);
        return svc;
    }

    private TeamAuditLog row(String id) {
        TeamAuditLog row = new TeamAuditLog();
        ReflectionTestUtils.setField(row, "id", id);
        return row;
    }

    @Test
    void listLatestReversesDescResultToAscendingForReplay() {
        TeamAuditLogMapper mapper = mock(TeamAuditLogMapper.class);
        // 模拟数据库按 create_time 倒序返回
        when(mapper.selectList(any())).thenReturn(Arrays.asList(row("3"), row("2"), row("1")));
        TeamAuditLogService svc = newService(mapper);

        List<TeamAuditLog> result = svc.listLatestForStream(Collections.emptyList(), 50);

        assertEquals(Arrays.asList("1", "2", "3"), Arrays.asList(result.get(0).getId(),
                result.get(1).getId(), result.get(2).getId()), "回放必须按时间正序");
        ArgumentCaptor<QueryWrapper<TeamAuditLog>> captor = captor();
        verify(mapper).selectList(captor.capture());
        String segment = captor.getValue().getSqlSegment();
        assertTrue(segment.contains("ORDER BY"), "应带排序");
        assertTrue(segment.contains("LIMIT"), "应带条数上限");
        assertTrue(!segment.contains("IN ("), "空过滤不应拼 IN 条件");
    }

    @Test
    void listLatestAppliesBizTypeInFilterWhenProvided() {
        TeamAuditLogMapper mapper = mock(TeamAuditLogMapper.class);
        when(mapper.selectList(any())).thenReturn(Collections.singletonList(row("1")));
        TeamAuditLogService svc = newService(mapper);

        svc.listLatestForStream(Arrays.asList("task", "bug"), 10);

        ArgumentCaptor<QueryWrapper<TeamAuditLog>> captor = captor();
        verify(mapper).selectList(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("biz_type IN ("), "非空过滤应拼 biz_type IN");
    }

    @Test
    void listSinceIdAppliesCursorAndAscendingOrder() {
        TeamAuditLogMapper mapper = mock(TeamAuditLogMapper.class);
        when(mapper.selectList(any())).thenReturn(Arrays.asList(row("101"), row("102")));
        TeamAuditLogService svc = newService(mapper);

        List<TeamAuditLog> result = svc.listSinceId("100", Arrays.asList("task"), 200);

        assertEquals(2, result.size(), "游标之后的数据应全量返回");
        ArgumentCaptor<QueryWrapper<TeamAuditLog>> captor = captor();
        verify(mapper).selectList(captor.capture());
        String segment = captor.getValue().getSqlSegment();
        assertTrue(segment.contains("id >"), "增量边界应为 id > lastId");
        assertTrue(segment.contains("biz_type IN ("), "过滤条件应透传");
        assertTrue(segment.contains("LIMIT"), "单轮应有条数上限");
    }

    @Test
    void listSinceIdWithNullCursorStartsFromMaxWithoutReplay() {
        TeamAuditLogMapper mapper = mock(TeamAuditLogMapper.class);
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());
        TeamAuditLogService svc = newService(mapper);

        svc.listSinceId(null, Collections.emptyList(), 200);

        ArgumentCaptor<QueryWrapper<TeamAuditLog>> captor = captor();
        verify(mapper).selectList(captor.capture());
        assertTrue(!captor.getValue().getSqlSegment().contains("id >"),
                "null 游标（空表起步）不应拼 gt 条件");
    }

    @Test
    void findMaxIdReturnsNullOnEmptyTable() {
        TeamAuditLogMapper mapper = mock(TeamAuditLogMapper.class);
        when(mapper.selectOne(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(null);
        TeamAuditLogService svc = newService(mapper);

        assertNull(svc.findMaxId(Collections.emptyList()), "空表返回 null 作为增量起点");
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<QueryWrapper<TeamAuditLog>> captor() {
        return ArgumentCaptor.forClass(QueryWrapper.class);
    }
}
