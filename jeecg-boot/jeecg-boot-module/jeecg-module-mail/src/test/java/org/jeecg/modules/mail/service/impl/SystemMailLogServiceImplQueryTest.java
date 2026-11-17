package org.jeecg.modules.mail.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.mail.dto.MailLogQueryDTO;
import org.jeecg.modules.mail.dto.MailLogStatDTO;
import org.jeecg.modules.mail.entity.SystemMailLog;
import org.jeecg.modules.mail.mapper.SystemMailLogMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * @Description: SystemMailLogServiceImpl 查询/统计单测（Mapper 走 Mockito，零 Spring 容器）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 锁定口径（接口文档 §1/§3.4）：单页 ≤ 100；时间格式错误带字段名报错；stat 按状态/模板聚合。
 */
class SystemMailLogServiceImplQueryTest {

    private SystemMailLogServiceImpl newService(SystemMailLogMapper mapper) {
        SystemMailLogServiceImpl service = new SystemMailLogServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        return service;
    }

    @Test
    void pageLogs_nullQuery_rejected() {
        SystemMailLogServiceImpl service = newService(mock(SystemMailLogMapper.class));
        assertThrows(JeecgBootException.class, () -> service.pageLogs(null));
    }

    @Test
    void pageLogs_oversizedPage_cappedTo100() {
        SystemMailLogMapper mapper = mock(SystemMailLogMapper.class);
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        SystemMailLogServiceImpl service = newService(mapper);
        MailLogQueryDTO query = new MailLogQueryDTO();
        query.setPageNo(1);
        query.setPageSize(1000);
        IPage<SystemMailLog> page = service.pageLogs(query);
        assertEquals(100, page.getSize());
    }

    @Test
    void pageLogs_badTimeFormat_throwsWithFieldName() {
        SystemMailLogMapper mapper = mock(SystemMailLogMapper.class);
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        SystemMailLogServiceImpl service = newService(mapper);
        MailLogQueryDTO query = new MailLogQueryDTO();
        query.setBeginTime("2026/11/17");
        JeecgBootException ex = assertThrows(JeecgBootException.class, () -> service.pageLogs(query));
        assertTrue(ex.getMessage().contains("beginTime"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void stat_aggregatesStatusAndTemplate() {
        SystemMailLogMapper mapper = mock(SystemMailLogMapper.class);
        when(mapper.selectMaps(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenAnswer(invocation -> {
                    com.baomidou.mybatisplus.core.conditions.Wrapper<SystemMailLog> wrapper =
                            invocation.getArgument(0);
                    String sql = wrapper.getSqlSelect() == null ? "" : wrapper.getSqlSelect();
                    if (sql.contains("send_status")) {
                        return List.<Map<String, Object>>of(
                                Map.of("send_status", 2, "cnt", 8),
                                Map.of("send_status", 3, "cnt", 1));
                    }
                    return List.<Map<String, Object>>of(Map.of("template_code", "APPROVAL_TIMEOUT", "cnt", 9));
                });
        SystemMailLogServiceImpl service = newService(mapper);
        MailLogStatDTO stat = service.stat();
        assertEquals(Long.valueOf(8), stat.getStatusCounts().get(2));
        assertEquals(Long.valueOf(1), stat.getStatusCounts().get(3));
        assertEquals(Long.valueOf(9), stat.getTemplateCounts().get("APPROVAL_TIMEOUT"));
    }

    @Test
    void stat_emptyRows_returnsEmptyMaps() {
        SystemMailLogMapper mapper = mock(SystemMailLogMapper.class);
        when(mapper.selectMaps(any())).thenReturn(List.of());
        SystemMailLogServiceImpl service = newService(mapper);
        MailLogStatDTO stat = service.stat();
        assertTrue(stat.getStatusCounts().isEmpty());
        assertTrue(stat.getTemplateCounts().isEmpty());
    }
}
