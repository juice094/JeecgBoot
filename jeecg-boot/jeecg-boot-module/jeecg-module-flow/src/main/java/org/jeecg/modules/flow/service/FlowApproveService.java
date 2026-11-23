package org.jeecg.modules.flow.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.jeecg.common.exception.JeecgBootBizTipException;
import org.jeecg.modules.flow.enums.FlowApproveAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程查询与审批办理（W2）。
 * 并发口径：任务拾取后办理——办理时校验 assignee 归属，complete 捕捉乐观锁/任务消失异常
 * 转为 409 业务提示，保证同一任务不会被并发双办。
 */
@Slf4j
@Service
public class FlowApproveService {

    /** 变量 key：审批结果（Boolean，贯穿各级审批） */
    private static final String VAR_APPROVED = "approved";
    /** 业务码：资源冲突（任务已被办理/被他人拾取/并发冲突） */
    private static final int ERR_CODE_CONFLICT = 409;
    /** 业务码：无权限操作他人任务 */
    private static final int ERR_CODE_FORBIDDEN = 403;

    @Autowired
    private org.flowable.engine.TaskService taskService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private HistoryService historyService;
    @Autowired
    private RepositoryService repositoryService;

    /**
     * 流程定义列表（含在途实例数），供 /flow/process/list 使用
     *
     * @return 流程定义摘要列表
     */
    public List<Map<String, Object>> listProcessDefinitions() {
        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .orderByProcessDefinitionKey().asc()
                .list();
        List<Map<String, Object>> result = new ArrayList<>(definitions.size());
        for (ProcessDefinition definition : definitions) {
            Map<String, Object> item = new LinkedHashMap<>(6);
            item.put("processDefinitionKey", definition.getKey());
            item.put("processDefinitionName", definition.getName());
            item.put("version", definition.getVersion());
            item.put("deploymentId", definition.getDeploymentId());
            item.put("suspended", definition.isSuspended());
            long runningInstances = runtimeService.createProcessInstanceQuery()
                    .processDefinitionId(definition.getId())
                    .count();
            item.put("runningInstances", runningInstances);
            result.add(item);
        }
        return result;
    }

