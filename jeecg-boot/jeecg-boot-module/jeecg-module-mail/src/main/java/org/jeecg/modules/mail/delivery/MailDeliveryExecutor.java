package org.jeecg.modules.mail.delivery;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * @Description: 邮件异步投递线程池（骨架 V0.3）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 *
 * 口径（需求文档 §4.1 注 / §6 / 接口文档 §6）：pilot 无 MQ，用应用内有界线程池；
 * 禁止 Executors；队列有界 + 拒绝策略，避免 OOM。
 *
 * 骨架边界：没有接入补偿扫描与死信推进，这两项在 SystemMailServiceImpl 里留 TODO。
 */
@Component
public class MailDeliveryExecutor {

    private static final Logger log = LoggerFactory.getLogger(MailDeliveryExecutor.class);

    private static final int CORE_SIZE = 2;
    private static final int MAX_SIZE = 4;
    private static final int QUEUE_CAPACITY = 200;
    private static final long KEEP_ALIVE_SECONDS = 60L;

    private final ThreadPoolExecutor executor;

    public MailDeliveryExecutor() {
        AtomicInteger seq = new AtomicInteger(1);
        this.executor = new ThreadPoolExecutor(
                CORE_SIZE,
                MAX_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                r -> {
                    Thread t = new Thread(r, "mail-delivery-" + seq.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 提交投递任务；队列满时抛 RejectedExecutionException，由调用方标记失败待重试。
     */
    public void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            log.warn("[mail] 投递队列已满，任务被拒绝");
            throw e;
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
