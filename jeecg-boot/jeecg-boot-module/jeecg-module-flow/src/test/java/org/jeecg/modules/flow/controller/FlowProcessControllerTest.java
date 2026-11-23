package org.jeecg.modules.flow.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.jeecg.common.exception.JeecgBootBizTipException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.flow.service.FlowApproveService;
import org.jeecg.modules.flow.service.FlowExpenseService;
import org.jeecg.modules.flow.service.FlowLeaveService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * /flow/process/start 统一收口的控制器级测试：H2 内存引擎 + Shiro 轻量主体（无 Spring 上下文）。
 * 覆盖：processKey 分派双分支、processKey/days 参数校验负路径、未登录 401 兜底。
 * 说明：@RequiresPermissions 由部署期 Shiro 过滤器 + AOP 保证，这里直接调用方法验证分派与未登录兜底两个可单测面。
 */
class FlowProcessControllerTest {

    private static final String USER_APPLICANT = "flow_start_tester";
    private static final String EXPENSE_RESOURCE = "processes/expense-process.bpmn20.xml";
    private static final String LEAVE_RESOURCE = "processes/leave-process.bpmn20.xml";
    private static final String PROCESS_KEY_EXPENSE = "expenseProcess";
    private static final String PROCESS_KEY_LEAVE = "leaveProcess";
    private static final int ERR_CODE_BAD_REQUEST = 400;
    private static final int ERR_CODE_UNAUTHORIZED = 401;

    private static ProcessEngine engine;
    private static FlowProcessController controller;

    @BeforeAll
    static void setUp() {
        engine = new StandaloneInMemProcessEngineConfiguration()
                .setHistoryLevel(HistoryLevel.AUDIT)
                .setAsyncExecutorActivate(false)
                .buildProcessEngine();
        engine.getRepositoryService().createDeployment()
                .name("test-start")
                .addClasspathResource(EXPENSE_RESOURCE)
                .addClasspathResource(LEAVE_RESOURCE)
                .deploy();
        FlowExpenseService expenseService = new FlowExpenseService();
        ReflectionTestUtils.setField(expenseService, "runtimeService", engine.getRuntimeService());
        FlowLeaveService leaveService = new FlowLeaveService();
        ReflectionTestUtils.setField(leaveService, "runtimeService", engine.getRuntimeService());
        FlowApproveService approveService = new FlowApproveService();
        ReflectionTestUtils.setField(approveService, "taskService", engine.getTaskService());
        ReflectionTestUtils.setField(approveService, "runtimeService", engine.getRuntimeService());
        ReflectionTestUtils.setField(approveService, "historyService", engine.getHistoryService());
        ReflectionTestUtils.setField(approveService, "repositoryService", engine.getRepositoryService());
        controller = new FlowProcessController();
        ReflectionTestUtils.setField(controller, "flowExpenseService", expenseService);
        ReflectionTestUtils.setField(controller, "flowLeaveService", leaveService);
        ReflectionTestUtils.setField(controller, "flowApproveService", approveService);
        ThreadContext.bind(new DefaultSecurityManager());
    }

    @AfterAll
    static void tearDown() {
        ThreadContext.unbindSecurityManager();
        if (engine != null) {
            engine.close();
        }
    }

    private void loginAs(String username) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUsername(username);
        PrincipalCollection principals = new SimplePrincipalCollection(loginUser, "testRealm");
        Subject subject = new Subject.Builder()
                .principals(principals)
                .authenticated(true)
                .buildSubject();
        ThreadContext.bind(subject);
    }

    @Test
    void anonymousStartRejectedWith401() {
        ThreadContext.unbindSubject();
        JeecgBootBizTipException e = assertThrows(JeecgBootBizTipException.class,
                () -> controller.start("leave", null, "病假", null, "2", null, null));
        assertEquals(ERR_CODE_UNAUTHORIZED, e.getErrCode());
    }

    @Test
    void startDispatchesToExpenseBranch() {
        loginAs(USER_APPLICANT);
        String message = controller.start("expense", new BigDecimal("88.00"), "差旅报销",
                "发票 1 张", null, "dept_mgr_a", "vp_a").getResult();
        assertNotNull(message);
        long running = engine.getRuntimeService().createProcessInstanceQuery()
                .processDefinitionKey(PROCESS_KEY_EXPENSE)
                .count();
        assertTrue(running >= 1, "报销分支应产生在途实例");
    }

    @Test
    void startDispatchesToLeaveBranch() {
        loginAs(USER_APPLICANT);
        String message = controller.start("leave", null, "病假", null, "2", null, null).getResult();
        assertNotNull(message);
        long running = engine.getRuntimeService().createProcessInstanceQuery()
                .processDefinitionKey(PROCESS_KEY_LEAVE)
                .count();
        assertTrue(running >= 1, "请假分支应产生在途实例");
    }

    @Test
    void unknownProcessKeyRejectedWithBadRequest() {
        loginAs(USER_APPLICANT);
        JeecgBootBizTipException e = assertThrows(JeecgBootBizTipException.class,
                () -> controller.start("unknown", null, "x", null, null, null, null));
        assertEquals(ERR_CODE_BAD_REQUEST, e.getErrCode());
    }

    @Test
    void leaveBranchRequiresDays() {
        loginAs(USER_APPLICANT);
        JeecgBootBizTipException e = assertThrows(JeecgBootBizTipException.class,
                () -> controller.start("leave", null, "病假", null, null, null, null));
        assertEquals(ERR_CODE_BAD_REQUEST, e.getErrCode());
    }

    @Test
    void leaveBranchRejectsNonIntegerDays() {
        loginAs(USER_APPLICANT);
        long before = engine.getRuntimeService().createProcessInstanceQuery()
                .processDefinitionKey(PROCESS_KEY_LEAVE).count();
        JeecgBootBizTipException e = assertThrows(JeecgBootBizTipException.class,
                () -> controller.start("leave", null, "病假", null, "3.5", null, null));
        assertEquals(ERR_CODE_BAD_REQUEST, e.getErrCode());
        assertEquals(before, engine.getRuntimeService().createProcessInstanceQuery()
                .processDefinitionKey(PROCESS_KEY_LEAVE).count(), "非整数天数不得新增实例");
    }

    @Test
    void expenseBranchStillValidatesOwnParams() {
        loginAs(USER_APPLICANT);
        assertThrows(JeecgBootBizTipException.class,
                () -> controller.start("expense", null, "差旅报销", null, null, "dm", "vp"));
    }
}
