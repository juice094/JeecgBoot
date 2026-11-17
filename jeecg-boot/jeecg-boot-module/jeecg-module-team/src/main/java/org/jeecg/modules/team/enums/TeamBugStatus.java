package org.jeecg.modules.team.enums;

/**
 * @Description: 团队协作-Bug 状态机枚举
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 * <p>
 * 流转口径（与 V3.9.6_3 team_bug.status 注释对齐）：
 * OPEN → PROCESSING（认领处理）/ CLOSED（误报直接关闭）
 * PROCESSING → RESOLVED（修复完成）/ OPEN（打回重新排队）
 * RESOLVED → CLOSED（验收通过归档）/ OPEN（验收不通过重开）
 * CLOSED 为终态，只允许迁移归档，不允许再流转
 * <p>
 * ponytail: 判定走 canTransitTo 的 switch 懒计算——枚举常量初始化顺序
 * 禁止在构造器里引用其它常量（EnumSet.of 会踩 clinit NPE），升级时如需
 * 配置化流转表，可迁移为 Map<TeamBugStatus, Set<TeamBugStatus>> 静态表。
 */
public enum TeamBugStatus {

    OPEN("open", "待处理"),
    PROCESSING("processing", "处理中"),
    RESOLVED("resolved", "已解决"),
    CLOSED("closed", "已关闭");

    /**
     * 状态码，落库值
     */
    private final String code;

    /**
     * 中文展示名（留痕/异常提示用）
     */
    private final String label;

    TeamBugStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 是否允许从当前状态流转到目标状态
     */
    public boolean canTransitTo(TeamBugStatus target) {
        if (target == null) {
            return false;
        }
        switch (this) {
            case OPEN:
                return target == PROCESSING || target == CLOSED;
            case PROCESSING:
                return target == RESOLVED || target == OPEN;
            case RESOLVED:
                return target == CLOSED || target == OPEN;
            case CLOSED:
                // 终态：删除类操作走迁移归档，不物理删；closed 后不再流转
            default:
                return false;
        }
    }

    /**
     * 按落库码解析状态，非法值返回 null（调用方决定报错口径）
     */
    public static TeamBugStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TeamBugStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return null;
    }
}
