package org.jeecg.modules.mail.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.mail.delivery.MailConnectFailedException;
import org.jeecg.modules.mail.dto.ConnectionTestResult;
import org.jeecg.modules.mail.dto.MailAccountBindReqDTO;
import org.jeecg.modules.mail.dto.MailFolderMappingReqDTO;
import org.jeecg.modules.mail.dto.MailSyncStatusDTO;
import org.jeecg.modules.mail.dto.OaMailAccountRespDTO;
import org.jeecg.modules.mail.entity.OaMailFolderState;
import org.jeecg.modules.mail.entity.OaMailMessageIndex;
import org.jeecg.modules.mail.service.IOaMailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
 * @Description: OA 邮件-员工邮箱（B-1 + B-2）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 对齐接口文档 §4.1/§4.2；权限口径 team:mail:mailbox:*（F-09，与系统发信 account 域区分）。
 * 归属规则：登录人 == 绑定归属人（create_by）或 admin；响应不携带密码密文（D-4）。
 * 本模块未注册进 jeecg-system-start，端点当前不会出现在运行时（Phase 2 才注册）。
 */
@Tag(name = "OA 邮件-员工邮箱", description = "员工邮箱绑定/测连/文件夹映射/增量同步/索引查询。统一响应 Result<T>；"
        + "权限 team:mail:mailbox:*（消息列表 team:mail:message:list）；非归属人访问被拒；"
        + "模块未注册，端点暂不生效（Phase 2 注册）。")
@RestController
@RequestMapping("/team/mail")
public class OaMailController {

    @Autowired
    private IOaMailService oaMailService;

    @AutoLog(value = "OA邮件-绑定邮箱")
    @Operation(summary = "绑定本人邮箱（IMAP+SMTP 双测连通过才落库）",
            description = "错误场景：配置不完整或密码为空被拒；同邮箱重复绑定被拒；双测连任一失败不落库——"
                    + "返回 success=false/code=500 结构体（message 含失败侧与分类，result 含 imapOk/smtpOk/"
                    + "imapErrorCode/smtpErrorCode），杜绝“能看不能回”。密码 AES 加密落库（D-76）。")
    @RequiresPermissions("team:mail:mailbox:add")
    @PostMapping("/accounts")
    public Result<String> bind(@RequestBody MailAccountBindReqDTO req) {
        return Result.OK(oaMailService.bind(req));
    }

    @AutoLog(value = "OA邮件-修改邮箱绑定")
    @Operation(summary = "修改绑定配置（触发双测连；密码留空表示不修改该侧密码）",
            description = "错误场景：绑定不存在；非归属人/非 admin 被拒（无权限操作邮箱绑定）；"
                    + "双测连失败不落库，返回同绑定的结构化失败体。")
    @RequiresPermissions("team:mail:mailbox:edit")
    @PutMapping("/accounts/{id}")
    public Result<String> update(@Parameter(description = "绑定 id", required = true) @PathVariable("id") String id,
            @RequestBody MailAccountBindReqDTO req) {
        oaMailService.updateAccount(id, req);
        return Result.OK("ok");
    }

    @AutoLog(value = "OA邮件-邮箱测连")
    @Operation(summary = "手动双测连（用库中已存配置）",
            description = "错误场景：绑定不存在；非归属人/非 admin 被拒。返回双侧连通性与错误分类，不落库。")
    @RequiresPermissions("team:mail:mailbox:test")
    @PostMapping("/accounts/{id}/test")
    public Result<ConnectionTestResult> test(
            @Parameter(description = "绑定 id", required = true) @PathVariable("id") String id) {
        return Result.OK(oaMailService.testAccount(id));
    }

    @Operation(summary = "本人账号列表及同步状态（管理员见全部）",
            description = "错误场景：无 team:mail:mailbox:list 权限返回未授权。响应剥离 IMAP/SMTP 密码。")
    @RequiresPermissions("team:mail:mailbox:list")
    @GetMapping("/accounts")
    public Result<List<OaMailAccountRespDTO>> list() {
        return Result.OK(oaMailService.listAccounts());
    }

    @AutoLog(value = "OA邮件-解绑邮箱")
    @Operation(summary = "解绑（非归属人/非 admin 被拒；索引保留策略属 B-2+，当前直接删除绑定记录）",
            description = "错误场景：绑定不存在；越权被拒。")
    @RequiresPermissions("team:mail:mailbox:delete")
    @DeleteMapping("/accounts/{id}")
    public Result<String> unbind(@Parameter(description = "绑定 id", required = true) @PathVariable("id") String id) {
        oaMailService.unbind(id);
        return Result.OK("ok");
    }

