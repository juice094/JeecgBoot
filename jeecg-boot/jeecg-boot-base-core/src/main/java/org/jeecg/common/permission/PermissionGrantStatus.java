package org.jeecg.common.permission;

/**
 * @Description: 权限授予状态机（确权治理层）
 * @Author: juice094
 * @Date: 2026-11-19
 * @Version: V1.0
 * <p>
 * 前六态与 TeamApprovalStatus 同构（同一套流转纪律），后三态为治理层特有：
 *   draft    → pending（submit）/ canceled（cancel）
 *   pending  → approved（末步 approve）/ rejected（reject）/ returned（return）/ canceled（cancel）
 *   pending  → pending（中间步 approve，仅 current_step++，不属于状态迁移）
 *   returned → pending（resubmit）/ canceled（cancel）
 *   approved → active（系统生效，禁止人工直写）
 *   active   → expired（读时判定到期 / 扫描落库）/ revoked（人工回收）
 *   rejected / canceled / expired / revoked 均为终态，不可复活，需重新申请。
 */
public enum PermissionGrantStatus {

    DRAFT("draft", "草稿"),
    PENDING("pending", "待审批"),
    RETURNED("returned", "已退回"),
    APPROVED("approved", "已批准"),
    REJECTED("rejected", "已拒绝"),
    CANCELED("canceled", "已撤销"),
    ACTIVE("active", "已生效"),
    EXPIRED("expired", "已过期"),
    REVOKED("revoked", "已回收");

    private final String code;
    private final String label;

    PermissionGrantStatus(String code, String label) {
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
        return this == REJECTED || this == CANCELED || this == EXPIRED || this == REVOKED;
    }

    /** 是否为鉴权侧可见状态（唯一合法来源：读路径只认 ACTIVE + 时间窗口） */
    public boolean isVisibleToAuthorizer() {
        return this == ACTIVE;
    }

    /** 是否允许从当前状态流转到目标状态 */
    public boolean canTransitTo(PermissionGrantStatus target) {
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
            case APPROVED:
                return target == ACTIVE || target == CANCELED;
            case ACTIVE:
                return target == EXPIRED || target == REVOKED;
            default:
                return false;
        }
    }

    public static PermissionGrantStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PermissionGrantStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }
}
