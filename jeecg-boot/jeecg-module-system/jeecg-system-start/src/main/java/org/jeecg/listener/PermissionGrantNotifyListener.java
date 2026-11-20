package org.jeecg.listener;

import org.jeecg.common.api.dto.message.MessageDTO;
import org.jeecg.common.permission.PermissionGrantEvent;
import org.jeecg.common.system.api.ISysBaseAPI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * @Description: 权限确权事件通知监听器（站内信）
 * @Author: juice094
 * @Date: 2026-11-19
 * @Version: V1.0
 * <p>
 * 装配在 jeecg-system-start：该模块才允许依赖通知通道。system 层只发事件不依赖通知。
 * 当前走 Jeecg 原生站内信（无外部依赖）；邮件通道待通知模板 + SMTP 账号配置后追加。
 * 通知失败只记日志，不回滚治理动作。
 */
@Slf4j
@Component
public class PermissionGrantNotifyListener {

    @Autowired
    private ISysBaseAPI sysBaseAPI;

    @EventListener
    public void onGrantEvent(PermissionGrantEvent event) {
        String receiver = resolveReceiver(event);
        if (receiver == null || receiver.trim().isEmpty()) {
            log.warn("授权事件缺少通知对象，跳过: type={}, grantNo={}", event.getEventType(), event.getGrantNo());
            return;
        }
        try {
            MessageDTO message = new MessageDTO("system", receiver, buildTitle(event), buildContent(event));
            sysBaseAPI.sendSysAnnouncement(message);
        } catch (Exception e) {
            log.warn("授权事件通知失败 type={}, grantNo={}, receiver={}: {}",
                    event.getEventType(), event.getGrantNo(), receiver, e.getMessage());
        }
    }

    /**
     * 通知对象口径（PERMISSION-GOVERNANCE.md §10.2）：
     * submitted → 指定审批人；其余 → 申请人。
     * 被授权人（subject）通知待 P3 与 subject 用户名解析一并接入。
     */
    private String resolveReceiver(PermissionGrantEvent event) {
        if (PermissionGrantEvent.SUBMITTED.equals(event.getEventType())) {
            return event.getApprover();
        }
        return event.getApplicant();
    }

    private String buildTitle(PermissionGrantEvent event) {
        switch (event.getEventType()) {
            case PermissionGrantEvent.SUBMITTED:
                return "权限授权申请待审批";
            case PermissionGrantEvent.APPROVED:
                return "权限授权申请已批准";
            case PermissionGrantEvent.REJECTED:
                return "权限授权申请被拒绝";
            case PermissionGrantEvent.ACTIVATED:
                return "权限授权已生效";
            case PermissionGrantEvent.EXPIRING:
                return "权限授权即将到期";
            case PermissionGrantEvent.EXPIRED:
                return "权限授权已到期";
            case PermissionGrantEvent.REVOKED:
                return "权限授权已被回收";
            default:
                return "权限授权状态变更";
        }
    }

    private String buildContent(PermissionGrantEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("授权单号：").append(event.getGrantNo()).append('\n');
        sb.append("事件：").append(event.getEventType()).append('\n');
        sb.append("主体：").append(event.getSubjectType()).append('/').append(event.getSubjectId()).append('\n');
        if (event.getOperator() != null) {
            sb.append("操作人：").append(event.getOperator()).append('\n');
        }
        return sb.toString();
    }
}
