package org.jeecg.modules.mail.delivery;

import org.jeecg.modules.mail.dto.ConnectionTestResult;

/**
 * @Description: 双测连未通过信号（B-1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 绑定/修改时 IMAP 或 SMTP 任一握手失败由 Service 抛出本异常；
 * REST 层捕获后按接口文档 §4.1 组装结构化失败响应（含 imapOk/smtpOk/双侧 errorCode）。
 */
public class MailConnectFailedException extends RuntimeException {

    private final transient ConnectionTestResult testResult;

    public MailConnectFailedException(ConnectionTestResult testResult) {
        super(buildMessage(testResult));
        this.testResult = testResult;
    }

    public ConnectionTestResult getTestResult() {
        return testResult;
    }

    /**
     * 失败侧文案：单侧指明侧别，双侧合并说明。
     */
    private static String buildMessage(ConnectionTestResult result) {
        if (result == null) {
            return "邮箱测连失败";
        }
        boolean imapFailed = !result.isImapOk();
        boolean smtpFailed = !result.isSmtpOk();
        if (imapFailed && smtpFailed) {
            return "IMAP 与 SMTP 连接均失败(" + result.getImapErrorCode() + "/" + result.getSmtpErrorCode() + ")";
        }
        if (imapFailed) {
            return "IMAP 连接失败(" + result.getImapErrorCode() + ")";
        }
        return "SMTP 连接失败(" + result.getSmtpErrorCode() + ")";
    }
}
