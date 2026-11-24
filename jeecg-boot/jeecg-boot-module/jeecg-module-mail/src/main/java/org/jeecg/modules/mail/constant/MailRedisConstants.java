package org.jeecg.modules.mail.constant;

/**
 * @Description: 邮件模块 Redis key 口径常量（D-20261119-87 ①③）
 * @Author: mail-dev
 * @Date: 2026-11-23
 *
 * 集中维护 key 前缀与 TTL，禁散写魔法值；两类占位均为「SET/INCR + TTL」语义，
 * Redis 不可用一律 fail-fast（见各落点注释），不降级为本地态。
 */
public final class MailRedisConstants {

    /** 员工写信幂等占位 key 前缀；完整 key = 前缀 + accountId + ":" + 幂等键摘要 */
    public static final String SEND_IDEMPOTENT_KEY_PREFIX = "mail:idem:send:";

    /** 幂等占位 TTL：24 小时（D-20261119-87 ① 决策默认值） */
    public static final long SEND_IDEMPOTENT_TTL_SECONDS = 24 * 60 * 60;

    /** 同步 running 占位 key 前缀；完整 key = 前缀 + accountId */
    public static final String SYNC_RUNNING_KEY_PREFIX = "mail:sync:running:";

    /**
     * 同步 running 占位 TTL：正常路径任务结束即删 key，TTL 仅兜底进程崩溃残留
     * （防多实例下永久占锁拒绝重入，D-20261119-87 ③）
     */
    public static final long SYNC_RUNNING_TTL_SECONDS = 30 * 60;

    private MailRedisConstants() {
    }
}
