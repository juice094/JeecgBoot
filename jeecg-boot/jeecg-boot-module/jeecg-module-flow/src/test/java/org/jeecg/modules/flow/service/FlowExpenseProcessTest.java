package org.jeecg.modules.flow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.jeecg.common.exception.JeecgBootBizTipException;
import org.jeecg.modules.flow.config.FlowDeploymentConfig;
import org.jeecg.modules.flow.enums.FlowApproveAction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 报销流程引擎级单元测试：H2 内存库 + 独立引擎（无 Spring 上下文，不依赖外部 MySQL）。
 * 覆盖：二级审批正路径、两级驳回路径、待办/已办口径、拾取-办理并发负路径、参数校验、部署幂等。
 * 隔离口径：共享引擎跨用例残留运行态任务，故每个用例使用专属办理人用户名，列表断言互不串扰。
 */
class FlowExpenseProcessTest {

    private static final String USER_APPLICANT = "zhangsan";
    private static final String USER_STRANGER = "stranger";
    private static final String TASK_NAME_DEPT_APPROVE = "部门主管审批";
    private static final String TASK_NAME_VP_APPROVE = "副总审批";
    private static final String END_ACTIVITY_APPROVED = "approvedEnd";
    private static final String END_ACTIVITY_REJECTED = "rejectedEnd";
    private static final String EXPENSE_RESOURCE = "processes/expense-process.bpmn20.xml";
    private static final int ERR_CODE_CONFLICT = 409;
    private static final int ERR_CODE_FORBIDDEN = 403;

    private static ProcessEngine engine;
    private static FlowExpenseService expenseService;
    private static FlowApproveService approveService;

    @BeforeAll
    static void setUpEngine() {
        engine = new StandaloneInMemProcessEngineConfiguration()
                .setHistoryLevel(HistoryLevel.AUDIT)
                .setAsyncExecutorActivate(false)
                .buildProcessEngine();
        engine.getRepositoryService().createDeployment()
                .name("test-expense")
                .addClasspathResource(EXPENSE_RESOURCE)
                .deploy();
        expenseService = new FlowExpenseService();
        ReflectionTestUtils.setField(expenseService, "runtimeService", engine.getRuntimeService());
        approveService = new FlowApproveService();
        ReflectionTestUtils.setField(approveService, "taskService", engine.getTaskService());
        ReflectionTestUtils.setField(approveService, "runtimeService", engine.getRuntimeService());
        ReflectionTestUtils.setField(approveService, "historyService", engine.getHistoryService());
        ReflectionTestUtils.setField(approveService, "repositoryService", engine.getRepositoryService());
        // listProcessDefinitions 需审批人预览解析器；本类只断言定义项存在，mock 恒 null 兜底
        FlowApproverResolver resolver = mock(FlowApproverResolver.class);
        ReflectionTestUtils.setField(approveService, "flowApproverResolver", resolver);
    }

    @AfterAll
    static void closeEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    /**
     * 每个用例使用专属办理人，避免共享引擎中其他用例残留的待办串扰列表断言
     *
     * @param tag 用例专属后缀
     */
    private String startWithUsers(String tag) {
        return expenseService.startExpenseProcess(USER_APPLICANT, new BigDecimal("1280.50"),
                "差旅报销", "发票 2 张", "dept_mgr_" + tag, "vp_" + tag);
    }

    private String firstTodoTaskId(String username) {
        List<Map<String, Object>> todo = approveService.listTodoTasks(username);
        assertFalse(todo.isEmpty(), "待办不应为空");
        return (String) todo.get(0).get("taskId");
    }

    @Test
    void startAssignsDeptTaskToDeptManagerOnly() {
        startWithUsers("start");
        List<Map<String, Object>> deptTodo = approveService.listTodoTasks("dept_mgr_start");
        assertEquals(1, deptTodo.size());
        assertEquals(TASK_NAME_DEPT_APPROVE, deptTodo.get(0).get("taskName"));
        assertEquals(FlowExpenseService.PROCESS_KEY_EXPENSE, engine.getRuntimeService()
                .createProcessInstanceQuery()
                .processInstanceId((String) deptTodo.get(0).get("processInstanceId"))
                .singleResult()
                .getProcessDefinitionKey());
        assertTrue(approveService.listTodoTasks("vp_start").isEmpty(), "副总此时不应有待办");
        assertTrue(approveService.listTodoTasks(USER_APPLICANT).isEmpty(), "申请人不应有待办");
    }

