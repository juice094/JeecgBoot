package org.jeecg.modules.mail.sync;

import java.util.Date;
import java.util.List;

import org.jeecg.modules.mail.entity.OaMailAccount;
import org.jeecg.modules.mail.mapper.OaMailAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * @Description: 增量同步定时调度（B-2 下半）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 口径：
 * - 默认关闭（jeecg.mail.sync.enabled=false），显式开启后才扫描；cron 可配（默认每 5 分钟）；
 * - 每轮只取到期账号（last_sync_time 为空或早于阈值）限量入队，具体同步仍走
 *   MailSyncTracker（有界池 + 同账号幂等不并发），调度器只负责"到期发现"；
 * - 组件扫描拾取前提：模块注册进 system-start；@Scheduled 生效前提：应用有
 *   @EnableScheduling。两者都属 Phase 2 注册面事项——本模块不自建 @EnableScheduling
 *   配置类（归属问题已报 lead，注册时统一确认，可能 system-start 已有）。
 * - 当前未注册，本类不产生任何副作用。
 */
@Component
public class MailSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(MailSyncScheduler.class);

    /** 单轮最多入队的账号数（防一轮风暴，其余下轮继续） */
    private static final int BATCH_LIMIT = 10;

    /** 距上次同步超过该毫秒数视为到期（默认 5 分钟） */
    private static final long DUE_THRESHOLD_MS = 5 * 60 * 1000L;

    @Value("${jeecg.mail.sync.enabled:false}")
    private boolean syncEnabled;

    @Autowired
    private OaMailAccountMapper accountMapper;

    @Autowired
    private MailSyncTracker syncTracker;

    /**
     * 到期扫描 + 入队；开关关闭时直接返回（每次 tick 一次轻量判断，不查库）。
     */
    @Scheduled(cron = "${jeecg.mail.sync.cron:0 */5 * * * ?}")
    public void scanDueAccounts() {
        if (!syncEnabled) {
            return;
        }
        Date threshold = new Date(System.currentTimeMillis() - DUE_THRESHOLD_MS);
        List<OaMailAccount> due = selectDueAccounts(threshold, BATCH_LIMIT);
        if (due.isEmpty()) {
            return;
        }
        log.info("[mail] 同步调度发现 {} 个到期账号，入队", due.size());
        for (OaMailAccount account : due) {
            try {
                syncTracker.submit(account.getId());
            } catch (RuntimeException e) {
                log.warn("[mail] 同步调度入队失败 accountId={}: {}", account.getId(), e.getMessage());
            }
        }
    }

    /**
     * 到期账号查询（包级可见，供离线单测直测）。
     */
    List<OaMailAccount> selectDueAccounts(Date threshold, int limit) {
        return accountMapper.selectList(new LambdaQueryWrapper<OaMailAccount>()
                .eq(OaMailAccount::getTestStatus, 1)
                .and(wrapper -> wrapper.isNull(OaMailAccount::getLastSyncTime)
                        .or().lt(OaMailAccount::getLastSyncTime, threshold))
                .orderByAsc(OaMailAccount::getLastSyncTime)
                .last("limit " + limit));
    }
}
