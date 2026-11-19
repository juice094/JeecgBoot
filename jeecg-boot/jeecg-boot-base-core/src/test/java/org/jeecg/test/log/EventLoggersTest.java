package org.jeecg.test.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.jeecg.common.log.EventLoggers;
import org.jeecg.config.filter.TraceIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * EventLoggers 单测：事件行单行 JSON 可解析（FastJSON2）、统一字段规范（ts/event/traceId）、
 * 字符串值控制字符剔除（防日志伪造注入）。
 */
class EventLoggersTest {

    private Logger teamEventLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        teamEventLogger = (Logger) LoggerFactory.getLogger("TEAM_EVENT");
        appender = new ListAppender<>();
        appender.start();
        teamEventLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        teamEventLogger.detachAppender(appender);
        appender.stop();
        MDC.clear();
    }

    @Test
    @DisplayName("事件行：单行、JSON 可解析，ts/event/业务字段齐全")
    void eventLineIsSingleLineParseableJson() {
        EventLoggers.info(EventLoggers.TEAM_EVENT, "approval.submitted",
                "code", "OA-20261119-001",
                "amount", new BigDecimal("12.50"),
                "stepCount", 2);

        assertEquals(1, appender.list.size());
        String line = appender.list.get(0).getFormattedMessage();
        assertFalse(line.contains("\n"), "事件必须是单行 JSON");

        JSONObject json = JSON.parseObject(line);
        assertTrue(json.getLongValue("ts") > 0, "ts 应为 epoch 毫秒");
        assertEquals("approval.submitted", json.getString("event"));
        assertEquals("OA-20261119-001", json.getString("code"));
        assertEquals(new BigDecimal("12.50"), json.getBigDecimal("amount"));
        assertEquals(2, json.getIntValue("stepCount"));
    }

    @Test
    @DisplayName("MDC 有 traceId 时自动附加（traceId 贯通的消费入口）")
    void traceIdFromMdcIsIncluded() {
        MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, "trace-for-event");

        EventLoggers.info(EventLoggers.TEAM_EVENT, "mail.sent", "accountId", "acc-1");

        JSONObject json = JSON.parseObject(appender.list.get(0).getFormattedMessage());
        assertEquals("trace-for-event", json.getString(TraceIdFilter.TRACE_ID_MDC_KEY));
        assertEquals("acc-1", json.getString("accountId"));
    }

    @Test
    @DisplayName("字符串值剔除控制字符：单行不被伪造换行破坏")
    void controlCharsAreStrippedFromStringValues() {
        EventLoggers.info(EventLoggers.TEAM_EVENT, "approval.validation_failed",
                "category", "leave", "reason", "bad\r\nvalue");

        String line = appender.list.get(0).getFormattedMessage();
        assertFalse(line.contains("\n") || line.contains("\r"));
        JSONObject json = JSON.parseObject(line);
        assertEquals("bad__value", json.getString("reason"));
    }

    @Test
    @DisplayName("无业务字段重载：仅 ts/event 也可输出")
    void noFieldsOverloadStillEmits() {
        EventLoggers.info(EventLoggers.TEAM_EVENT, "mail.bind");

        JSONObject json = JSON.parseObject(appender.list.get(0).getFormattedMessage());
        assertEquals("mail.bind", json.getString("event"));
        assertTrue(json.containsKey("ts"));
    }
}
