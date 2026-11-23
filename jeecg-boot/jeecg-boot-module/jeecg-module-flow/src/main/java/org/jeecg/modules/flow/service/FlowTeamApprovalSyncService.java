package org.jeecg.modules.flow.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.jeecg.common.exception.JeecgBootBizTipException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * team 审批链 ↔ 引擎双写的引擎侧同步器（W3）。
 *
 * 只触碰 Flowable 引擎，不写 team 域表（对账/锚定落库由 team 侧
 * {@code TeamApprovalFlowSyncer} 负责）。所有方法在引擎异常时向上抛出，
 * 由调用方决定阻断或降级——并行观测态（默认）下 team 侧必须捕获降级（charter R2）。
 *
 * 口径：
 * <ul>
 *   <li>引擎镜像仅覆盖二级审批链（与 expenseProcess 同构：deptApprove → vpApprove）；
 *       team 侧非二级链不起实例、不进对账。</li>
 *   <li>approve → complete 当前在途任务并带 approved=true；reject/return → complete
 *       当前在途任务并带 approved=false（引擎侧直达 rejectedEnd；team 的 returned
 *       语义在引擎镜像中等价于「未通过终止」，对账按终态一致判定）。</li>
 *   <li>cancel → deleteProcessInstance（历史留痕 deleteReason）。</li>
 * </ul>
 */
@Slf4j
@Service
public class FlowTeamApprovalSyncService {

    /** 流程定义 key，复用 W2 报销二级链定义（deptApprove → vpApprove） */
    public static final String PROCESS_KEY = FlowExpenseService.PROCESS_KEY_EXPENSE;
    /** 变量 key：审批结果（驳回/退回时写 false，直达 rejectedEnd） */
    private static final String VAR_APPROVED = "approved";
    /** 变量 key：申请单号（team 审批单 code） */
    private static final String VAR_APPROVAL_CODE = "approvalCode";
    /** 变量 key：申请人（username） */
    private static final String VAR_APPLICANT = "applicant";
    /** 变量 key：金额（可空） */
    private static final String VAR_AMOUNT = "amount";
    /** 变量 key：部门主管办理人 */
    private static final String VAR_DEPT_MANAGER = "deptManager";
    /** 变量 key：副总办理人 */
    private static final String VAR_VP = "vp";
    /** 业务码：同步目标不存在（实例/任务缺失） */
    private static final int ERR_CODE_NOT_FOUND = 409;

    @Autowired
    private org.flowable.engine.TaskService taskService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private HistoryService historyService;

    /**
     * 启动审批单的引擎镜像实例。
     *
     * @param approvalCode 申请单号（作为 businessKey 双向锚定）
     * @param applicant    申请人 username
     * @param amount       金额（可空）
     * @param deptManager  部门主管办理人 username（不得为空，调用方先行兜底）
     * @param vp           副总办理人 username（不得为空，调用方先行兜底）
     * @return 流程实例 ID
     */
    public String startInstance(String approvalCode, String applicant, BigDecimal amount,
                                String deptManager, String vp) {
        if (StringUtils.isAnyBlank(approvalCode, applicant, deptManager, vp)) {
            throw new JeecgBootBizTipException("启动引擎镜像缺少必要参数: approvalCode/applicant/deptManager/vp", ERR_CODE_NOT_FOUND);
        }
        Map<String, Object> variables = new HashMap<>(6);
        variables.put(VAR_APPROVAL_CODE, approvalCode);
        variables.put(VAR_APPLICANT, applicant);
        variables.put(VAR_AMOUNT, amount);
        variables.put(VAR_DEPT_MANAGER, deptManager);
        variables.put(VAR_VP, vp);
        String processInstanceId = runtimeService
                .startProcessInstanceByKey(PROCESS_KEY, approvalCode, variables)
                .getId();
        log.info("approval engine mirror started, approvalCode={}, processInstanceId={}", approvalCode, processInstanceId);
        return processInstanceId;
    }

    /** 同步「通过」：complete 当前在途任务（approved=true），实例推进到下一节点或 approvedEnd */
    public void syncApprove(String processInstanceId) {
        completeOpenTask(processInstanceId, Boolean.TRUE);
    }

    /** 同步「拒绝/退回」：complete 当前在途任务（approved=false），直达 rejectedEnd */
    public void syncReject(String processInstanceId) {
        completeOpenTask(processInstanceId, Boolean.FALSE);
    }

    /** 同步「撤回」：删除在途实例（历史留痕 deleteReason=canceled-by-team） */
    public void syncCancel(String processInstanceId) {
        if (runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).count() == 0) {
            return;
        }
        runtimeService.deleteProcessInstance(processInstanceId, "canceled-by-team");
        log.info("approval engine mirror canceled, processInstanceId={}", processInstanceId);
    }

    /**
     * 观测引擎实例状态（对账快照口径）：
     * RUNNING:&lt;任务定义Key&gt; / FINISHED:&lt;endEvent&gt; / DELETED / NOT_FOUND
     *
     * @param processInstanceId 流程实例 ID
     * @return 状态快照串
     */
    public String observeState(String processInstanceId) {
        if (StringUtils.isBlank(processInstanceId)) {
            return "NOT_FOUND";
        }
        List<Task> openTasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .orderByTaskCreateTime().asc()
                .list();
        if (!openTasks.isEmpty()) {
            return "RUNNING:" + openTasks.get(0).getTaskDefinitionKey();
        }
        HistoricProcessInstance instance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (instance == null) {
            return "NOT_FOUND";
        }
        // deleteProcessInstance 删除的运行实例在历史档留 deleteReason（endTime 也会补上），优先按删除判定
        if (StringUtils.isNotBlank(instance.getDeleteReason()) || instance.getEndTime() == null) {
            return "DELETED";
        }
        return "FINISHED:" + StringUtils.defaultString(instance.getEndActivityId());
    }

    private void completeOpenTask(String processInstanceId, Boolean approved) {
        List<Task> openTasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .orderByTaskCreateTime().asc()
                .list();
        if (openTasks.isEmpty()) {
            throw new JeecgBootBizTipException("引擎镜像无在途任务: processInstanceId=" + processInstanceId, ERR_CODE_NOT_FOUND);
        }
        if (openTasks.size() > 1) {
            throw new JeecgBootBizTipException("引擎镜像存在多个在途任务，拒绝猜测同步: processInstanceId="
                    + processInstanceId + ", openTasks=" + openTasks.size(), ERR_CODE_NOT_FOUND);
        }
        Task task = openTasks.get(0);
        Map<String, Object> variables = new HashMap<>(1);
        variables.put(VAR_APPROVED, approved);
        taskService.complete(task.getId(), variables);
        log.info("approval engine mirror synced, processInstanceId={}, taskKey={}, approved={}",
                processInstanceId, task.getTaskDefinitionKey(), approved);
    }
}
