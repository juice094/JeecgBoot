package org.jeecg.modules.mail.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.mail.dto.MailTemplatePreviewDTO;
import org.jeecg.modules.mail.dto.MailTemplatePreviewReqDTO;
import org.jeecg.modules.mail.entity.SystemMailTemplate;
import org.jeecg.modules.mail.service.ISystemMailTemplateService;
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
 * @Description: OA 邮件-发信模板管理（Phase 1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 对齐接口文档 §3.2；权限口径 team:mail:template:*（F-09，G2 评审点）。
 * 保存时自动扫描 {key} 写入 params（需求文档 A-2）。
 * 本模块未注册进 jeecg-system-start，端点当前不会出现在运行时（Phase 2 才注册）。
 */
@Tag(name = "OA 邮件-发信模板", description = "系统发信模板（占位符扫描/渲染预览）。统一响应 Result<T>；"
        + "权限 team:mail:template:*；code 全局唯一且不可变更；模块未注册，端点暂不生效（Phase 2 注册）。")
@RestController
@RequestMapping("/team/mail/system/templates")
public class SystemMailTemplateController {

    @Autowired
    private ISystemMailTemplateService templateService;

    @AutoLog(value = "OA邮件-新建发信模板")
    @Operation(summary = "新建模板（自动解析 {key} 写入 params）",
            description = "错误场景：code 为空或已存在被拒。title/content 中的 {简单标识符} 占位符被扫描为"
                    + " params（JSON 数组），发送时缺参即拒发（MAIL_SEND_TEMPLATE_PARAM_MISS）。返回模板 id 与 params。")
    @RequiresPermissions("team:mail:template:add")
    @PostMapping
    public Result<String> create(@RequestBody SystemMailTemplate template) {
        return Result.OK(templateService.saveTemplate(template));
    }

    @AutoLog(value = "OA邮件-修改发信模板")
    @Operation(summary = "修改模板（code 不可变更，占位符重新扫描）",
            description = "错误场景：模板不存在。请求体 name/accountId/title/content/status 覆盖更新，"
                    + " params 按新内容重新生成；需改 code 请走新建+停用旧模板。")
    @RequiresPermissions("team:mail:template:edit")
    @PutMapping("/{id}")
    public Result<String> update(@Parameter(description = "模板 id", required = true) @PathVariable("id") String id,
            @RequestBody SystemMailTemplate template) {
        templateService.updateTemplate(id, template);
        return Result.OK("ok");
    }

    @Operation(summary = "模板分页查询（code/name 模糊，status 精确）",
            description = "错误场景：无 team:mail:template:list 权限返回未授权。")
    @RequiresPermissions("team:mail:template:list")
    @GetMapping
    public Result<IPage<SystemMailTemplate>> page(
            @Parameter(description = "页码", example = "1") @RequestParam(name = "pageNo", defaultValue = "1") long pageNo,
            @Parameter(description = "每页条数", example = "10") @RequestParam(name = "pageSize", defaultValue = "10") long pageSize,
            @Parameter(description = "模板编码（模糊）") @RequestParam(name = "code", required = false) String code,
            @Parameter(description = "模板名称（模糊）") @RequestParam(name = "name", required = false) String name,
            @Parameter(description = "状态：1 启用 / 0 停用") @RequestParam(name = "status", required = false) Integer status) {
        return Result.OK(templateService.pageTemplates(pageNo, pageSize, code, name, status));
    }

    @Operation(summary = "按 code 查模板详情", description = "错误场景：模板不存在返回 result 为 null。")
    @RequiresPermissions("team:mail:template:list")
    @GetMapping("/{code}")
    public Result<SystemMailTemplate> detailByCode(
            @Parameter(description = "模板编码", required = true) @PathVariable("code") String code) {
        return Result.OK(templateService.getByCode(code));
    }

    @Operation(summary = "预览渲染（缺参返回 MAIL_SEND_TEMPLATE_PARAM_MISS 并列出缺失 key）",
            description = "错误场景：模板不存在（MAIL_TEMPLATE_NOT_FOUND）；参数缺失（MAIL_SEND_TEMPLATE_PARAM_MISS，"
                    + "message 列缺失 key，不产生半截渲染）。请求体 params 为渲染参数；成功返回渲染后标题/正文与参数清单。")
    @RequiresPermissions("team:mail:template:preview")
    @PostMapping("/{code}/preview")
    public Result<MailTemplatePreviewDTO> preview(
            @Parameter(description = "模板编码", required = true) @PathVariable("code") String code,
            @RequestBody(required = false) MailTemplatePreviewReqDTO req) {
        return Result.OK(templateService.preview(code, req == null ? null : req.getParams()));
    }

    @AutoLog(value = "OA邮件-删除发信模板")
    @Operation(summary = "删除模板",
            description = "错误场景：模板不存在。引用该模板的 system_mail_log 快照保留，删除不影响历史日志。")
    @RequiresPermissions("team:mail:template:delete")
    @DeleteMapping("/{id}")
    public Result<String> delete(@Parameter(description = "模板 id", required = true) @PathVariable("id") String id) {
        templateService.removeById(id);
        return Result.OK("ok");
    }
}
