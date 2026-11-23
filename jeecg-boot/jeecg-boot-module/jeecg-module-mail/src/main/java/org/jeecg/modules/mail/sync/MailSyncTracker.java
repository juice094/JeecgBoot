package org.jeecg.modules.mail.sync;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jeecg.modules.mail.delivery.MailDeliveryExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Description: 同步任务跟踪（B-2）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 内存态 running 集合支撑 GET /accounts/{id}/sync/status 的 running 字段；
 * 任务经模块有界线程池（MailDeliveryExecutor）异步执行，同账号不并发重入。
 */
@Component
public class MailSyncTracker {

    private static final Logger log = LoggerFactory.getLogger(MailSyncTracker.class);

    private final Set<String> runningAccountIds = ConcurrentHashMap.newKeySet();

    @Autowired
    private MailDeliveryExecutor deliveryExecutor;

    @Autowired
    private MailSyncEngine syncEngine;

    /**
     * 提交同步；同账号已在同步中则忽略重复提交（幂等入队）。
     *
     * @return taskId（pilot 口径 = accountId）
     */
    public String submit(String accountId) {
        if (!runningAccountIds.add(accountId)) {
            log.info("[mail] 账号正在同步，忽略重复提交 accountId={}", accountId);
            return accountId;
        }
        deliveryExecutor.submit(() -> {
            try {
                syncEngine.syncAccount(accountId);
            } catch (RuntimeException e) {
                log.warn("[mail] 同步任务失败 accountId={}: {}", accountId, e.getMessage());
            } finally {
                runningAccountIds.remove(accountId);
            }
        });
        return accountId;
    }

    public boolean isRunning(String accountId) {
        return runningAccountIds.contains(accountId);
    }
}
