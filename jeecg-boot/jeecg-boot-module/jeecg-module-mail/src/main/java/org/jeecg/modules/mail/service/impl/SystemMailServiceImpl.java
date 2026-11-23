package org.jeecg.modules.mail.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.mail.constant.MailErrorCodes;
import org.jeecg.modules.mail.constant.MailTraceConstants;
import org.jeecg.modules.mail.delivery.MailDelivery;
import org.jeecg.modules.mail.delivery.MailDeliveryException;
import org.jeecg.modules.mail.delivery.MailDeliveryExecutor;
import org.jeecg.modules.mail.dto.MailBatchSendItemResult;
import org.jeecg.modules.mail.dto.MailSendSingleReqDTO;
import org.jeecg.modules.mail.entity.SystemMailLog;
import org.jeecg.modules.mail.entity.SystemMailTemplate;
import org.jeecg.modules.mail.enums.MailSendStatus;
import org.jeecg.modules.mail.mapper.SystemMailLogMapper;
import org.jeecg.modules.mail.service.ISystemMailLogService;
import org.jeecg.modules.mail.service.ISystemMailService;
import org.jeecg.modules.mail.service.ISystemMailTemplateService;
import org.jeecg.modules.mail.support.MailDigests;
import org.jeecg.modules.mail.support.MailTemplateParams;
import org.jeecg.modules.mail.support.MailTemplateParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: OA 邮件-系统发信 ServiceImpl（Phase 1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 已实现（一期 A-2/A-3/A-4 主体 + D-1 异步 traceId 透传）：
 * - 模板存在性与启用状态校验（MAIL_TEMPLATE_NOT_FOUND）
 * - 占位符缺失校验（MAIL_SEND_TEMPLATE_PARAM_MISS，列出缺失 key）
 * - 幂等键查重：同 key 且渲染结果一致返回原 logId；不一致抛 MAIL_IDEMPOTENT_CONFLICT
 * - 同步落日志（PENDING，含 to_hash 指纹与 traceId）后提交异步投递，接口立即返回 logId
 * - 投递结果推进状态机，失败写结构化错误码
 * - 异步线程 MDC 透传 traceId，finally 清理（需求文档 D-1）
 * - 批量发送（≤500，逐条独立成败）
 *
 * 未实现（明确标注，不假装可用）：
 * - 指数退避：仅支持人工重试与补偿扫描，无自动退避调度
 *
 * 事务边界：sendSingleMail 刻意不加 @Transactional——日志必须先提交，异步线程才读得到。
 */
@Service
public class SystemMailServiceImpl extends ServiceImpl<SystemMailLogMapper, SystemMailLog> implements ISystemMailService {

    private static final Logger log = LoggerFactory.getLogger(SystemMailServiceImpl.class);

    /** 批量发送单次上限（接口文档 §3.3） */
    private static final int MAX_BATCH_SIZE = 500;

    @Autowired
    private ISystemMailTemplateService templateService;

    @Autowired
    private ISystemMailLogService mailLogService;

    @Autowired
    private MailDelivery mailDelivery;

    @Autowired
    private MailDeliveryExecutor deliveryExecutor;

    @Override
    public String sendSingleMail(MailSendSingleReqDTO req) {
        if (req == null || StringUtils.isBlank(req.getTemplateCode())) {
            throw new JeecgBootException("模板编码不能为空");
        }
        if (StringUtils.isBlank(req.getTo())) {
            throw new JeecgBootException("收件人不能为空");
        }

        SystemMailTemplate template = templateService.getByCode(req.getTemplateCode());
        if (template == null) {
            throw new JeecgBootException(MailErrorCodes.MAIL_TEMPLATE_NOT_FOUND + ": " + req.getTemplateCode());
        }
        if (template.getStatus() != null && template.getStatus() == 0) {
            throw new JeecgBootException(MailErrorCodes.MAIL_TEMPLATE_NOT_FOUND + ": 模板已停用 " + req.getTemplateCode());
        }

        Map<String, Object> params = req.getParams();
        List<String> requiredKeys = MailTemplateParams.fromJson(template.getParams());
        List<String> missing = MailTemplateParser.missingKeys(requiredKeys, params);
        if (!missing.isEmpty()) {
            throw new JeecgBootException(MailErrorCodes.MAIL_SEND_TEMPLATE_PARAM_MISS
                    + "：缺少参数 " + String.join(",", missing));
        }

        String renderedTitle = MailTemplateParser.render(template.getTitle(), params);
        String renderedContent = MailTemplateParser.render(template.getContent(), params);

        if (StringUtils.isNotBlank(req.getIdempotencyKey())) {
            SystemMailLog existing = getOne(new LambdaQueryWrapper<SystemMailLog>()
                    .eq(SystemMailLog::getIdempotencyKey, req.getIdempotencyKey())
                    .last("limit 1"), false);
            if (existing != null) {
                if (StringUtils.equals(existing.getTemplateContent(), renderedContent)) {
                    return existing.getId();
                }
                throw new JeecgBootException(MailErrorCodes.MAIL_IDEMPOTENT_CONFLICT
                        + "：幂等键 " + req.getIdempotencyKey() + " 已用于不同参数");
            }
        }

        SystemMailLog mailLog = new SystemMailLog();
        mailLog.setAccountId(template.getAccountId());
        mailLog.setTemplateCode(req.getTemplateCode());
        mailLog.setToMails(req.getTo());
        // 收件人指纹（SHA-256 前 8 位）：查询与统计只用指纹，避免按完整地址检索（A-7/D-4）
        mailLog.setToHash(MailDigests.toHash8(req.getTo()));
        mailLog.setCcMails(req.getCc());
        mailLog.setBccMails(req.getBcc());
        mailLog.setTemplateTitle(renderedTitle);
        mailLog.setTemplateContent(renderedContent);
        mailLog.setSendStatus(MailSendStatus.PENDING.getCode());
        mailLog.setRetryCount(0);
        mailLog.setIdempotencyKey(req.getIdempotencyKey());
        String traceId = resolveTraceId();
        mailLog.setTraceId(traceId);
        mailLog.setCreateTime(new Date());
        save(mailLog);

        submitDelivery(mailLog.getId(), template.getAccountId(), traceId, req, renderedTitle, renderedContent);
        return mailLog.getId();
    }

