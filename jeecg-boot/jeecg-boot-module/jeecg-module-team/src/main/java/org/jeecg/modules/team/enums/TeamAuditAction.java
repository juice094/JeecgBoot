package org.jeecg.modules.team.enums;

/**
 * @Description: 审计日志动作类型
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 */
public enum TeamAuditAction {

    CREATE("create", "创建"), SUBMIT("submit", "提交"), CHANGE_STATUS("change_status", "状态流转"), COMMENT("comment", "评论"), APPROVE("approve", "审批通过"), REJECT("reject", "审批拒绝");

    private final String code;
    private final String label;

    TeamAuditAction(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }
}
