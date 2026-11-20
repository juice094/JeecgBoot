package org.jeecg.modules.quartz.job;

import org.jeecg.modules.system.service.ISysPermissionGrantService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.extern.slf4j.Slf4j;

/**
 * @Description: 权限确权-授权到期扫描
 * @Author: juice094
 * @Date: 2026-11-19
 * @Version: V1.0
 * <p>
 * 两件事：
 * 1) 把已过 valid_until 的 ACTIVE 授权落库为 EXPIRED（鉴权侧到期判定在读路径，不依赖本任务）；
 * 2) 对 valid_until 落在未来 N 天内的授权发出 grant.expiring（每个授权仅一次）。
 * 参数 parameter = 提前天数（默认 3）。
 * 注册脚本：V3.9.6_13__permission_grant_expire_job.sql
 */
@Slf4j
@DisallowConcurrentExecution
public class PermissionGrantExpireJob implements Job {

    private static final int DEFAULT_AHEAD_DAYS = 3;
    private static final int BATCH_SIZE = 500;

    @Autowired
    private ISysPermissionGrantService permissionGrantService;

    @Override
    public void execute(JobExecutionContext context) {
        int aheadDays = parseAheadDays(context.getMergedJobDataMap().getString("parameter"));
        try {
            int expired = permissionGrantService.expireDue(BATCH_SIZE);
            int notified = permissionGrantService.notifyExpiring(aheadDays);
            if (expired > 0 || notified > 0) {
                log.info("权限确权到期扫描完成：过期 {} 条，提醒 {} 条", expired, notified);
            }
        } catch (Exception e) {
            log.error("权限确权到期扫描失败: {}", e.getMessage(), e);
        }
    }

    private int parseAheadDays(String parameter) {
        if (parameter == null || parameter.trim().isEmpty()) {
            return DEFAULT_AHEAD_DAYS;
        }
        try {
            return Integer.parseInt(parameter.trim());
        } catch (NumberFormatException e) {
            log.warn("到期提醒参数非数字，回退默认 {} 天: {}", DEFAULT_AHEAD_DAYS, parameter);
            return DEFAULT_AHEAD_DAYS;
        }
    }
}
