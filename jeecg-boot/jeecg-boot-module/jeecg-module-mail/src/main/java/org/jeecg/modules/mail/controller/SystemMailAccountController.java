package org.jeecg.modules.mail.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.mail.dto.ConnectionTestResult;
import org.jeecg.modules.mail.dto.SystemMailAccountRespDTO;
import org.jeecg.modules.mail.entity.SystemMailAccount;
import org.jeecg.modules.mail.service.ISystemMailAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @Description: OA 邮件-系统发信账号管理（Phase 1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 对齐接口文档 §3.1；权限口径 team:mail:account:*（F-09，G2 评审点）。
 * 响应一律经 SystemMailAccountRespDTO 剥离密码（需求文档 D-4）。
 * 本模块未注册进 jeecg-system-start，端点当前不会出现在运行时（Phase 2 才注册）。
 */
@Tag(name = "OA 邮件-系统发信账号", description = "系统发信账号（SMTP 多账号）。统一响应 Result<T>；"
        + "权限 team:mail:account:*；响应不携带密码；模块未注册，端点暂不生效（Phase 2 注册）。")
@RestController
@RequestMapping("/team/mail/system/accounts")
public class SystemMailAccountController {

    @Autowired
    private ISystemMailAccountService accountService;

    @AutoLog(value = "OA邮件-新建发送账号")
    @Operation(summary = "新建发送账号（密码密文落库）",
            description = "错误场景：host/port/username/fromAddress 任一为空被拒；密码为空被拒。"
                    + "请求体 password 为明文，服务端 AES 加密后落库（D-76）。返回账号 id。")
    @RequiresPermissions("team:mail:account:add")
    @PostMapping
    public Result<String> create(@RequestBody SystemMailAccount account) {
        return Result.OK(accountService.createAccount(account));
    }

    @AutoLog(value = "OA邮件-修改发送账号")
    @Operation(summary = "修改发送账号（密码留空表示不修改）",
            description = "错误场景：账号不存在；必填字段缺失。密码非空则重新加密覆盖并清除 SMTP 连接缓存。"
                    + "被模板引用的账号仅可停用，删除见 DELETE。")
    @RequiresPermissions("team:mail:account:edit")
    @PutMapping("/{id}")
    public Result<String> update(@Parameter(description = "账号 id", required = true) @PathVariable("id") String id,
            @RequestBody SystemMailAccount account) {
        accountService.updateAccount(id, account);
        return Result.OK("ok");
    }

    @Operation(summary = "发送账号分页列表（keyword 模糊匹配 host/username）",
            description = "错误场景：无 team:mail:account:list 权限返回未授权。响应剥离密码字段。")
    @RequiresPermissions("team:mail:account:list")
    @GetMapping
    public Result<IPage<SystemMailAccountRespDTO>> page(
            @Parameter(description = "页码", example = "1") @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @Parameter(description = "每页条数", example = "10") @RequestParam(name = "pageSize", defaultValue = "10") long pageSize,
            @Parameter(description = "状态：1 启用 / 0 停用") @RequestParam(name = "status", required = false) Integer status,
            @Parameter(description = "关键字（模糊匹配 host/username）") @RequestParam(name = "keyword", required = false) String keyword) {
        IPage<SystemMailAccount> page = accountService.pageAccounts(pageNo, pageSize, status, keyword);
        return Result.OK(page.convert(SystemMailAccountRespDTO::from));
    }

    @AutoLog(value = "OA邮件-测试发送账号")
    @Operation(summary = "测试 SMTP 连通（真实握手，不发信）",
            description = "错误场景：账号不存在；SMTP 认证失败 smtpOk=false/errorCode=smtp_auth_failed；"
                    + "网络不可达 smtp_connection；超时 smtp_timeout。响应 ConnectionTestResult 含 latencyMs。")
    @RequiresPermissions("team:mail:account:test")
    @PostMapping("/{id}/test")
    public Result<ConnectionTestResult> test(@Parameter(description = "账号 id", required = true) @PathVariable("id") String id) {
        return Result.OK(accountService.testConnection(id));
    }

    @AutoLog(value = "OA邮件-删除发送账号")
    @Operation(summary = "删除发送账号（被模板引用则拒绝）",
            description = "错误场景：账号不存在；被 N 个模板引用时返回拒绝及引用数（需先改模板或停用）。")
    @RequiresPermissions("team:mail:account:delete")
    @DeleteMapping("/{id}")
    public Result<String> delete(@Parameter(description = "账号 id", required = true) @PathVariable("id") String id) {
        accountService.deleteAccount(id);
        return Result.OK("ok");
    }
}
