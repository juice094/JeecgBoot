package org.jeecg.modules.team.enums;

/**
 * @Description: 审计日志业务类型
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 */
public enum TeamAuditBizType {

    TASK("task", "任务"),
    APPROVAL("approval", "审批单"),
    BUG("bug", "Bug反馈");

    private final String code;
    private final String label;

    TeamAuditBizType(String code, String label) {
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
