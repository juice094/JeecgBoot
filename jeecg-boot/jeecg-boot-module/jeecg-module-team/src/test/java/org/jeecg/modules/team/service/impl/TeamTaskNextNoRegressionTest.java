package org.jeecg.modules.team.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jeecg.modules.team.mapper.TeamTaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * nextTaskNo 存量修复回归（D-95，照 nextBugNo 修法）：
 * 删过最大序号的任务后新建不得复用已删序号撞 uk_team_task_no。
 *
 * 修法口径：getOne(orderByDesc) → mapper 手写 SELECT MAX(task_no)（含逻辑删行）。
 * 回归锚点：只桩 selectMaxTaskNo，并断言不触碰 selectList（旧 getOne 路径必走 selectList，
 * 一旦回退该用例即红）。
 */
class TeamTaskNextNoRegressionTest {

    private TeamTaskServiceImpl newService(TeamTaskMapper mapper) {
        TeamTaskServiceImpl svc = new TeamTaskServiceImpl();
        ReflectionTestUtils.setField(svc, "baseMapper", mapper);
        return svc;
    }

    @Test
    void nextTaskNoSkipsLogicallyDeletedRowsViaMaxQuery() {
        // 场景：task_no=42 的任务已逻辑删（deleted=1），新任务仍须取 43，不得复用 42 撞唯一键
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        when(mapper.selectMaxTaskNo()).thenReturn(42);

        assertEquals(43, newService(mapper).nextTaskNo());
        verify(mapper, never()).selectList(any());
    }

    @Test
    void nextTaskNoStartsAtOneWhenTableEmpty() {
        TeamTaskMapper mapper = mock(TeamTaskMapper.class);
        when(mapper.selectMaxTaskNo()).thenReturn(null);

        assertEquals(1, newService(mapper).nextTaskNo());
    }
}
