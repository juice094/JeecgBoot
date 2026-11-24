package org.jeecg.modules.flow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.jeecg.common.exception.JeecgBootBizTipException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * team 审批链引擎镜像同步器测试（W3）：H2 引擎验证
 * businessKey 锚定、approve/reject/cancel 同步语义、观测快照口径、异常上抛（由 team 侧决定阻断或降级）。
 */
class FlowTeamApprovalSyncServiceTest {

    private static final String EXPENSE_RESOURCE = "processes/expense-process.bpmn20.xml";
    private static final String CODE = "OA-20261123-001";
    private static ProcessEngine engine;
    private static FlowTeamApprovalSyncService syncService;

    @BeforeAll
    static void setUpEngine() {
        engine = new StandaloneInMemProcessEngineConfiguration()
                .setHistoryLevel(HistoryLevel.AUDIT)
                .setAsyncExecutorActivate(false)
                .buildProcessEngine();
        engine.getRepositoryService().createDeployment()
                .name("test-team-approval-mirror")
                .addClasspathResource(EXPENSE_RESOURCE)
                .deploy();
        syncService = new FlowTeamApprovalSyncService();
        ReflectionTestUtils.setField(syncService, "taskService", engine.getTaskService());
        ReflectionTestUtils.setField(syncService, "runtimeService", engine.getRuntimeService());
        ReflectionTestUtils.setField(syncService, "historyService", engine.getHistoryService());
    }

    @AfterAll
    static void closeEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    private String startMirror(String tag) {
        return syncService.startInstance(CODE + "-" + tag, "applicant_" + tag,
                new BigDecimal("100"), "dept_" + tag, "vp_" + tag);
    }

    private String onlyTaskKey(String processInstanceId) {
        List<Task> tasks = engine.getTaskService().createTaskQuery()
                .processInstanceId(processInstanceId).list();
        assertEquals(1, tasks.size());
        return tasks.get(0).getTaskDefinitionKey();
    }

    @Test
    void startInstanceAnchorsBusinessKeyAndVariables() {
        String instanceId = startMirror("anchor");
        ProcessInstance instance = engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        assertNotNull(instance);
        assertEquals(CODE + "-anchor", instance.getBusinessKey());
        assertEquals("dept_anchor", engine.getRuntimeService().getVariable(instanceId, "deptManager"));
        assertEquals(CODE + "-anchor", engine.getRuntimeService().getVariable(instanceId, "approvalCode"));
        assertEquals(0, new BigDecimal("100")
                .compareTo((BigDecimal) engine.getRuntimeService().getVariable(instanceId, "amount")));
        assertEquals("RUNNING:deptApprove", syncService.observeState(instanceId));
    }

    @Test
    void startInstanceRejectsBlankArguments() {
        assertThrows(JeecgBootBizTipException.class, () -> syncService.startInstance(
                " ", "applicant", null, "dept", "vp"));
        assertThrows(JeecgBootBizTipException.class, () -> syncService.startInstance(
                CODE, "applicant", null, " ", "vp"));
    }

    @Test
    void syncApproveAdvancesThenFinishesAtApprovedEnd() {
        String instanceId = startMirror("approve");
        syncService.syncApprove(instanceId);
        assertEquals("vpApprove", onlyTaskKey(instanceId));
        assertEquals("RUNNING:vpApprove", syncService.observeState(instanceId));

        syncService.syncApprove(instanceId);
        assertEquals("FINISHED:approvedEnd", syncService.observeState(instanceId));
    }

    @Test
    void syncRejectFinishesImmediatelyAtRejectedEnd() {
        String instanceId = startMirror("reject");
        syncService.syncReject(instanceId);
        assertEquals("FINISHED:rejectedEnd", syncService.observeState(instanceId));
    }

    @Test
    void syncCancelDeletesInstanceAndObserveStateDeleted() {
        String instanceId = startMirror("cancel");
        syncService.syncCancel(instanceId);
        assertEquals("DELETED", syncService.observeState(instanceId));
        // 幂等：已删除的实例再次 cancel 不抛错
        syncService.syncCancel(instanceId);
    }

    @Test
    void syncApproveWithoutOpenTaskThrows() {
        String instanceId = startMirror("gone");
        syncService.syncReject(instanceId);
        JeecgBootBizTipException e = assertThrows(JeecgBootBizTipException.class,
                () -> syncService.syncApprove(instanceId));
        assertEquals(409, e.getErrCode());
    }

    @Test
    void observeStateNotFoundForBlankOrUnknown() {
        assertEquals("NOT_FOUND", syncService.observeState(null));
        assertEquals("NOT_FOUND", syncService.observeState("not-exist"));
    }

    @Test
    void engineSideQueryByBusinessKeyResolvesBackToCode() {
        String instanceId = startMirror("bk");
        ProcessInstance instance = engine.getRuntimeService().createProcessInstanceQuery()
                .processDefinitionKey(FlowTeamApprovalSyncService.PROCESS_KEY)
                .processInstanceBusinessKey(CODE + "-bk")
                .singleResult();
        assertNotNull(instance);
        assertEquals(instanceId, instance.getId());
    }
}
