package org.jeecg.modules.team.service.impl;

import java.util.Date;

import org.apache.shiro.SecurityUtils;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.team.entity.TeamAuditLog;
import org.jeecg.modules.team.enums.TeamAuditAction;
import org.jeecg.modules.team.enums.TeamAuditBizType;
import org.jeecg.modules.team.mapper.TeamAuditLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Description: 团队协作-审计埋点器（team_audit_log 只追加写入）
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 *
 * 由业务状态机（task/approval/bug）在事务内调用；操作人取当前登录态，
 * 未登录（如异步补偿场景）时 operator 字段留空，不阻断业务主流程。
 * 审计行只增不改，禁止提供更新/删除路径。
 */
@Component
public class TeamAuditLogger {

    @Autowired
    private TeamAuditLogMapper auditLogMapper;

    /**
     * 记录一条审计日志（同业务事务，随业务回滚）
     *
     * @param bizType  业务类型
     * @param bizId    业务单据ID
     * @param bizCode  业务单号/标题冗余（可空）
     * @param action   动作
     * @param fromValue 变更前值（可空，如 submit 无前置状态）
     * @param toValue  变更后值
     * @param detail   补充说明（可空）
     */
    public void log(TeamAuditBizType bizType, String bizId, String bizCode, TeamAuditAction action,
            String fromValue, String toValue, String detail) {
        TeamAuditLog auditLog = new TeamAuditLog();
        auditLog.setBizType(bizType.getCode());
        auditLog.setBizId(bizId);
        auditLog.setBizCode(bizCode);
        auditLog.setAction(action.getCode());
        auditLog.setFromValue(fromValue);
        auditLog.setToValue(toValue);
        auditLog.setDetail(detail);
        auditLog.setCreateTime(new Date());

        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser != null) {
            auditLog.setOperatorId(loginUser.getId());
            auditLog.setOperatorName(loginUser.getRealname());
            auditLog.setCreateBy(loginUser.getUsername());
        }
        auditLogMapper.insert(auditLog);
    }
}
