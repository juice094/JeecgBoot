package org.jeecg.modules.mail.service.impl;

import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.mail.constant.MailErrorCodes;
import org.jeecg.modules.mail.dto.MailTemplatePreviewDTO;
import org.jeecg.modules.mail.entity.SystemMailTemplate;
import org.jeecg.modules.mail.mapper.SystemMailTemplateMapper;
import org.jeecg.modules.mail.service.ISystemMailTemplateService;
import org.jeecg.modules.mail.support.MailTemplateParams;
import org.jeecg.modules.mail.support.MailTemplateParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: OA 邮件-发信模板 ServiceImpl（Phase 1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 已实现：code 唯一性校验、占位符扫描写入 params（JSON 数组字符串）、
 * 修改重扫、分页查询、预览渲染（缺参拦截）。
 */
@Service
public class SystemMailTemplateServiceImpl extends ServiceImpl<SystemMailTemplateMapper, SystemMailTemplate>
        implements ISystemMailTemplateService {

    @Override
    public SystemMailTemplate getByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<SystemMailTemplate>()
                .eq(SystemMailTemplate::getCode, code.trim())
                .last("limit 1"), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveTemplate(SystemMailTemplate template) {
        if (template == null || StringUtils.isBlank(template.getCode())) {
            throw new JeecgBootException("模板编码不能为空");
        }
        String code = template.getCode().trim();
        if (getByCode(code) != null) {
            throw new JeecgBootException("模板编码已存在: " + code);
        }
        template.setId(null);
        template.setCode(code);
        template.setParams(scanParamsJson(template));
        if (template.getStatus() == null) {
            template.setStatus(1);
        }
        template.setCreateTime(new Date());
        save(template);
        return template.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTemplate(String id, SystemMailTemplate template) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("模板 id 不能为空");
        }
        SystemMailTemplate existing = getById(id);
        if (existing == null) {
            throw new JeecgBootException("模板不存在: " + id);
        }
        if (template == null) {
            throw new JeecgBootException("模板内容不能为空");
        }
        existing.setName(template.getName());
        existing.setAccountId(template.getAccountId());
        existing.setTitle(template.getTitle());
        existing.setContent(template.getContent());
        existing.setStatus(template.getStatus());
        existing.setParams(scanParamsJson(existing));
        existing.setUpdateTime(new Date());
        updateById(existing);
    }

    @Override
    public IPage<SystemMailTemplate> pageTemplates(long pageNo, long pageSize, String code, String name,
            Integer status) {
        Page<SystemMailTemplate> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SystemMailTemplate> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(code)) {
            wrapper.like(SystemMailTemplate::getCode, code.trim());
        }
        if (StringUtils.isNotBlank(name)) {
            wrapper.like(SystemMailTemplate::getName, name.trim());
        }
        if (status != null) {
            wrapper.eq(SystemMailTemplate::getStatus, status);
        }
        wrapper.orderByDesc(SystemMailTemplate::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public MailTemplatePreviewDTO preview(String code, Map<String, Object> params) {
        SystemMailTemplate template = getByCode(code);
        if (template == null) {
            throw new JeecgBootException(MailErrorCodes.MAIL_TEMPLATE_NOT_FOUND + ": " + code);
        }
        List<String> requiredKeys = MailTemplateParams.fromJson(template.getParams());
        List<String> missing = MailTemplateParser.missingKeys(requiredKeys, params);
        if (!missing.isEmpty()) {
            throw new JeecgBootException(MailErrorCodes.MAIL_SEND_TEMPLATE_PARAM_MISS
                    + "：缺少参数 " + String.join(",", missing));
        }
        MailTemplatePreviewDTO result = new MailTemplatePreviewDTO();
        result.setTitle(MailTemplateParser.render(template.getTitle(), params));
        result.setContent(MailTemplateParser.render(template.getContent(), params));
        result.setParams(requiredKeys);
        return result;
    }

    /**
     * 扫描 title 与 content 的 {key}，按出现顺序去重，序列化为 JSON 数组字符串。
     */
    private String scanParamsJson(SystemMailTemplate template) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(MailTemplateParser.extractKeys(template.getTitle()));
        keys.addAll(MailTemplateParser.extractKeys(template.getContent()));
        return MailTemplateParams.toJson(new java.util.ArrayList<>(keys));
    }
}