    @Test
    void fullApprovePathFinishesAtApprovedEnd() {
        String instanceId = startWithUsers("full");
        approveService.handleTask("dept_mgr_full", firstTodoTaskId("dept_mgr_full"), FlowApproveAction.APPROVE, "同意，票据齐全");
        approveService.handleTask("vp_full", firstTodoTaskId("vp_full"), FlowApproveAction.APPROVE, "同意");
        Map<String, Object> detail = approveService.instanceDetail(instanceId);
        assertEquals(Boolean.TRUE, detail.get("finished"));
        assertEquals(END_ACTIVITY_APPROVED, detail.get("endActivityId"));
        assertTrue(((List<?>) detail.get("openTasks")).isEmpty());
        List<Map<String, Object>> activityTrace = (List<Map<String, Object>>) detail.get("activityTrace");
        assertTrue(activityTrace.stream().anyMatch(n -> "vpApprove".equals(n.get("activityId"))));
        assertEquals(2, ((List<?>) detail.get("taskTrace")).size());
        Map<String, Object> variables = (Map<String, Object>) detail.get("variables");
        assertEquals(0, new BigDecimal("1280.50").compareTo((BigDecimal) variables.get("amount")));
        assertEquals(USER_APPLICANT, variables.get("applicant"));
    }

    @Test
    void deptRejectFinishesImmediatelyAtRejectedEnd() {
        String instanceId = startWithUsers("deptreject");
        approveService.handleTask("dept_mgr_deptreject", firstTodoTaskId("dept_mgr_deptreject"),
                FlowApproveAction.REJECT, "票据不全，驳回");
        Map<String, Object> detail = approveService.instanceDetail(instanceId);
        assertEquals(Boolean.TRUE, detail.get("finished"));
        assertEquals(END_ACTIVITY_REJECTED, detail.get("endActivityId"));
        assertTrue(approveService.listTodoTasks("vp_deptreject").isEmpty(), "驳回后副总不应再有待办");
    }

    @Test
    void vpRejectFinishesAtRejectedEnd() {
        String instanceId = startWithUsers("vpreject");
        approveService.handleTask("dept_mgr_vpreject", firstTodoTaskId("dept_mgr_vpreject"), FlowApproveAction.APPROVE, null);
        approveService.handleTask("vp_vpreject", firstTodoTaskId("vp_vpreject"), FlowApproveAction.REJECT, "超预算，驳回");
        Map<String, Object> detail = approveService.instanceDetail(instanceId);
        assertEquals(Boolean.TRUE, detail.get("finished"));
        assertEquals(END_ACTIVITY_REJECTED, detail.get("endActivityId"));
    }

    @Test
    void doneListReflectsHandledTaskAndCommentRecorded() {
        String instanceId = startWithUsers("done");
        String deptTaskId = firstTodoTaskId("dept_mgr_done");
        approveService.handleTask("dept_mgr_done", deptTaskId, FlowApproveAction.APPROVE, "同意，票据齐全");
        assertTrue(approveService.listTodoTasks("dept_mgr_done").isEmpty(), "办理后待办应为空");
        List<Map<String, Object>> done = approveService.listDoneTasks("dept_mgr_done");
        assertTrue(done.stream().anyMatch(t -> deptTaskId.equals(t.get("taskId"))), "已办应包含该任务");
        assertNotNull(done.get(0).get("endTime"));
        Map<String, Object> detail = approveService.instanceDetail(instanceId);
        List<Map<String, Object>> taskTrace = (List<Map<String, Object>>) detail.get("taskTrace");
        Map<String, Object> deptTask = taskTrace.stream()
                .filter(t -> deptTaskId.equals(t.get("taskId")))
                .findFirst()
                .orElseThrow();
        assertTrue(((List<?>) deptTask.get("comments")).contains("同意，票据齐全"), "审批意见应落历史档");
    }

