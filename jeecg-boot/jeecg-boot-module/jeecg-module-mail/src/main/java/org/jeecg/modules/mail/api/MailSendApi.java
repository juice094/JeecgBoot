package org.jeecg.modules.mail.api;

import org.jeecg.modules.mail.dto.MailSendSingleReqDTO;

/**
 * @Description: 契约层-发信 API（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.1
 *
 * 依赖规则（需求文档 §4.1）：业务模块编译期不得出现 JavaMailSender 或 Jakarta Mail 类型，只允许依赖本接口。
 * 同步落日志并触发异步投递，返回 logId。
 */
public interface MailSendApi {

    /**
     * 单封发信：同步落日志（状态待发送）并触发异步投递。
     *
     * @return 发信日志 id
     */
    String sendSingleMail(MailSendSingleReqDTO req);
}
