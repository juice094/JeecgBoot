package org.jeecg.modules.team.enums;

/**
 * @Description: 团队协作-审批状态机枚举
 * @Author: juice094
 * @Date: 2026-11-18
 * @Version: V1.0
 * <p>
 * 流转口径：
 *   draft    → pending（submit）
 *   pending  → approved（末步 approve）/ rejected（reject）/ returned（return）/ canceled（cancel）
 *   pending  → pending（中间步 approve，仅 current_step++，不属于状态迁移）
 *   returned → pending（resubmit）/ canceled（cancel）
 *   approved / rejected / canceled 均为终态。
 */
public enum TeamApprovalStatus {

    DRAFT("draft", "草稿"),
    PENDING("pending", "待审"),
    RETURNED("returned", "已退回"),
    APPROVED("approved", "已通过"),
    REJECTED("rejected", "已拒绝"),
    CANCELED("canceled", "已撤销");

    private final String code;
    private final String label;

    TeamApprovalStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 终态：不可再发起任何流转 */
    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED || this == CANCELED;
    }

    /** 是否允许从当前状态流转到目标状态 */
    public boolean canTransitTo(TeamApprovalStatus target) {
        if (target == null) {
            return false;
        }
        switch (this) {
            case DRAFT:
                return target == PENDING || target == CANCELED;
            case PENDING:
                return target == APPROVED || target == REJECTED
                        || target == RETURNED || target == CANCELED;
            case RETURNED:
                return target == PENDING || target == CANCELED;
            default:
                return false;
        }
    }

    /** 按落库码解析状态，非法值返回 null */
    public static TeamApprovalStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TeamApprovalStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return null;
    }
}
