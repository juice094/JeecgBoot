package org.jeecg.modules.flow.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.RuntimeService;
import org.jeecg.common.exception.JeecgBootBizTipException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 报销审批流程：部门主管 -> 副总 二级审批（驳回直达 rejectedEnd，见 expense-process.bpmn20.xml）。
 * 办理人（deptManager/vp）由启动方显式传入——本波不接组织链映射（W3 范围），属显式简化。
 */
@Slf4j
@Service
public class FlowExpenseService {

    /** 流程定义 key，与 processes/expense-process.bpmn20.xml 中的 process id 一致 */
    public static final String PROCESS_KEY_EXPENSE = "expenseProcess";
    /** 变量 key：申请人 */
    private static final String VAR_APPLICANT = "applicant";
    /** 变量 key：报销金额 */
    private static final String VAR_AMOUNT = "amount";
    /** 变量 key：报销事由 */
    private static final String VAR_REASON = "reason";
    /** 变量 key：附件说明 */
    private static final String VAR_ATTACHMENT_NOTE = "attachmentNote";
    /** 变量 key：部门主管办理人 */
    private static final String VAR_DEPT_MANAGER = "deptManager";
    /** 变量 key：副总办理人 */
    private static final String VAR_VP = "vp";
    /** 单次报销金额上限（含），防异常输入 */
    private static final BigDecimal MAX_EXPENSE_AMOUNT = new BigDecimal("1000000");
    /** 报销金额允许的小数位上限 */
    private static final int MAX_AMOUNT_SCALE = 2;
    /** 事由/附件说明长度上限 */
    private static final int MAX_TEXT_LENGTH = 500;
    /** 请求参数错误业务码 */
    private static final int ERR_CODE_BAD_REQUEST = 400;

    @Autowired
    private RuntimeService runtimeService;

    /**
     * 启动报销流程实例
     *
     * @param applicant      申请人（当前登录人）
     * @param amount         报销金额（大于 0、不超过上限、最多两位小数）
     * @param reason         报销事由
     * @param attachmentNote 附件说明（可空）
     * @param deptManager    部门主管办理人
     * @param vp             副总办理人
     * @return 流程实例 ID
     */
    public String startExpenseProcess(String applicant, BigDecimal amount, String reason,
                                      String attachmentNote, String deptManager, String vp) {
        if (StringUtils.isBlank(applicant)) {
            throw new JeecgBootBizTipException("申请人不能为空", ERR_CODE_BAD_REQUEST);
        }
        validateAmount(amount);
        if (StringUtils.isBlank(reason)) {
            throw new JeecgBootBizTipException("报销事由不能为空", ERR_CODE_BAD_REQUEST);
        }
        if (reason.length() > MAX_TEXT_LENGTH) {
            throw new JeecgBootBizTipException("报销事由不能超过 " + MAX_TEXT_LENGTH + " 字", ERR_CODE_BAD_REQUEST);
        }
        if (StringUtils.isNotBlank(attachmentNote) && attachmentNote.length() > MAX_TEXT_LENGTH) {
            throw new JeecgBootBizTipException("附件说明不能超过 " + MAX_TEXT_LENGTH + " 字", ERR_CODE_BAD_REQUEST);
        }
        if (StringUtils.isBlank(deptManager) || StringUtils.isBlank(vp)) {
            throw new JeecgBootBizTipException("部门主管与副总办理人不能为空", ERR_CODE_BAD_REQUEST);
        }
        Map<String, Object> variables = new HashMap<>(8);
        variables.put(VAR_APPLICANT, applicant);
        variables.put(VAR_AMOUNT, amount);
        variables.put(VAR_REASON, reason);
        variables.put(VAR_ATTACHMENT_NOTE, StringUtils.defaultString(attachmentNote));
        variables.put(VAR_DEPT_MANAGER, deptManager);
        variables.put(VAR_VP, vp);
        String processInstanceId = runtimeService.startProcessInstanceByKey(PROCESS_KEY_EXPENSE, variables).getId();
        log.info("expense process started, processInstanceId={}, applicant={}, amount={}", processInstanceId, applicant, amount);
        return processInstanceId;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new JeecgBootBizTipException("报销金额必须大于 0", ERR_CODE_BAD_REQUEST);
        }
        if (amount.compareTo(MAX_EXPENSE_AMOUNT) > 0) {
            throw new JeecgBootBizTipException("报销金额不能超过 " + MAX_EXPENSE_AMOUNT, ERR_CODE_BAD_REQUEST);
        }
        if (amount.stripTrailingZeros().scale() > MAX_AMOUNT_SCALE) {
            throw new JeecgBootBizTipException("报销金额最多两位小数", ERR_CODE_BAD_REQUEST);
        }
    }
}
