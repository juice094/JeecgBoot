package org.jeecg.modules.mail.service;

import java.util.Map;

import org.jeecg.modules.mail.dto.MailTemplatePreviewDTO;
import org.jeecg.modules.mail.entity.SystemMailTemplate;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: OA 邮件-发信模板 Service（Phase 1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 对齐接口文档 §3.2 模板管理：保存时自动扫描 {key} 写入 params（需求文档 A-2）。
 */
public interface ISystemMailTemplateService extends IService<SystemMailTemplate> {

    /**
     * 按模板编码查询；不存在返回 null，由调用方决定错误口径。
     */
    SystemMailTemplate getByCode(String code);

    /**
     * 新建模板：扫描 title/content 生成 params 后落库，返回模板 id。
     */
    String saveTemplate(SystemMailTemplate template);

    /**
     * 修改模板：code 不可变更；重新扫描占位符并覆盖 params。
     */
    void updateTemplate(String id, SystemMailTemplate template);

    /**
     * 分页查询（code/name 模糊，status 精确），按创建时间倒序。
     */
    IPage<SystemMailTemplate> pageTemplates(long pageNo, long pageSize, String code, String name, Integer status);

    /**
     * 预览渲染：缺参抛 MAIL_SEND_TEMPLATE_PARAM_MISS 并列出缺失 key；不缺参返回渲染结果。
     */
    MailTemplatePreviewDTO preview(String code, Map<String, Object> params);
}
