package org.jeecg.modules.mail.api;

import org.jeecg.modules.mail.dto.MailIndexDTO;
import org.jeecg.modules.mail.dto.MailSearchReqDTO;
import org.jeecg.modules.mail.dto.PageResult;

/**
 * @Description: 契约层-邮件索引只读查询 API（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.2
 *
 * 对齐接口文档 §2。仅查 oa_mail_message_index，绝不触发 IMAP（需求文档 C-4）。
 * 解封前不对外暴露 REST，仅内部只读。
 */
public interface MailIndexQueryApi {

    PageResult<MailIndexDTO> search(MailSearchReqDTO req);

    MailIndexDTO getById(String id);
}