    /**
     * 我的待办：当前办理人名下的在途任务
     *
     * @param username 当前登录人
     * @return 待办任务摘要列表
     */
    public List<Map<String, Object>> listTodoTasks(String username) {
        List<Task> tasks = taskService.createTaskQuery()
                .taskAssignee(username)
                .active()
                .orderByTaskCreateTime().desc()
                .list();
        Map<String, String> definitionNames = new HashMap<>(8);
        List<Map<String, Object>> result = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            Map<String, Object> item = new LinkedHashMap<>(6);
            item.put("taskId", task.getId());
            item.put("taskName", task.getName());
            item.put("processInstanceId", task.getProcessInstanceId());
            item.put("processDefinitionName", definitionName(task.getProcessDefinitionId(), definitionNames));
            item.put("createTime", task.getCreateTime());
            result.add(item);
        }
        return result;
    }

    /**
     * 我的已办：当前办理人名下已完成的任务
     *
     * @param username 当前登录人
     * @return 已办任务摘要列表
     */
    public List<Map<String, Object>> listDoneTasks(String username) {
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(username)
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .list();
        Map<String, String> definitionNames = new HashMap<>(8);
        List<Map<String, Object>> result = new ArrayList<>(tasks.size());
        for (HistoricTaskInstance task : tasks) {
            Map<String, Object> item = new LinkedHashMap<>(8);
            item.put("taskId", task.getId());
            item.put("taskName", task.getName());
            item.put("processInstanceId", task.getProcessInstanceId());
            item.put("processDefinitionName", definitionName(task.getProcessDefinitionId(), definitionNames));
            item.put("startTime", task.getStartTime());
            item.put("endTime", task.getEndTime());
            item.put("durationInMillis", task.getDurationInMillis());
            result.add(item);
        }
        return result;
    }

    /**
     * Flowable 8.0.0 起 Task/HistoricTaskInstance 不再携带流程定义名，按定义 ID 查表（按调用缓存）
     */
    private String definitionName(String processDefinitionId, Map<String, String> cache) {
        return cache.computeIfAbsent(processDefinitionId, id -> {
            ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(id)
                    .singleResult();
            return definition == null ? null : definition.getName();
        });
    }

    /**
     * 审批办理（通过/驳回）。
     * 拾取口径：任务无办理人则拾取为当前人；已有办理人且非当前人则拒绝；
     * complete 阶段捕捉引擎乐观锁/任务消失，转为 409 冲突提示。
     *
     * @param username 当前登录人
     * @param taskId   任务 ID
     * @param action   通过/驳回
     * @param comment  审批意见（可空）
     * @return 所属流程实例 ID
     */
    public String handleTask(String username, String taskId, FlowApproveAction action, String comment) {
        if (StringUtils.isBlank(username)) {
            throw new JeecgBootBizTipException("当前登录人不能为空", ERR_CODE_FORBIDDEN);
        }
        if (StringUtils.isBlank(taskId)) {
            throw new JeecgBootBizTipException("任务 ID 不能为空", ERR_CODE_CONFLICT);
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new JeecgBootBizTipException("任务不存在或已被办理", ERR_CODE_CONFLICT);
        }
        if (StringUtils.isBlank(task.getAssignee())) {
            taskService.claim(taskId, username);
        } else if (!username.equals(task.getAssignee())) {
            throw new JeecgBootBizTipException("任务已由他人拾取，无权办理", ERR_CODE_FORBIDDEN);
        }
        if (StringUtils.isNotBlank(comment)) {
            taskService.addComment(taskId, task.getProcessInstanceId(), comment);
        }
        Map<String, Object> variables = new HashMap<>(1);
        variables.put(VAR_APPROVED, action == FlowApproveAction.APPROVE);
        try {
            taskService.complete(taskId, variables);
        } catch (FlowableObjectNotFoundException e) {
            log.warn("task vanished during complete, taskId={}, username={}", taskId, username);
            throw new JeecgBootBizTipException("任务不存在或已被办理", ERR_CODE_CONFLICT);
        } catch (FlowableOptimisticLockingException e) {
            log.warn("optimistic locking conflict on complete, taskId={}, username={}", taskId, username);
            throw new JeecgBootBizTipException("并发冲突，任务状态已被其他操作变更，请刷新后重试", ERR_CODE_CONFLICT);
        }
        log.info("task handled, taskId={}, username={}, action={}, processInstanceId={}",
                taskId, username, action, task.getProcessInstanceId());
        return task.getProcessInstanceId();
    }

    /**
     * 实例详情：进度 + 变量 + 节点轨迹 + 任务轨迹（消费 AUDIT 历史档）
     *
     * @param processInstanceId 流程实例 ID
     * @return 实例详情
     */
    public Map<String, Object> instanceDetail(String processInstanceId) {
        if (StringUtils.isBlank(processInstanceId)) {
            throw new JeecgBootBizTipException("流程实例 ID 不能为空", ERR_CODE_CONFLICT);
        }
        HistoricProcessInstance instance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .includeProcessVariables()
                .singleResult();
        if (instance == null) {
            throw new JeecgBootBizTipException("流程实例不存在", ERR_CODE_CONFLICT);
        }
        Map<String, Object> detail = new LinkedHashMap<>(10);
        detail.put("processInstanceId", instance.getId());
        detail.put("processDefinitionKey", instance.getProcessDefinitionKey());
        detail.put("processDefinitionName", repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(instance.getProcessDefinitionId())
                .singleResult()
                .getName());
        detail.put("startTime", instance.getStartTime());
        detail.put("endTime", instance.getEndTime());
        detail.put("durationInMillis", instance.getDurationInMillis());
        detail.put("finished", instance.getEndTime() != null);
        detail.put("endActivityId", instance.getEndActivityId());
        detail.put("variables", instance.getProcessVariables());

        List<Map<String, Object>> activityTrace = new ArrayList<>();
        historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list()
                .forEach(activity -> {
                    Map<String, Object> node = new LinkedHashMap<>(7);
                    node.put("activityId", activity.getActivityId());
                    node.put("activityName", activity.getActivityName());
                    node.put("activityType", activity.getActivityType());
                    node.put("startTime", activity.getStartTime());
                    node.put("endTime", activity.getEndTime());
                    node.put("durationInMillis", activity.getDurationInMillis());
                    node.put("assignee", activity.getAssignee());
                    activityTrace.add(node);
                });
        detail.put("activityTrace", activityTrace);

        List<Map<String, Object>> taskTrace = new ArrayList<>();
        historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricTaskInstanceStartTime().asc()
                .list()
                .forEach(task -> {
                    Map<String, Object> node = new LinkedHashMap<>(8);
                    node.put("taskId", task.getId());
                    node.put("taskName", task.getName());
                    node.put("assignee", task.getAssignee());
                    node.put("startTime", task.getStartTime());
                    node.put("endTime", task.getEndTime());
                    node.put("durationInMillis", task.getDurationInMillis());
                    node.put("deleteReason", task.getDeleteReason());
                    List<String> comments = taskService.getTaskComments(task.getId())
                            .stream()
                            .map(c -> c.getFullMessage() == null ? "" : c.getFullMessage())
                            .toList();
                    node.put("comments", comments);
                    taskTrace.add(node);
                });
        detail.put("taskTrace", taskTrace);

        List<String> openTasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .orderByTaskCreateTime().asc()
                .list()
                .stream()
                .map(Task::getName)
                .toList();
        detail.put("openTasks", openTasks);
        return detail;
    }
}
