package org.jeecg.modules.mail.service;

import java.util.List;

import org.jeecg.modules.mail.api.MailSendApi;
import org.jeecg.modules.mail.dto.MailBatchSendItemResult;
import org.jeecg.modules.mail.dto.MailSendSingleReqDTO;
import org.jeecg.modules.mail.entity.SystemMailLog;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: OA 邮件-系统发信 Service（Phase 1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 同时实现契约层 MailSendApi，业务模块只依赖接口即可（需求文档 A-3）。
 */
public interface ISystemMailService extends IService<SystemMailLog>, MailSendApi {

    /**
     * 手动重试一条失败日志（仅 FAILED 且未超上限）。
     */
    void retry(String logId);

    /**
     * 批量发送（接口文档 §3.3）：单次 ≤ 500，逐条独立落日志，
     * 单条失败不影响其他条，逐条返回成败。
     */
    List<MailBatchSendItemResult> sendBatch(List<MailSendSingleReqDTO> requests);
}
