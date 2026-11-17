package org.jeecg.modules.mail.service.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.mail.constant.MailErrorCodes;
import org.jeecg.modules.mail.dto.MailLogQueryDTO;
import org.jeecg.modules.mail.dto.MailLogStatDTO;
import org.jeecg.modules.mail.entity.SystemMailLog;
import org.jeecg.modules.mail.enums.MailSendStatus;
import org.jeecg.modules.mail.mapper.SystemMailLogMapper;
import org.jeecg.modules.mail.service.ISystemMailLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import cn.hutool.core.date.DateUtil;

/**
 * @Description: OA 邮件-发信日志 ServiceImpl（Phase 1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 */
@Service
public class SystemMailLogServiceImpl extends ServiceImpl<SystemMailLogMapper, SystemMailLog>
        implements ISystemMailLogService {

    /** 死信前最大重试次数（需求文档 A-5） */
    private static final int MAX_RETRY = 5;

    /** 单页最大条数（接口文档 §1 分页约定） */
    private static final long MAX_PAGE_SIZE = 100;

    /** 模板聚合统计的返回条数上限 */
    private static final int TEMPLATE_STAT_LIMIT = 20;

    /** 时间参数格式（接口文档 §1） */
    private static final String TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transit(String logId, int targetStatus, String errorCode, String errorMsg) {
        SystemMailLog mailLog = getById(logId);
        if (mailLog == null) {
            throw new JeecgBootException("发信日志不存在: " + logId);
        }
        MailSendStatus from = MailSendStatus.fromCode(mailLog.getSendStatus());
        MailSendStatus target = MailSendStatus.fromCode(targetStatus);
        if (from == null || target == null) {
            throw new JeecgBootException("发信状态数据异常: logId=" + logId);
        }
        if (!from.canTransitTo(target)) {
            throw new JeecgBootException("非法发信状态流转: " + from.getCode() + " to " + target.getCode()
                    + "（logId=" + logId + "）");
        }
        mailLog.setSendStatus(target.getCode());
        if (errorCode != null) {
            mailLog.setErrorCode(errorCode);
        }
        if (errorMsg != null) {
            mailLog.setErrorMsg(errorMsg);
        }
        mailLog.setUpdateTime(new Date());
        updateById(mailLog);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFailed(String logId, String errorCode, String errorMsg) {
        SystemMailLog mailLog = getById(logId);
        if (mailLog == null) {
            throw new JeecgBootException("发信日志不存在: " + logId);
        }
        int retried = mailLog.getRetryCount() == null ? 0 : mailLog.getRetryCount();
        if (retried >= MAX_RETRY) {
            transit(logId, MailSendStatus.DEAD.getCode(), MailErrorCodes.MAIL_RETRY_LIMIT_EXCEEDED,
                    "重试超过 " + MAX_RETRY + " 次，进入死信；最后一次错误: " + errorMsg);
            return;
        }
        transit(logId, MailSendStatus.FAILED.getCode(), errorCode, errorMsg);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retry(String logId) {
        SystemMailLog mailLog = getById(logId);
        if (mailLog == null) {
            throw new JeecgBootException("发信日志不存在: " + logId);
        }
        MailSendStatus current = MailSendStatus.fromCode(mailLog.getSendStatus());
        if (current != MailSendStatus.FAILED) {
            throw new JeecgBootException("仅失败状态可重试，当前: "
                    + (current == null ? mailLog.getSendStatus() : current.getLabel()));
        }
        int retried = mailLog.getRetryCount() == null ? 0 : mailLog.getRetryCount();
        if (retried >= MAX_RETRY) {
            throw new JeecgBootException(MailErrorCodes.MAIL_RETRY_LIMIT_EXCEEDED + "：已达 " + MAX_RETRY + " 次上限");
        }
        mailLog.setRetryCount(retried + 1);
        mailLog.setSendStatus(MailSendStatus.SENDING.getCode());
        mailLog.setUpdateTime(new Date());
        updateById(mailLog);
    }

    @Override
    public IPage<SystemMailLog> pageLogs(MailLogQueryDTO query) {
        if (query == null) {
            throw new JeecgBootException("查询条件不能为空");
        }
        long pageSize = Math.min(query.getPageSize() <= 0 ? 10 : query.getPageSize(), MAX_PAGE_SIZE);
        long pageNo = query.getPageNo() <= 0 ? 1 : query.getPageNo();
        Page<SystemMailLog> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SystemMailLog> wrapper = new LambdaQueryWrapper<>();
        if (query.getStatus() != null) {
            wrapper.eq(SystemMailLog::getSendStatus, query.getStatus());
        }
        if (StringUtils.isNotBlank(query.getTemplateCode())) {
            wrapper.eq(SystemMailLog::getTemplateCode, query.getTemplateCode().trim());
        }
        if (StringUtils.isNotBlank(query.getToHash())) {
            wrapper.eq(SystemMailLog::getToHash, query.getToHash().trim());
        }
        if (StringUtils.isNotBlank(query.getTraceId())) {
            wrapper.eq(SystemMailLog::getTraceId, query.getTraceId().trim());
        }
        Date begin = parseTime(query.getBeginTime(), "beginTime");
        if (begin != null) {
            wrapper.ge(SystemMailLog::getCreateTime, begin);
        }
        Date end = parseTime(query.getEndTime(), "endTime");
        if (end != null) {
            wrapper.le(SystemMailLog::getCreateTime, end);
        }
        wrapper.orderByDesc(SystemMailLog::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    public MailLogStatDTO stat() {
        MailLogStatDTO result = new MailLogStatDTO();
        QueryWrapper<SystemMailLog> statusWrapper = new QueryWrapper<>();
        statusWrapper.select("send_status", "count(*) AS cnt").groupBy("send_status");
        List<Map<String, Object>> statusRows = baseMapper.selectMaps(statusWrapper);
        for (Map<String, Object> row : statusRows) {
            Object status = row.get("send_status");
            Object cnt = row.get("cnt");
            if (status != null && cnt != null) {
                result.getStatusCounts().put(Integer.valueOf(status.toString()), Long.valueOf(cnt.toString()));
            }
        }
        QueryWrapper<SystemMailLog> templateWrapper = new QueryWrapper<>();
        templateWrapper.select("template_code", "count(*) AS cnt")
                .groupBy("template_code")
                .orderByDesc("cnt")
                .last("limit " + TEMPLATE_STAT_LIMIT);
        List<Map<String, Object>> templateRows = baseMapper.selectMaps(templateWrapper);
        for (Map<String, Object> row : templateRows) {
            Object code = row.get("template_code");
            Object cnt = row.get("cnt");
            if (code != null && cnt != null) {
                result.getTemplateCounts().put(code.toString(), Long.valueOf(cnt.toString()));
            }
        }
        return result;
    }

    /**
     * 解析时间参数；格式错误抛带字段名的异常（信任边界输入校验）。
     */
    private Date parseTime(String raw, String fieldName) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return DateUtil.parse(raw.trim(), TIME_PATTERN);
        } catch (Exception e) {
            throw new JeecgBootException("时间格式错误（要求 " + TIME_PATTERN + "）: " + fieldName + "=" + raw);
        }
    }
}
