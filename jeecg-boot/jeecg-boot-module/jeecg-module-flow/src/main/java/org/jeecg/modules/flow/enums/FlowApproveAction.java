package org.jeecg.modules.flow.enums;

/**
 * 审批办理动作（POST /flow/approve/handle 的 action 参数取值）
 */
public enum FlowApproveAction {

    /** 通过 */
    APPROVE,
    /** 驳回 */
    REJECT
}
