package org.jeecg.common.log;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jeecg.config.filter.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.alibaba.fastjson2.JSONObject;

/**
 * @Description: 业务事件日志工具（日志埋点 P0，规划稿 docs/roadmap/logging-instrumentation-plan.md §2.2）
 * @Author: logging-instrumentation
 * @Date: 2026-11-19
 *
 * 专用 logger 输出单行 JSON 事件（FastJSON2/slf4j 均为既有依赖，零新增）。
 *
 * 统一字段规范（jq/grep 可直接消费）：
 * <ul>
 *   <li>ts    ：epoch 毫秒（Long，无时区歧义、可排序）</li>
 *   <li>event ：点分层级事件名，如 approval.submitted / mail.sent</li>
 *   <li>traceId：当前线程 MDC 存在时自动附加（traceId 贯通的消费入口）</li>
 *   <li>其余业务字段由调用方平铺传入，禁止嵌套对象</li>
 * </ul>
 * 字符串值剔除控制字符（防日志伪造注入）；null 值照常输出（fastjson2 序列化为 null）。
 *
 * 审计双轨：本类只写技术/运营面日志文件；人事/合规审计入库仍走 TeamAuditLogger，两者不合并（规划稿 §2.3）。
 */
public final class EventLoggers {

    /** team 模块业务事件 logger（logback 可按 logger name 单独路由/定级） */
    public static final Logger TEAM_EVENT = LoggerFactory.getLogger("TEAM_EVENT");

    /** mail 模块业务事件 logger */
    public static final Logger MAIL_EVENT = LoggerFactory.getLogger("MAIL_EVENT");

    private EventLoggers() {
    }

    public static void info(Logger logger, String event) {
        info(logger, event, (Map<String, ?>) null);
    }

    /**
     * 输出单行 JSON 事件。
     *
     * @param logger 专用事件 logger（{@link #TEAM_EVENT}/{@link #MAIL_EVENT}）
     * @param event  点分层级事件名
     * @param fields 业务字段（平铺；可为 null）
     */
    public static void info(Logger logger, String event, Map<String, ?> fields) {
        JSONObject payload = new JSONObject();
        payload.put("ts", System.currentTimeMillis());
        payload.put("event", event);
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        if (traceId != null) {
            payload.put(TraceIdFilter.TRACE_ID_MDC_KEY, traceId);
        }
        if (fields != null) {
            fields.forEach((key, value) -> payload.put(key, sanitize(value)));
        }
        logger.info(payload.toJSONString());
    }

    /**
     * 便捷重载：以 key1, value1, key2, value2... 平铺传入，调用点保持单行。
     * keyValues 必须成对；奇数个时末位 key 被忽略。
     */
    public static void info(Logger logger, String event, Object... keyValues) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            fields.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        info(logger, event, fields);
    }

    private static Object sanitize(Object value) {
        if (value instanceof String) {
            return ((String) value).replaceAll("[\\p{Cntrl}]", "_");
        }
        return value;
    }
}
