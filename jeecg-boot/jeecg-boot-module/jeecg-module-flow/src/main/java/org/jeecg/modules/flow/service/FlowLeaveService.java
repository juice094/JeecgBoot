package org.jeecg.modules.flow.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.RuntimeService;
import org.jeecg.common.exception.JeecgBootBizTipException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 请假审批流程：主管审批 -> 归档 二级办理（见 leave-process.bpmn20.xml）。
 * 办理人为定义内静态办理人（leader/archiver）——本波不接组织链映射，属显式简化。
 * 发起统一走 /flow/process/start（processKey=leave），申请人取当前登录人。
 */
@Slf4j
@Service
public class FlowLeaveService {

    /** 流程定义 key，与 processes/leave-process.bpmn20.xml 中的 process id 一致 */
    public static final String PROCESS_KEY_LEAVE = "leaveProcess";
    /** 变量 key：申请人 */
    private static final String VAR_APPLICANT = "applicant";
    /** 变量 key：请假天数 */
    private static final String VAR_DAYS = "days";
    /** 变量 key：请假事由 */
    private static final String VAR_REASON = "reason";
    /** 请求参数错误业务码 */
    private static final int ERR_CODE_BAD_REQUEST = 400;
    /** 请假天数字面量下限（含） */
    private static final int MIN_LEAVE_DAYS = 1;
    /** 请假天数上限（含），防异常输入 */
    private static final int MAX_LEAVE_DAYS = 365;

    @Autowired
    private RuntimeService runtimeService;

    /**
     * 启动请假流程实例
     *
     * @param applicant 申请人（当前登录人）
     * @param days      请假天数（1-365 的整数）
     * @param reason    请假事由（可空）
     * @return 流程实例 ID
     */
    public String startLeaveProcess(String applicant, Integer days, String reason) {
        if (StringUtils.isBlank(applicant)) {
            throw new JeecgBootBizTipException("申请人不能为空", ERR_CODE_BAD_REQUEST);
        }
        if (days == null || days < MIN_LEAVE_DAYS || days > MAX_LEAVE_DAYS) {
            throw new JeecgBootBizTipException(
                    "请假天数必须是 " + MIN_LEAVE_DAYS + "-" + MAX_LEAVE_DAYS + " 的整数", ERR_CODE_BAD_REQUEST);
        }
        Map<String, Object> variables = new HashMap<>(4);
        variables.put(VAR_APPLICANT, applicant);
        variables.put(VAR_DAYS, days);
        variables.put(VAR_REASON, StringUtils.defaultString(reason));
        String processInstanceId = runtimeService.startProcessInstanceByKey(PROCESS_KEY_LEAVE, variables).getId();
        log.info("leave process started, processInstanceId={}, applicant={}, days={}", processInstanceId, applicant, days);
        return processInstanceId;
    }
}
