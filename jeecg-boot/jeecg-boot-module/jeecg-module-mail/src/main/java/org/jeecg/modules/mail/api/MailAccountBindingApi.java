package org.jeecg.modules.mail.api;

import org.jeecg.modules.mail.dto.ConnectionTestResult;
import org.jeecg.modules.mail.dto.MailAccountBindReqDTO;
import org.jeecg.modules.mail.dto.MailServerConfig;

/**
 * @Description: 契约层-邮箱绑定 API（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.2
 *
 * 对齐接口文档 §2。本层不暴露 Jakarta Mail / IMAP 类型。
 * id 口径说明：接口文档草案为 Long，JeecgEntity 主键为 String，骨架统一用 String，立项时对齐文档。
 */
public interface MailAccountBindingApi {

    /**
     * 绑定本人邮箱：IMAP 与 SMTP 双测连通过才落库。
     */
    String bind(MailAccountBindReqDTO req);

    /**
     * 解绑。
     */
    void unbind(String accountId);

    /**
     * 仅测连，不落库。
     */
    ConnectionTestResult testConnection(MailServerConfig cfg);
}