    @Override
    public List<MailBatchSendItemResult> sendBatch(List<MailSendSingleReqDTO> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new JeecgBootException("批量发送请求不能为空");
        }
        if (requests.size() > MAX_BATCH_SIZE) {
            throw new JeecgBootException("批量发送单次最多 " + MAX_BATCH_SIZE + " 条，当前 " + requests.size() + " 条");
        }
        List<MailBatchSendItemResult> results = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            MailSendSingleReqDTO req = requests.get(i);
            MailBatchSendItemResult item = new MailBatchSendItemResult();
            item.setIndex(i);
            if (req != null) {
                item.setTemplateCode(req.getTemplateCode());
            }
            try {
                item.setLogId(sendSingleMail(req));
                item.setSuccess(true);
            } catch (RuntimeException e) {
                // 单条失败不影响其他条；错误信息回传调用方定位
                item.setSuccess(false);
                item.setErrorMessage(e.getMessage());
                log.warn("[mail] 批量发送第 {} 条失败 templateCode={}: {}", i,
                        item.getTemplateCode(), e.getMessage());
            }
            results.add(item);
        }
        return results;
    }

    @Override
    public void retry(String logId) {
        // 先做状态机校验与 retryCount+1（FAILED → SENDING），再提交投递
        mailLogService.retry(logId);
        SystemMailLog mailLog = getById(logId);
        if (mailLog == null) {
            return;
        }
        MailSendSingleReqDTO req = new MailSendSingleReqDTO();
        req.setTemplateCode(mailLog.getTemplateCode());
        req.setTo(mailLog.getToMails());
        req.setCc(mailLog.getCcMails());
        req.setBcc(mailLog.getBccMails());
        submitDelivery(logId, mailLog.getAccountId(), mailLog.getTraceId(), req,
                mailLog.getTemplateTitle(), mailLog.getTemplateContent());
    }

    private void submitDelivery(String logId, String accountId, String traceId, MailSendSingleReqDTO req,
            String subject, String content) {
        try {
            deliveryExecutor.submit(() -> doDeliver(logId, accountId, traceId, req, subject, content));
        } catch (RuntimeException e) {
            mailLogService.markFailed(logId, MailErrorCodes.MAIL_INTERNAL_ERROR, "投递队列已满");
        }
    }

    /**
     * 异步投递主体。
     * 状态机注意：新建路径是 PENDING→SENDING；重试路径 retry() 已把状态置为 SENDING，
     * 此处必须先判断当前状态，避免出现 1→1 的非法自迁移。
     * traceId 经 MDC 透传（需求文档 D-1），finally 清理防线程池复用串号。
     */
    private void doDeliver(String logId, String accountId, String traceId, MailSendSingleReqDTO req,
            String subject, String content) {
        boolean mdcAttached = false;
        try {
            if (StringUtils.isNotBlank(traceId)) {
                MDC.put(MailTraceConstants.TRACE_ID_MDC_KEY, traceId);
                mdcAttached = true;
            }
            SystemMailLog current = getById(logId);
            if (current == null) {
                return;
            }
            MailSendStatus status = MailSendStatus.fromCode(current.getSendStatus());
            if (status != MailSendStatus.SENDING) {
                mailLogService.transit(logId, MailSendStatus.SENDING.getCode(), null, null);
            }
        } catch (RuntimeException e) {
            log.warn("[mail] 推进发送中失败，跳过本次投递 logId={}", logId, e);
            return;
        }

        try {
            mailDelivery.deliver(accountId, req.getTo(), req.getCc(), req.getBcc(), subject, content);
            mailLogService.transit(logId, MailSendStatus.SUCCESS.getCode(), null, null);
        } catch (MailDeliveryException e) {
            mailLogService.markFailed(logId, e.getErrorCode(), e.getMessage());
        } catch (RuntimeException e) {
            mailLogService.markFailed(logId, MailErrorCodes.MAIL_INTERNAL_ERROR, e.getMessage());
        } finally {
            if (mdcAttached) {
                MDC.remove(MailTraceConstants.TRACE_ID_MDC_KEY);
            }
        }
    }

    /**
     * 解析链路 id：优先取当前线程 MDC（上游已透传），否则生成新 id；
     * 与 system_mail_log.trace_id 一致（接口文档 §1 链路）。
     */
    private String resolveTraceId() {
        String traceId = MDC.get(MailTraceConstants.TRACE_ID_MDC_KEY);
        if (StringUtils.isNotBlank(traceId)) {
            return traceId;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
