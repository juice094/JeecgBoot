package org.jeecg.modules.mail.constant;

import org.jeecg.config.filter.TraceIdFilter;

/**
 * @Description: OA 邮件-链路追踪常量
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 口径（接口文档 §1 链路 / 需求文档 D-1）：异步投递日志透传 traceId（MDC），
 * system_mail_log.trace_id 与之一致。键值统一定义在 base-core 的 TraceIdFilter
 * （日志埋点 P0 全请求过滤器），本常量保留为 mail 模块内引用点（向后兼容）。
 */
public final class MailTraceConstants {

    /** MDC 中的链路键名，与 logback pattern 的 %X{traceId} 对应 */
    public static final String TRACE_ID_MDC_KEY = TraceIdFilter.TRACE_ID_MDC_KEY;

    private MailTraceConstants() {
    }
}
