package org.jeecg.modules.flow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.jeecg.common.exception.JeecgBootBizTipException;
import org.jeecg.modules.flow.enums.FlowApproveAction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 请假流程引擎级单元测试：H2 内存库 + 独立引擎（无 Spring 上下文，不依赖外部 MySQL）。
 * 覆盖：发起落点（主管节点/静态办理人 leader）、两级办理正路径、参数校验负路径、变量落档。
 * 隔离口径：共享引擎跨用例残留运行态任务，故每用例推进到完结或只用断言性查询，不依赖全局计数。
 */
class FlowLeaveProcessTest {

    private static final String USER_APPLICANT = "lisi";
    private static final String ASSIGNEE_SUPERVISOR = "leader";
    private static final String ASSIGNEE_ARCHIVER = "archiver";
    private static final String TASK_NAME_SUPERVISOR = "主管审批";
    private static final String TASK_NAME_ARCHIVE = "归档";
    private static final String END_ACTIVITY = "endEvent";
    private static final String LEAVE_RESOURCE = "processes/leave-process.bpmn20.xml";
    private static final int ERR_CODE_BAD_REQUEST = 400;

    private static ProcessEngine engine;
    private static FlowLeaveService leaveService;
    private static FlowApproveService approveService;

    @BeforeAll
    static void setUpEngine() {
        engine = new StandaloneInMemProcessEngineConfiguration()
                .setHistoryLevel(HistoryLevel.AUDIT)
                .setAsyncExecutorActivate(false)
                .buildProcessEngine();
        engine.getRepositoryService().createDeployment()
                .name("test-leave")
                .addClasspathResource(LEAVE_RESOURCE)
                .deploy();
        leaveService = new FlowLeaveService();
        ReflectionTestUtils.setField(leaveService, "runtimeService", engine.getRuntimeService());
        approveService = new FlowApproveService();
        ReflectionTestUtils.setField(approveService, "taskService", engine.getTaskService());
        ReflectionTestUtils.setField(approveService, "runtimeService", engine.getRuntimeService());
        ReflectionTestUtils.setField(approveService, "historyService", engine.getHistoryService());
        ReflectionTestUtils.setField(approveService, "repositoryService", engine.getRepositoryService());
    }

    @AfterAll
    static void closeEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    private String firstTodoTaskId(String username) {
        List<Map<String, Object>> todo = approveService.listTodoTasks(username);
        assertFalse(todo.isEmpty(), "待办不应为空");
        return (String) todo.get(0).get("taskId");
    }

    @Test
    void startAssignsSupervisorTaskToLeaderOnly() {
        leaveService.startLeaveProcess(USER_APPLICANT, 3, "病假");
        List<Map<String, Object>> leaderTodo = approveService.listTodoTasks(ASSIGNEE_SUPERVISOR);
        assertEquals(1, leaderTodo.size());
        assertEquals(TASK_NAME_SUPERVISOR, leaderTodo.get(0).get("taskName"));
        assertEquals(FlowLeaveService.PROCESS_KEY_LEAVE, engine.getRuntimeService()
                .createProcessInstanceQuery()
                .processInstanceId((String) leaderTodo.get(0).get("processInstanceId"))
                .singleResult()
                .getProcessDefinitionKey());
        assertTrue(approveService.listTodoTasks(ASSIGNEE_ARCHIVER).isEmpty(), "归档人此时不应有待办");
        assertTrue(approveService.listTodoTasks(USER_APPLICANT).isEmpty(), "申请人不应有待办");
    }

    @Test
    void fullTwoLevelApproveFinishesAtEndEvent() {
        String instanceId = leaveService.startLeaveProcess(USER_APPLICANT, 5, "事假");
        approveService.handleTask(ASSIGNEE_SUPERVISOR, firstTodoTaskId(ASSIGNEE_SUPERVISOR),
                FlowApproveAction.APPROVE, "同意");
        List<Map<String, Object>> archiverTodo = approveService.listTodoTasks(ASSIGNEE_ARCHIVER);
        assertEquals(1, archiverTodo.size(), "主管办后归档人应出现待办");
        assertEquals(TASK_NAME_ARCHIVE, archiverTodo.get(0).get("taskName"));
        approveService.handleTask(ASSIGNEE_ARCHIVER, (String) archiverTodo.get(0).get("taskId"),
                FlowApproveAction.APPROVE, "已归档");
        Map<String, Object> detail = approveService.instanceDetail(instanceId);
        assertEquals(Boolean.TRUE, detail.get("finished"));
        assertEquals(END_ACTIVITY, detail.get("endActivityId"));
        assertTrue(((List<?>) detail.get("openTasks")).isEmpty());
        assertEquals(2, ((List<?>) detail.get("taskTrace")).size());
    }

    @Test
    void startRecordsApplicantDaysReasonAsVariables() {
        String instanceId = leaveService.startLeaveProcess(USER_APPLICANT, 7, "年假");
        Map<String, Object> variables = (Map<String, Object>) approveService.instanceDetail(instanceId).get("variables");
        assertEquals(USER_APPLICANT, variables.get("applicant"));
        assertEquals(7, variables.get("days"));
        assertEquals("年假", variables.get("reason"));
    }

    @Test
    void startValidationRejectsIllegalInput() {
        assert400(() -> leaveService.startLeaveProcess(" ", 3, "病假"));
        assert400(() -> leaveService.startLeaveProcess(USER_APPLICANT, null, "病假"));
        assert400(() -> leaveService.startLeaveProcess(USER_APPLICANT, 0, "病假"));
        assert400(() -> leaveService.startLeaveProcess(USER_APPLICANT, 366, "病假"));
        assert400(() -> leaveService.startLeaveProcess(USER_APPLICANT, -1, "病假"));
    }

    private void assert400(org.junit.jupiter.api.function.Executable executable) {
        JeecgBootBizTipException e = assertThrows(JeecgBootBizTipException.class, executable);
        assertEquals(ERR_CODE_BAD_REQUEST, e.getErrCode());
    }
}
