package org.jeecg.test.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import jakarta.servlet.ServletException;

import org.jeecg.config.filter.TraceIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * TraceIdFilter 单测：MDC 放入/清理、header 透传白名单、既有 MDC 值不越权清除。
 */
class TraceIdFilterTest {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("无 header：生成 UUID 写入 MDC，finally 清理")
    void noHeaderGeneratesUuidAndCleansMdc() throws ServletException, IOException {
        AtomicReference<String> seenInChain = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, (req, res) ->
                seenInChain.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)));

        assertNotNull(seenInChain.get());
        assertTrue(UUID_PATTERN.matcher(seenInChain.get()).matches(),
                "应生成 UUID，实际: " + seenInChain.get());
        assertEquals(seenInChain.get(), response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY), "请求结束后 MDC 必须清理，防线程池复用串号");
    }

    @Test
    @DisplayName("合法 header 透传：沿用上游 traceId 并回写响应")
    void validHeaderIsPropagated() throws ServletException, IOException {
        AtomicReference<String> seenInChain = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "upstream-trace_123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                seenInChain.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)));

        assertEquals("upstream-trace_123", seenInChain.get());
        assertEquals("upstream-trace_123", response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
        assertNull(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY));
    }

    @Test
    @DisplayName("非法 header（含换行）被拒绝：不落 MDC、不回写响应")
    void maliciousHeaderIsIgnored() throws ServletException, IOException {
        AtomicReference<String> seenInChain = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "evil\ninjected");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                seenInChain.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)));

        assertNotNull(seenInChain.get());
        assertFalse(seenInChain.get().contains("evil"), "非法 header 值不得进入 MDC");
        assertTrue(UUID_PATTERN.matcher(seenInChain.get()).matches());
        assertFalse(response.getHeader(TraceIdFilter.TRACE_ID_HEADER).contains("evil"));
    }

    @Test
    @DisplayName("MDC 已有值（内层转发/嵌套链）：不覆盖、不越权清理")
    void preExistingMdcValueIsPreserved() throws ServletException, IOException {
        MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, "existing-trace");
        AtomicReference<String> seenInChain = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) ->
                seenInChain.set(MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY)));

        assertEquals("existing-trace", seenInChain.get());
        assertEquals("existing-trace", MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY),
                "非本过滤器设置的 MDC 值不得清理");
    }
}
