package org.jeecg.modules.team.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.team.entity.TeamAuditLog;
import org.jeecg.modules.team.service.impl.TeamAuditLogService;
import org.jeecg.modules.team.vo.TeamEventVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @Description: 团队协作-业务事件流（SSE 推送，把 team_audit_log 从「拉取」变「推送」）
 * @Author: juice094
 * @Date: 2026-11-20
 * @Version: V1.0
 *
 * 权限说明：复用既有 team:task:list（读审计表不新增权限码）。
 *
 * ponytail: 推送采用「每 2s 轮询 id 游标」方案——上限=秒级事件量级、单实例连接数小
 * （线程池有界 2~8、队列 64，溢出即拒绝新连接）；升级路径=业务埋点双写 MQ/Redis Stream，
 * 本端点改为订阅转发，接口契约（事件字段/入参）不变。
 */
@Tag(name = "团队协作-业务事件流",
        description = "SSE 端点：连接后先回放最近 tail 条（默认 50），之后每 2s 增量推送新审计事件；"
                + "15s 心跳注释防代理断连；超时 30 分钟自动收尾。事件名 audit，载荷见 TeamEventVO。")
@RestController
@RequestMapping("/team/event")
public class TeamEventStreamController {

    private static final long STREAM_TIMEOUT_MS = 30 * 60 * 1000L;
    private static final long POLL_INTERVAL_MS = 2000L;
    private static final long HEARTBEAT_INTERVAL_MS = 15000L;
    private static final int TAIL_DEFAULT = 50;
    private static final int TAIL_MAX = 200;
    private static final int INCREMENT_ROUND_LIMIT = 200;
    private static final int POOL_CORE = 2;
    private static final int POOL_MAX = 8;
    private static final int POOL_QUEUE = 64;

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    /**
     * 有界流线程池：每个 SSE 连接占一个轮询线程，拒绝策略 AbortPolicy（池满新连接直接报错，不占调用线程）；
     * 声明为 ExecutorService 仅为测试可注入（生产实例仍是下有界的 ThreadPoolExecutor）
     */
    private final ExecutorService streamExecutor = new ThreadPoolExecutor(
            POOL_CORE, POOL_MAX, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(POOL_QUEUE),
            runnable -> {
                Thread thread = new Thread(runnable, "team-event-stream-" + THREAD_COUNTER.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });

    @Autowired
    private TeamAuditLogService auditLogService;

    @AutoLog(value = "团队协作-事件流订阅")
    @Operation(summary = "团队协作-业务事件流（SSE）",
            description = "入参 bizType 可选（多值逗号分隔过滤）、tail 可选（回放最近 N 条，默认 50，上限 200）。"
                    + "先按时间正序回放存量，随后每 2s 推送 id 游标之后的新事件。")
    @RequiresPermissions("team:task:list")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @Parameter(name = "bizType", description = "业务类型过滤，多值逗号分隔，如 task,bug", example = "task,bug")
            @RequestParam(name = "bizType", required = false) String bizType,
            @Parameter(name = "tail", description = "回放最近 N 条（1-200，默认 50）", example = "50")
            @RequestParam(name = "tail", defaultValue = "50") Integer tail) {
        List<String> bizTypes = parseBizTypes(bizType);
        int tailClamped = clampTail(tail);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicBoolean done = new AtomicBoolean(false);
        emitter.onCompletion(() -> done.set(true));
        emitter.onTimeout(() -> {
            done.set(true);
            emitter.complete();
        });
        streamExecutor.submit(() -> runStream(emitter, bizTypes, tailClamped, done));
        return emitter;
    }

    /**
     * 轮询推送主循环：先正序回放最近 tail 条，之后每 2s 查 id 游标增量并推送，15s 心跳注释防代理断连。
     * 客户端断开/发送失败/超时统一走 finally 收尾发射器。
     */
    void runStream(SseEmitter emitter, List<String> bizTypes, int tail, AtomicBoolean done) {
        try {
            String cursor = null;
            List<TeamAuditLog> replay = auditLogService.listLatestForStream(bizTypes, tail);
            for (TeamAuditLog row : replay) {
                if (done.get()) {
                    return;
                }
                sendEvent(emitter, row);
                cursor = row.getId();
            }
            if (cursor == null) {
                // 空表/空过滤：从当前最大 id 起步，不回放存量
                cursor = auditLogService.findMaxId(bizTypes);
            }
            long lastHeartbeatAt = System.currentTimeMillis();
            while (!done.get()) {
                Thread.sleep(POLL_INTERVAL_MS);
                if (done.get()) {
                    break;
                }
                List<TeamAuditLog> batch = auditLogService.listSinceId(cursor, bizTypes, INCREMENT_ROUND_LIMIT);
                for (TeamAuditLog row : batch) {
                    sendEvent(emitter, row);
                    cursor = row.getId();
                }
                if (System.currentTimeMillis() - lastHeartbeatAt >= HEARTBEAT_INTERVAL_MS) {
                    lastHeartbeatAt = System.currentTimeMillis();
                    // 15s 心跳：SSE 注释帧不占事件流，仅防中间代理因空闲断连
                    emitter.send(SseEmitter.event().comment("ping"));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // 客户端断开等异常：不再重试，交由 finally 收尾
        } finally {
            done.set(true);
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 发射器可能已被容器/超时回调收尾，重复 complete 忽略
            }
        }
    }

    private void sendEvent(SseEmitter emitter, TeamAuditLog row) throws IOException {
        emitter.send(SseEmitter.event().name("audit").data(TeamEventVO.from(row), MediaType.APPLICATION_JSON));
    }

    private List<String> parseBizTypes(String bizType) {
        List<String> result = new ArrayList<>();
        if (bizType == null || bizType.isBlank()) {
            return result;
        }
        for (String item : bizType.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private int clampTail(Integer tail) {
        if (tail == null) {
            return TAIL_DEFAULT;
        }
        return Math.min(Math.max(tail, 1), TAIL_MAX);
    }
}