    @Operation(summary = "文件夹列表 + 规范映射结果（含未识别待映射项）",
            description = "错误场景：绑定不存在；越权被拒。canonical 为空表示未识别，需经 mapping 接口人工归一，"
                    + "同步不静默跳过（需求 B-2）。")
    @RequiresPermissions("team:mail:mailbox:folders")
    @GetMapping("/accounts/{id}/folders")
    public Result<List<OaMailFolderState>> folders(
            @Parameter(description = "绑定 id", required = true) @PathVariable("id") String id) {
        return Result.OK(oaMailService.listFolderStates(id));
    }

    @AutoLog(value = "OA邮件-维护文件夹映射")
    @Operation(summary = "维护别名映射（未识别文件夹归入 canonical）",
            description = "错误场景：canonical 非法（可选 Inbox/Sent/Drafts/Trash/Spam/Archive）；越权被拒。"
                    + "自定义映射优先于内置别名表。")
    @RequiresPermissions("team:mail:mailbox:mapping")
    @PutMapping("/accounts/{id}/folders/mapping")
    public Result<String> saveMapping(
            @Parameter(description = "绑定 id", required = true) @PathVariable("id") String id,
            @RequestBody MailFolderMappingReqDTO req) {
        oaMailService.saveFolderMapping(id, req);
        return Result.OK("ok");
    }

    @AutoLog(value = "OA邮件-触发同步")
    @Operation(summary = "手动触发增量同步（异步，基于 last_uid 游标，只写信封索引）",
            description = "错误场景：绑定不存在；越权被拒。同账号同步进行中重复提交幂等忽略。"
                    + "返回 taskId（pilot 口径 = accountId）；失败不落错误到响应，查 sync/status 与日志。")
    @RequiresPermissions("team:mail:mailbox:sync")
    @PostMapping("/accounts/{id}/sync")
    public Result<Map<String, String>> sync(@Parameter(description = "绑定 id", required = true) @PathVariable("id") String id) {
        String taskId = oaMailService.submitSync(id);
        return Result.OK(Collections.singletonMap("taskId", taskId));
    }

    @Operation(summary = "同步状态（lastUid / lastSyncTime / running）",
            description = "错误场景：绑定不存在；越权被拒。folders 为各远端文件夹游标与归一结果。")
    @RequiresPermissions("team:mail:mailbox:sync")
    @GetMapping("/accounts/{id}/sync/status")
    public Result<MailSyncStatusDTO> syncStatus(
            @Parameter(description = "绑定 id", required = true) @PathVariable("id") String id) {
        return Result.OK(oaMailService.syncStatus(id));
    }

    @AutoLog(value = "OA邮件-消息列表")
    @Operation(summary = "消息列表（只查索引表，不触发 IMAP；归属校验已接入）",
            description = "错误场景：绑定不存在；越权被拒；canonicalFolder 非法（可选 Inbox/Sent/Drafts/"
                    + "Trash/Spam/Archive）；keyword 超 100 字符。keyword 仅匹配主题，正文搜索属 B-3。")
    @RequiresPermissions("team:mail:message:list")
    @GetMapping("/messages")
    public Result<IPage<OaMailMessageIndex>> messages(
            @Parameter(description = "绑定 id", required = true) @RequestParam(name = "accountId") String accountId,
            @Parameter(description = "规范文件夹（可选）") @RequestParam(name = "canonicalFolder", required = false) String canonicalFolder,
            @Parameter(description = "主题关键字（可选，LIKE 匹配）") @RequestParam(name = "keyword", required = false) String keyword,
            @Parameter(description = "仅未读（可选，true 时生效）") @RequestParam(name = "unreadOnly", required = false) Boolean unreadOnly,
            @Parameter(description = "页码", example = "1") @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @Parameter(description = "每页条数", example = "10") @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.OK(oaMailService.pageMessages(accountId, canonicalFolder, keyword, unreadOnly, pageNo, pageSize));
    }

    /**
     * 组装接口文档 §4.1 结构化失败响应：success=false、code=500、message 含失败侧与分类，
     * result 携带 imapOk/smtpOk/双侧 errorCode。
     */
    @ExceptionHandler(MailConnectFailedException.class)
    public Result<ConnectionTestResult> handleConnectFailed(MailConnectFailedException e) {
        Result<ConnectionTestResult> failure = new Result<>();
        failure.setSuccess(false);
        failure.setCode(500);
        failure.setMessage(e.getMessage());
        failure.setResult(e.getTestResult());
        return failure;
    }
}
