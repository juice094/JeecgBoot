package com.lanou.wordsync.start;

import com.lanou.wordsync.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kafka 消费状态查询，用于验证消费端确实收到事件。
 */
@RestController
@RequestMapping("/word-sync/api/kafka")
public class KafkaStatusController {

    private final KafkaEventListener listener;

    public KafkaStatusController(KafkaEventListener listener) {
        this.listener = listener;
    }

    @GetMapping("/status")
    public Result<Map<String, Long>> status() {
        Map<String, Long> data = new LinkedHashMap<>();
        data.put("syncCount", listener.getSyncCount());
        data.put("auditCount", listener.getAuditCount());
        return Result.ok(data);
    }
}
