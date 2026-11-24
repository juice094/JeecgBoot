package org.jeecg.modules.mail.sync;

import java.time.Duration;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.mail.constant.MailRedisConstants;
import org.jeecg.modules.mail.delivery.MailDeliveryExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * @Description: 同步任务跟踪（B-2；D-20261119-87 ③ Redis 化）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * running 态存 Redis（SET NX EX 语义）：多实例部署下同账号不并发重入，
 * 支撑 GET /accounts/{id}/sync/status 的 running 字段；
 * 任务经模块有界线程池（MailDeliveryExecutor）异步执行，池语义不变。
 *
 * 容灾口径（决策默认值）：Redis 挂 → submit fail-fast 抛业务异常拒绝提交；
 * isRunning 按 false 返回并 warn（状态查询不阻断业务）。绝不降级为本地 ConcurrentHashMap——
 * 降级会让多实例各自放行，重入防护形同虚设。
 */
@Component
public class MailSyncTracker {

    private static final Logger log = LoggerFactory.getLogger(MailSyncTracker.class);

    /** 占位 value，running 语义承载在 key 存在性上 */
    private static final String RUNNING_FLAG = "1";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MailDeliveryExecutor deliveryExecutor;

    @Autowired
    private MailSyncEngine syncEngine;

    /**
     * 提交同步；同账号已在同步中则忽略重复提交（幂等入队）。
     *
     * @return taskId（pilot 口径 = accountId）
     * @throws JeecgBootException Redis 不可用（连接失败/超时），fail-fast 拒绝提交
     */
    public String submit(String accountId) {
        String key = runningKey(accountId);
        Boolean acquired = tryAcquire(key, accountId);
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("[mail] 账号正在同步，忽略重复提交 accountId={}", accountId);
            return accountId;
        }
        deliveryExecutor.submit(() -> {
            try {
                syncEngine.syncAccount(accountId);
            } catch (RuntimeException e) {
                log.warn("[mail] 同步任务失败 accountId={}: {}", accountId, e.getMessage());
            } finally {
                releaseQuietly(key, accountId);
            }
        });
        return accountId;
    }

    /**
     * 是否在同步中。
     *
     * 口径：Redis 挂时按 false 返回并 warn——状态查询不阻断业务，重入防护仍由 submit 的 fail-fast 把守。
     */
    public boolean isRunning(String accountId) {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(runningKey(accountId)));
        } catch (RedisConnectionFailureException e) {
            log.warn("[mail] Redis 不可用，isRunning 按 false 返回 accountId={}", accountId, e);
            return false;
        }
    }

    private Boolean tryAcquire(String key, String accountId) {
        try {
            return stringRedisTemplate.opsForValue().setIfAbsent(key, RUNNING_FLAG,
                    Duration.ofSeconds(MailRedisConstants.SYNC_RUNNING_TTL_SECONDS));
        } catch (RedisConnectionFailureException e) {
            // fail-fast（D-20261119-87 ③）：Redis 挂则拒绝提交，绝不降级为本地并发检查
            throw new JeecgBootException("邮件同步暂不可用（Redis 连接失败），请稍后重试", e);
        }
    }

    private void releaseQuietly(String key, String accountId) {
        try {
            stringRedisTemplate.delete(key);
        } catch (RedisConnectionFailureException e) {
            // 删除失败仅告警：key 带 TTL，最长残留 SYNC_RUNNING_TTL_SECONDS 后自动过期释放
            log.warn("[mail] 释放同步锁失败（依赖 TTL 兜底） accountId={}", accountId, e);
        }
    }

    private static String runningKey(String accountId) {
        return MailRedisConstants.SYNC_RUNNING_KEY_PREFIX + accountId;
    }
}
