package org.jeecg.modules.mail.constant;

/**
 * @Description: OA 邮件-链路追踪常量
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 口径（接口文档 §1 链路 / 需求文档 D-1）：异步投递日志透传 traceId（MDC），
 * system_mail_log.trace_id 与之一致。若后续 jeecg 接入统一 trace 组件，仅需改本常量。
 */
public final class MailTraceConstants {

    /** MDC 中的链路键名，与 logback pattern 的 %X{traceId} 对应 */
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private MailTraceConstants() {
    }
}
