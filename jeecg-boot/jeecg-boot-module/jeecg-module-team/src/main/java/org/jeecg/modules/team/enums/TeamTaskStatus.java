package org.jeecg.modules.team.enums;

/**
 * @Description: 团队协作-任务状态机枚举
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 * <p>
 * 流转口径（与 V3.9.6_2 team_task.status 及前端 src/constants/task.ts 对齐）：
 *   TODO → DOING（开始执行）/ DONE（直接完成）
 *   DOING → TODO（退回待办）/ DONE（完成）
 *   DONE → TODO（重做）/ DOING（验收不通过重启）
 * 看板语义下 done 不是终态，但同状态自流转视为非法（调用方不应在状态未变时发起流转）。
 * <p>
 * ponytail: 与 TeamBugStatus 相同的 switch 懒判定实现，配置化流转表属过度设计。
 */
public enum TeamTaskStatus {

    TODO("todo", "待办"),
    DOING("doing", "进行中"),
    DONE("done", "已完成");

    /**
     * 状态码，落库值
     */
    private final String code;

    /**
     * 中文展示名（留痕/异常提示用）
     */
    private final String label;

    TeamTaskStatus(String code, String label) {
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
    public boolean canTransitTo(TeamTaskStatus target) {
        if (target == null) {
            return false;
        }
        switch (this) {
            case TODO:
                return target == DOING || target == DONE;
            case DOING:
                return target == TODO || target == DONE;
            case DONE:
                return target == TODO || target == DOING;
            default:
                return false;
        }
    }

    /**
     * 按落库码解析状态，非法值返回 null（调用方决定报错口径）
     */
    public static TeamTaskStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TeamTaskStatus status : values()) {
            if (status.code.equalsIgnoreCase(code.trim())) {
                return status;
            }
        }
        return null;
    }
}
