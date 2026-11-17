package org.jeecg.modules.mail.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.mail.dto.MailLogQueryDTO;
import org.jeecg.modules.mail.dto.MailLogStatDTO;
import org.jeecg.modules.mail.entity.SystemMailLog;
import org.jeecg.modules.mail.service.ISystemMailLogService;
import org.jeecg.modules.mail.service.ISystemMailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @Description: OA 邮件-发信日志查询与重试（Phase 1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 对齐接口文档 §3.4；权限口径 team:mail:log:*（F-09，G2 评审点）。
 * 本模块未注册进 jeecg-system-start，端点当前不会出现在运行时（Phase 2 才注册）。
 */
@Tag(name = "OA 邮件-发信日志", description = "系统发信日志（脱敏/查询/重试/统计）。统一响应 Result<T>；"
        + "权限 team:mail:log:*；收件人仅见 toHash 指纹（A-7/D-4）；模块未注册，端点暂不生效（Phase 2 注册）。")
@RestController
@RequestMapping("/team/mail/system/logs")
public class SystemMailLogController {

    @Autowired
    private ISystemMailLogService mailLogService;

    @Autowired
    private ISystemMailService systemMailService;

    @Operation(summary = "日志条件分页查询（status/templateCode/toHash/traceId/时间窗）",
            description = "错误场景：时间格式非法（要求 yyyy-MM-dd HH:mm:ss，message 带字段名）；"
                    + "单页最大 100（超出自动收敛）。按创建时间倒序。")
    @RequiresPermissions("team:mail:log:list")
    @GetMapping
    public Result<IPage<SystemMailLog>> page(MailLogQueryDTO query) {
        return Result.OK(mailLogService.pageLogs(query));
    }

    @Operation(summary = "日志详情（含渲染快照）", description = "错误场景：日志不存在返回 result 为 null。")
    @RequiresPermissions("team:mail:log:queryById")
    @GetMapping("/{id}")
    public Result<SystemMailLog> detail(@Parameter(description = "日志 id", required = true) @PathVariable("id") String id) {
        return Result.OK(mailLogService.getById(id));
    }

    @AutoLog(value = "OA邮件-手动重试")
    @Operation(summary = "手动重试（仅失败且 retry_count < 5）",
            description = "错误场景：日志不存在；当前非失败状态；已达 5 次上限"
                    + "（MAIL_RETRY_LIMIT_EXCEEDED）。成功则状态回发送中并异步投递。")
    @RequiresPermissions("team:mail:log:retry")
    @PostMapping("/{id}/retry")
    public Result<String> retry(@Parameter(description = "日志 id", required = true) @PathVariable("id") String id) {
        systemMailService.retry(id);
        return Result.OK("ok");
    }

    @Operation(summary = "按状态/模板聚合统计", description = "statusCounts 全量按状态计数；templateCounts 按模板降序前 20。")
    @RequiresPermissions("team:mail:log:stat")
    @PostMapping("/stat")
    public Result<MailLogStatDTO> stat() {
        return Result.OK(mailLogService.stat());
    }
}
