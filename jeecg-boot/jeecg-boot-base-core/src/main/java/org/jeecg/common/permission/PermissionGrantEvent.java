package org.jeecg.common.permission;

import org.springframework.context.ApplicationEvent;

/**
 * @Description: 权限确权领域事件（授权变更作为一等事件，而非普通日志）
 * @Author: juice094
 * @Date: 2026-11-19
 * @Version: V1.0
 * <p>
 * 事件名口径见 docs/permission-governance/PERMISSION-GOVERNANCE.md §10.2。
 * 通知监听器装配在 jeecg-system-start（该模块依赖 mail），system 层不直接依赖 mail。
 */
public class PermissionGrantEvent extends ApplicationEvent {

    public static final String SUBMITTED = "grant.submitted";
    public static final String APPROVED = "grant.approved";
    public static final String REJECTED = "grant.rejected";
    public static final String ACTIVATED = "grant.activated";
    public static final String EXPIRING = "grant.expiring";
    public static final String EXPIRED = "grant.expired";
    public static final String REVOKED = "grant.revoked";

    private static final long serialVersionUID = 1L;

    private final String eventType;
    private final String grantId;
    private final String grantNo;
    private final String subjectType;
    private final String subjectId;
    private final String applicant;
    private final String operator;
    private final String approver;

    public PermissionGrantEvent(Object source, String eventType, String grantId, String grantNo,
                                String subjectType, String subjectId, String applicant, String operator) {
        this(source, eventType, grantId, grantNo, subjectType, subjectId, applicant, operator, null);
    }

    public PermissionGrantEvent(Object source, String eventType, String grantId, String grantNo,
                                String subjectType, String subjectId, String applicant, String operator,
                                String approver) {
        super(source);
        this.eventType = eventType;
        this.grantId = grantId;
        this.grantNo = grantNo;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.applicant = applicant;
        this.operator = operator;
        this.approver = approver;
    }

    public String getEventType() {
        return eventType;
    }

    public String getGrantId() {
        return grantId;
    }

    public String getGrantNo() {
        return grantNo;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public String getApplicant() {
        return applicant;
    }

    public String getOperator() {
        return operator;
    }

    public String getApprover() {
        return approver;
    }
}
