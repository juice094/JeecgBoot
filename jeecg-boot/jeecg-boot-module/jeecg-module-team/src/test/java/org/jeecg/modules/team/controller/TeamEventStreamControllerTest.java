package org.jeecg.modules.team.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jeecg.modules.team.service.impl.TeamAuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 事件流端点形状测试（Mockito，无 Spring MVC）。
 *
 * 覆盖：
 * 1) 端点返回非空 SseEmitter（SSE 契约的形状而非 HTTP 序列化）；
 * 2) bizType 逗号分隔过滤参数解析后透传 service（去空白、丢空段）；
 * 3) tail 缺省 50、越界夹取到 1-200。
 *
 * 注：后台轮询线程由注入的单线程 executor 承载，每用例收尾 complete + shutdown 终止循环。
 */
class TeamEventStreamControllerTest {

    private TeamEventStreamController controller;
    private TeamAuditLogService auditLogService;
    private ExecutorService testExecutor;

    @BeforeEach
    void setUp() {
        controller = new TeamEventStreamController();
        auditLogService = mock(TeamAuditLogService.class);
        when(auditLogService.listLatestForStream(anyList(), anyInt())).thenReturn(Collections.emptyList());
        when(auditLogService.findMaxId(anyList())).thenReturn("100");
        when(auditLogService.listSinceId(isNull(), anyList(), anyInt())).thenReturn(Collections.emptyList());
        when(auditLogService.listSinceId(anyString(), anyList(), anyInt())).thenReturn(Collections.emptyList());
        ReflectionTestUtils.setField(controller, "auditLogService", auditLogService);
        testExecutor = Executors.newSingleThreadExecutor();
        ReflectionTestUtils.setField(controller, "streamExecutor", testExecutor);
    }

    @AfterEach
    void tearDown() {
        testExecutor.shutdownNow();
    }

    @Test
    void streamReturnsEmitterAndDefaultsTailToFifty() {
        SseEmitter emitter = controller.stream(null, null);
        assertNotNull(emitter, "SSE 端点必须返回非空发射器");

        ArgumentCaptor<List<String>> typesCaptor = ArgumentCaptor.forClass(List.class);
        verify(auditLogService, timeout(5000)).listLatestForStream(typesCaptor.capture(), org.mockito.ArgumentMatchers.eq(50));
        assertTrue(typesCaptor.getValue().isEmpty(), "无 bizType 时应透传空过滤（全量）");

        emitter.complete();
    }

    @Test
    void streamParsesCommaSeparatedBizTypeFilter() {
        SseEmitter emitter = controller.stream("task, approval ,,bug", 10);
        assertNotNull(emitter);

        ArgumentCaptor<List<String>> typesCaptor = ArgumentCaptor.forClass(List.class);
        verify(auditLogService, timeout(5000)).listLatestForStream(typesCaptor.capture(), org.mockito.ArgumentMatchers.eq(10));
        assertEquals(List.of("task", "approval", "bug"), typesCaptor.getValue(), "逗号分隔过滤应去空白并丢弃空段");

        emitter.complete();
    }

    @Test
    void streamClampsOversizedTailToMax() {
        SseEmitter emitter = controller.stream(null, 999999);
        assertNotNull(emitter);
        verify(auditLogService, timeout(5000)).listLatestForStream(anyList(), org.mockito.ArgumentMatchers.eq(200));
        emitter.complete();
    }

    @Test
    void streamClampsUndersizedTailToMin() {
        SseEmitter emitter = controller.stream(null, 0);
        assertNotNull(emitter);
        verify(auditLogService, timeout(5000)).listLatestForStream(anyList(), org.mockito.ArgumentMatchers.eq(1));
        emitter.complete();
    }
}
