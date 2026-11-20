package org.jeecg.config;

import org.jeecg.config.filter.TraceIdFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * @Description: traceId 过滤器装配（日志埋点 P0）
 * @Author: logging-instrumentation
 * @Date: 2026-11-19
 *
 * 模式对齐 MailCodecConfig（模块内 @Configuration + @Bean 显式装配，不用 @Component 自动注册），
 * 顺序与 URL 模式显式可控；决策权衡见 TraceIdFilter 类注释。
 */
@Configuration
public class TraceIdFilterConfig {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TraceIdFilter());
        bean.addUrlPatterns("/*");
        // 最高优先级区：认证失败与全局异常处理阶段打出的日志也要带 traceId
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        bean.setName("traceIdFilter");
        return bean;
    }
}