    @Test
    void handleByWrongUserRejectedWithForbidden() {
        startWithUsers("wrong");
        String deptTaskId = firstTodoTaskId("dept_mgr_wrong");
        JeecgBootBizTipException e = assertThrows(JeecgBootBizTipException.class,
                () -> approveService.handleTask(USER_STRANGER, deptTaskId, FlowApproveAction.APPROVE, null));
        assertEquals(ERR_CODE_FORBIDDEN, e.getErrCode());
    }

    @Test
    void doubleHandleSameTaskSecondOneConflicts() {
        startWithUsers("double");
        String deptTaskId = firstTodoTaskId("dept_mgr_double");
        approveService.handleTask("dept_mgr_double", deptTaskId, FlowApproveAction.APPROVE, null);
        JeecgBootBizTipException e = assertThrows(JeecgBootBizTipException.class,
                () -> approveService.handleTask("dept_mgr_double", deptTaskId, FlowApproveAction.APPROVE, null));
        assertEquals(ERR_CODE_CONFLICT, e.getErrCode());
    }

    @Test
    void handleNonExistentTaskConflicts() {
        JeecgBootBizTipException e = assertThrows(JeecgBootBizTipException.class,
                () -> approveService.handleTask("dept_mgr_x", "not-exist-task", FlowApproveAction.APPROVE, null));
        assertEquals(ERR_CODE_CONFLICT, e.getErrCode());
    }

    @Test
    void startValidationRejectsIllegalInput() {
        assertThrows(JeecgBootBizTipException.class, () -> expenseService.startExpenseProcess(
                USER_APPLICANT, null, "事由", null, "m1", "v1"));
        assertThrows(JeecgBootBizTipException.class, () -> expenseService.startExpenseProcess(
                USER_APPLICANT, BigDecimal.ZERO, "事由", null, "m1", "v1"));
        assertThrows(JeecgBootBizTipException.class, () -> expenseService.startExpenseProcess(
                USER_APPLICANT, new BigDecimal("12.345"), "事由", null, "m1", "v1"));
        assertThrows(JeecgBootBizTipException.class, () -> expenseService.startExpenseProcess(
                USER_APPLICANT, new BigDecimal("100"), "  ", null, "m1", "v1"));
        assertThrows(JeecgBootBizTipException.class, () -> expenseService.startExpenseProcess(
                USER_APPLICANT, new BigDecimal("100"), "事由", null, " ", "v1"));
    }

    @Test
    void instanceDetailRejectsUnknownInstance() {
        JeecgBootBizTipException e = assertThrows(JeecgBootBizTipException.class,
                () -> approveService.instanceDetail("not-exist-instance"));
        assertEquals(ERR_CODE_CONFLICT, e.getErrCode());
    }

    @Test
    void processListContainsExpenseDefinition() {
        List<Map<String, Object>> definitions = approveService.listProcessDefinitions("u_test");
        assertTrue(definitions.stream().anyMatch(d ->
                FlowExpenseService.PROCESS_KEY_EXPENSE.equals(d.get("processDefinitionKey"))));
    }

    @Test
    void deploymentRunnerIsIdempotent() throws Exception {
        // 测试引擎初态只有报销定义；runner 补齐请假定义后，重复执行不得再新增版本
        FlowDeploymentConfig config = new FlowDeploymentConfig();
        config.flowDeploymentRunner(engine.getRepositoryService()).run();
        config.flowDeploymentRunner(engine.getRepositoryService()).run();
        assertEquals(1, engine.getRepositoryService().createProcessDefinitionQuery()
                .processDefinitionKey(FlowExpenseService.PROCESS_KEY_EXPENSE)
                .count(), "重复执行部署不得产生新版本");
        assertEquals(2, approveService.listProcessDefinitions("u_test").size(), "请假+报销两条定义，重复运行不得增多");
    }
}
