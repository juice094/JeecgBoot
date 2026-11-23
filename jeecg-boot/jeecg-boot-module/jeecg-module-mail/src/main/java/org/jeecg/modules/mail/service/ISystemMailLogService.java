package org.jeecg.modules.mail.service;

import org.jeecg.modules.mail.dto.MailLogQueryDTO;
import org.jeecg.modules.mail.dto.MailLogStatDTO;
import org.jeecg.modules.mail.entity.SystemMailLog;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: OA 邮件-发信日志 Service（Phase 1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 状态推进严格走 MailSendStatus.canTransitTo：0→1→2/3，3→1，3→4。
 * 查询口径（接口文档 §3.4 / A-6）：按状态/模板/traceId/收件人 hash/时间过滤，单页 ≤ 100。
 */
public interface ISystemMailLogService extends IService<SystemMailLog> {

    /**
     * 推进状态；非法迁移抛 JeecgBootException。
     */
    void transit(String logId, int targetStatus, String errorCode, String errorMsg);

    /**
     * 标记失败：retryCount 已用尽则直接进死信。
     */
    void markFailed(String logId, String errorCode, String errorMsg);

    /**
     * 重试：仅失败且未超限可重试，成功后回到发送中。
     */
    void retry(String logId);

    /**
     * 条件分页查询；单页上限 100（接口文档 §1 分页约定）。
     */
    IPage<SystemMailLog> pageLogs(MailLogQueryDTO query);

    /**
     * 按状态与模板聚合统计（接口文档 §3.4 GET /system/logs/stat）。
     */
    MailLogStatDTO stat();
}
