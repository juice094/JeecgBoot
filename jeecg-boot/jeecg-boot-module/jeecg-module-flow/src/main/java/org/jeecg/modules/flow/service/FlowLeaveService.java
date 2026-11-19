package org.jeecg.modules.flow.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flowable 最小请假流程：主管审批 -> 归档
 * 由 demo 模块尖刺移植而来，仅用于 W1 引擎落地冒烟验证，不代表最终业务设计
 */
@Slf4j
@Service
public class FlowLeaveService {

    /** 流程定义 key，与 processes/leave-process.bpmn20.xml 中的 process id 一致 */
    private static final String PROCESS_KEY_LEAVE = "leaveProcess";
    /** 主管审批节点定义 key */
    private static final String TASK_DEF_KEY_SUPERVISOR_APPROVE = "supervisorApprove";
    /** 归档节点定义 key */
    private static final String TASK_DEF_KEY_ARCHIVE = "archiveTask";
    /** 主管审批人（冒烟用静态办理人，不接用户体系） */
    private static final String ASSIGNEE_SUPERVISOR = "leader";
    /** 归档办理人（冒烟用静态办理人，不接用户体系） */
    private static final String ASSIGNEE_ARCHIVER = "archiver";
    /** 变量 key：申请人 */
    private static final String VAR_APPLICANT = "applicant";
    /** 变量 key：请假天数 */
    private static final String VAR_DAYS = "days";
    /** 变量 key：请假事由 */
    private static final String VAR_REASON = "reason";
    /** 变量 key：审批结果 */
    private static final String VAR_APPROVED = "approved";
    /** 请假天数上限（含），防异常输入 */
    private static final int MAX_LEAVE_DAYS = 365;
    /** 请假天数字面量下限（含） */
    private static final int MIN_LEAVE_DAYS = 1;

    @Autowired
    private org.flowable.engine.TaskService taskService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private HistoryService historyService;

    /**
     * 启动请假流程实例
     *
     * @param applicant 申请人
     * @param days      请假天数
     * @param reason    请假事由
     * @return 流程实例 ID
     */
    public String startLeaveProcess(String applicant, Integer days, String reason) {
        if (StringUtils.isBlank(applicant)) {
            throw new IllegalArgumentException("申请人不能为空");
        }
        if (days == null || days < MIN_LEAVE_DAYS || days > MAX_LEAVE_DAYS) {
            throw new IllegalArgumentException("请假天数必须在 " + MIN_LEAVE_DAYS + "-" + MAX_LEAVE_DAYS + " 之间");
        }
        Map<String, Object> variables = new HashMap<>(4);
        variables.put(VAR_APPLICANT, applicant);
        variables.put(VAR_DAYS, days);
        variables.put(VAR_REASON, StringUtils.defaultString(reason));
        String processInstanceId = runtimeService.startProcessInstanceByKey(PROCESS_KEY_LEAVE, variables).getId();
        log.info("leave process started, processInstanceId={}, applicant={}, days={}", processInstanceId, applicant, days);
        return processInstanceId;
    }

    /**
     * 主管审批并自动完成归档节点，使流程实例走到完结
     *
     * @param processInstanceId 流程实例 ID
     * @param approved          是否批准
     * @return true=实例已完结；false=实例仍在运行
     */
    public boolean approve(String processInstanceId, boolean approved) {
        if (StringUtils.isBlank(processInstanceId)) {
            throw new IllegalArgumentException("流程实例 ID 不能为空");
        }
        completeTask(processInstanceId, TASK_DEF_KEY_SUPERVISOR_APPROVE, ASSIGNEE_SUPERVISOR, VAR_APPROVED, approved);
        log.info("supervisor task completed, processInstanceId={}, approved={}", processInstanceId, approved);
        // 冒烟场景：模拟归档节点由系统自动办理，验证流程可完整走完
        completeTask(processInstanceId, TASK_DEF_KEY_ARCHIVE, ASSIGNEE_ARCHIVER, VAR_APPROVED, approved);
        log.info("archive task completed, processInstanceId={}", processInstanceId);
        return isProcessFinished(processInstanceId);
    }

    /**
     * 查询流程实例当前待办任务名列表；实例已完结返回空列表
     *
     * @param processInstanceId 流程实例 ID
     * @return 待办任务名列表
     */
    public List<String> listOpenTaskNames(String processInstanceId) {
        if (StringUtils.isBlank(processInstanceId)) {
            throw new IllegalArgumentException("流程实例 ID 不能为空");
        }
        return taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .orderByTaskCreateTime().asc()
                .list()
                .stream()
                .map(Task::getName)
                .toList();
    }

    /**
     * 查询流程实例是否已完结
     *
     * @param processInstanceId 流程实例 ID
     * @return true=已完结
     */
    public boolean isProcessFinished(String processInstanceId) {
        HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .singleResult();
        return historic != null;
    }

    private void completeTask(String processInstanceId, String taskDefKey, String assignee, String varKey, Object varValue) {
        Task task = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(taskDefKey)
                .taskAssignee(assignee)
                .singleResult();
        if (task == null) {
            log.warn("task not found, processInstanceId={}, taskDefKey={}, assignee={}", processInstanceId, taskDefKey, assignee);
            throw new IllegalStateException("任务不存在或已被办理: " + taskDefKey);
        }
        Map<String, Object> variables = new HashMap<>(1);
        variables.put(varKey, varValue);
        taskService.complete(task.getId(), variables);
    }
}
