package org.jeecg.modules.mail.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.mail.dto.MailBatchSendItemResult;
import org.jeecg.modules.mail.dto.MailSendSingleReqDTO;
import org.jeecg.modules.mail.service.ISystemMailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @Description: OA 邮件-系统发信（Phase 1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 对齐接口文档 §3.3 发送；权限口径 team:mail:send(:batch)（F-09，G2 评审点）。
 * 本模块未注册进 jeecg-system-start，端点当前不会出现在运行时（Phase 2 才注册）。
 */
@Tag(name = "OA 邮件-系统发信", description = "系统发信（模板渲染+异步投递+状态机）。统一响应 Result<T>；"
        + "权限 team:mail:send / team:mail:send:batch；模块未注册，端点暂不生效（Phase 2 注册）。")
@RestController
@RequestMapping("/team/mail/system")
public class SystemMailController {

    @Autowired
    private ISystemMailService systemMailService;

    @AutoLog(value = "OA邮件-单发")
    @Operation(summary = "单发（同步落日志 + 异步投递，立即返回 logId）",
            description = "错误场景：模板不存在/已停用（MAIL_TEMPLATE_NOT_FOUND）；占位符缺参"
                    + "（MAIL_SEND_TEMPLATE_PARAM_MISS，message 列缺失 key）；幂等键参数不一致"
                    + "（MAIL_IDEMPOTENT_CONFLICT）；投递失败落日志异步重试，接口本身不抛投递异常。"
                    + "请求体必填 templateCode/to/params，可选 cc/bcc/idempotencyKey（24h 幂等）。")
    @RequiresPermissions("team:mail:send")
    @PostMapping("/send")
    public Result<Map<String, String>> send(@RequestBody MailSendSingleReqDTO req) {
        String logId = systemMailService.sendSingleMail(req);
        return Result.OK(Collections.singletonMap("logId", logId));
    }

    @AutoLog(value = "OA邮件-批量发送")
    @Operation(summary = "批量发送（≤500，逐条独立落日志与成败）",
            description = "错误场景：批次为空或超过 500 条整体拒绝；单条失败不影响其他条，"
                    + "逐条返回 {index, templateCode, success, logId, errorMessage}。"
                    + "请求体为 MailSendSingleReqDTO 数组。")
    @RequiresPermissions("team:mail:send:batch")
    @PostMapping("/send/batch")
    public Result<List<MailBatchSendItemResult>> sendBatch(@RequestBody List<MailSendSingleReqDTO> requests) {
        return Result.OK(systemMailService.sendBatch(requests));
    }
}
