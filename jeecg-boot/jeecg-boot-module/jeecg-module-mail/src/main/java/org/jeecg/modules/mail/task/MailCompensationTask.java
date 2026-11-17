package org.jeecg.modules.mail.task;

import java.util.Date;
import java.util.List;

import org.jeecg.modules.mail.entity.SystemMailLog;
import org.jeecg.modules.mail.enums.MailSendStatus;
import org.jeecg.modules.mail.service.ISystemMailLogService;
import org.jeecg.modules.mail.service.ISystemMailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * @Description: OA 邮件-投递补偿扫描（骨架 V0.3）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 *
 * 口径（接口文档 §6）：每 5 分钟扫描 send_status IN (0,1) 且 update_time < now()-5min
 * 且 retry_count < 5 的记录重新入队。
 *
 * 状态机注意：PENDING(0) 只能迁到 SENDING(1)，不能直接到 FAILED(3)；
 * 因此 PENDING 卡住记录先推进到 SENDING，再按失败处理。
 *
 * 启用条件（两条都不满足时本类不产生任何副作用）：
 * 1. 模块已注册进 jeecg-system-start —— 当前未注册；
 * 2. 应用开启 @EnableScheduling。
 */
@Component
public class MailCompensationTask {

    private static final Logger log = LoggerFactory.getLogger(MailCompensationTask.class);

    private static final long STUCK_MINUTES = 5L;
    private static final int MAX_RETRY = 5;

    @Autowired
    private ISystemMailLogService mailLogService;

    @Autowired
    private ISystemMailService systemMailService;

    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void scanStuckLogs() {
        Date threshold = new Date(System.currentTimeMillis() - STUCK_MINUTES * 60 * 1000L);
        List<SystemMailLog> stuck = mailLogService.list(new LambdaQueryWrapper<SystemMailLog>()
                .in(SystemMailLog::getSendStatus, MailSendStatus.PENDING.getCode(), MailSendStatus.SENDING.getCode())
                .lt(SystemMailLog::getUpdateTime, threshold)
                .lt(SystemMailLog::getRetryCount, MAX_RETRY)
                .orderByAsc(SystemMailLog::getCreateTime)
                .last("limit 100"));
        if (stuck.isEmpty()) {
            return;
        }
        log.warn("[mail] 补偿扫描发现 {} 条卡住日志，尝试重新入队", stuck.size());
        for (SystemMailLog item : stuck) {
            try {
                recoverOne(item);
            } catch (RuntimeException e) {
                log.warn("[mail] 补偿重入队失败 logId={}", item.getId(), e);
            }
        }
    }

    private void recoverOne(SystemMailLog item) {
        MailSendStatus status = MailSendStatus.fromCode(item.getSendStatus());
        if (status == MailSendStatus.PENDING) {
            // 合法路径 0 → 1
            mailLogService.transit(item.getId(), MailSendStatus.SENDING.getCode(), null, null);
        }
        // 合法路径 1 → 3
        mailLogService.markFailed(item.getId(), "unknown", "补偿扫描：投递超时未完成");
        // 合法路径 3 → 1，并将 retryCount + 1
        systemMailService.retry(item.getId());
    }
}
