package org.jeecg.config.filter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * @Description: 全请求 traceId 过滤器（日志埋点 P0，规划稿 docs/roadmap/logging-instrumentation-plan.md §2.1）
 * @Author: logging-instrumentation
 * @Date: 2026-11-19
 *
 * 每个请求生成 UUID 写入 MDC（键 {@link #TRACE_ID_MDC_KEY}），logback pattern 以 %X{traceId} 输出；
 * 上游经 {@link #TRACE_ID_HEADER} 透传的合法 traceId 直接沿用（值做白名单校验，防日志伪造注入）。
 * 仅当本次请求自行设置 MDC 值时才在 finally 清理——上游已设值（内层转发/嵌套链）时不越权清除。
 *
 * 注册方式权衡（结论见 TraceIdFilterConfig，类注释记录决策）：
 * 1. 不用 @Component 依赖 Boot 的 Filter bean 自动注册——自动注册顺序不可控，且 mail 模块 D-1
 *    已踩过「条件评估早于组件扫描」的装配时序坑（MailCodecConfig 注释），改走模块内 @Configuration
 *    显式 FilterRegistrationBean，顺序、URL 模式全部显式；
 * 2. 不插入 Shiro 过滤器链——Shiro 链是 PathMatchingFilter 语义（登录跳转/权限拦截），与
 *    OncePerRequestFilter 的 servlet 语义不同，嵌入会与 ShiroConfig 的链定义纠缠，且无法保证
 *    MDC 先于平台过滤器（含认证失败路径）生效；
 * 3. 顺序置于最高优先级区：认证失败、全局异常处理等阶段打出的日志也要带 traceId。
 *
 * ponytail: 异步线程（线程池/调度）不自动继承 MDC，由业务侧自行透传（先例 SystemMailServiceImpl D-1）；
 * ERROR/ASYNC 分派默认不再过滤（OncePerRequestFilter 默认行为），REQUEST 分派已覆盖 P0 验收口径。
 */
public class TraceIdFilter extends OncePerRequestFilter {

    /** MDC 链路键名；logback pattern 的 %X{traceId} 读取本键。mail 模块 MailTraceConstants 亦引用本常量 */
    public static final String TRACE_ID_MDC_KEY = "traceId";

    /** 上游 traceId 透传 header（跨服务/P2 微服务拆分时启用，规划稿 §2.1） */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** 透传 traceId 的最大长度 */
    private static final int TRACE_ID_MAX_LENGTH = 64;

    /** 透传 traceId 白名单：仅字母数字/连字符/下划线（防 CR/LF 日志注入） */
    private static final Pattern SAFE_TRACE_ID =
            Pattern.compile("^[A-Za-z0-9_-]{1," + TRACE_ID_MAX_LENGTH + "}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean owned = false;
        try {
            if (MDC.get(TRACE_ID_MDC_KEY) == null) {
                String traceId = resolveTraceId(request);
                MDC.put(TRACE_ID_MDC_KEY, traceId);
                response.setHeader(TRACE_ID_HEADER, traceId);
                owned = true;
            }
            filterChain.doFilter(request, response);
        } finally {
            if (owned) {
                MDC.remove(TRACE_ID_MDC_KEY);
            }
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String inbound = request.getHeader(TRACE_ID_HEADER);
        if (inbound != null && SAFE_TRACE_ID.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString();
    }
}
