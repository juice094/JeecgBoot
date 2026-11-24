package org.jeecg.modules.team.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.jeecg.common.exception.JeecgBootBizTipException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.flow.service.FlowApproverResolver;
import org.jeecg.modules.flow.service.FlowTeamApprovalSyncService;
import org.jeecg.modules.team.entity.TeamApproval;
import org.jeecg.modules.team.entity.TeamApprovalFlowRecon;
import org.jeecg.modules.team.entity.TeamApprovalStep;
import org.jeecg.modules.team.enums.TeamApprovalStatus;
import org.jeecg.modules.team.mapper.TeamApprovalFlowReconMapper;
import org.jeecg.modules.team.mapper.TeamApprovalMapper;
import org.jeecg.modules.team.service.ITeamApprovalService;
import org.jeecg.modules.team.service.ITeamApprovalTemplateService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;

/**
 * team 审批链 ↔ Flowable 双写集成测试（W3）：H2 内存库（team 表 + sys 组织表）
 * + 独立 Flowable 引擎（H2），验证四类验收口径：
 * <ul>
 *   <li>双写正向：submit 起镜像实例（businessKey=单号）锚定 process_instance_id，
 *       两级 approve 后引擎 FINISHED:approvedEnd、对账零差异；</li>
 *   <li>同步失败不阻断主流程：观测态引擎同步抛错时 team 流转照常、对账行 diff=1 留痕；</li>
 *   <li>开关两态：engine-driven=false（默认）观测态不阻断；true 时引擎异常阻断 team 流转，
 *       引擎恢复后同一单据可继续驱动；</li>
 *   <li>对账检出：引擎侧被外部动作终结后 team 再流转，对账行标记不一致。</li>
 * </ul>
 */
@SpringJUnitConfig(TeamApprovalFlowSyncerTest.TestConfig.class)
class TeamApprovalFlowSyncerTest {

    private static final String DB_URL =
            "jdbc:h2:mem:teamfloww3;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,COMMENT";
    private static final String EXPENSE_RESOURCE = "processes/expense-process.bpmn20.xml";

    private static ProcessEngine engine;
    /** 可注入故障的镜像同步器：failSync=true 时同步动作抛错，模拟引擎侧异常 */
    private static FailingSyncService syncService;

    @Autowired
    private ITeamApprovalService approvalService;
    @Autowired
    private TeamApprovalMapper approvalMapper;
    @Autowired
    private TeamApprovalFlowReconMapper reconMapper;
    @Autowired
    private TeamApprovalFlowSyncer flowSyncer;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterAll
    static void closeEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    @BeforeEach
    void cleanBizTables() {
        reconMapper.delete(new LambdaQueryWrapper<TeamApprovalFlowRecon>());
        jdbcTemplate.update("DELETE FROM team_approval_step");
        jdbcTemplate.update("DELETE FROM team_approval");
        jdbcTemplate.update("DELETE FROM team_audit_log");
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("DELETE FROM sys_user_depart");
        jdbcTemplate.update("DELETE FROM sys_position");
        jdbcTemplate.update("DELETE FROM sys_user_position");
        jdbcTemplate.update("DELETE FROM sys_role");
        jdbcTemplate.update("DELETE FROM sys_user_role");
        syncService.failSync = false;
        setEngineDriven(false);
    }

    private void setEngineDriven(boolean driven) {
        // approvalService 是事务代理，@Value 字段需打在原始目标实例上
        ReflectionTestUtils.setField(TestConfig.rawService, "engineDriven", driven);
    }

    // ------------------------------------------------------------------
    // 数据准备辅助
    // ------------------------------------------------------------------

    private void addSysUser(String id, String username, String realname) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, realname, status, del_flag) VALUES (?,?,?,1,'0')",
                id, username, realname);
    }

    private void addDepartMember(String userId, String depId) {
        jdbcTemplate.update("INSERT INTO sys_user_depart (id, user_id, dep_id) VALUES (?,?,?)",
                "ud-" + userId, userId, depId);
    }

    private void addPosition(String userId, int postLevel, String name) {
        String positionId = "pos-" + postLevel + "-" + userId;
        jdbcTemplate.update("INSERT INTO sys_position (id, name, post_level) VALUES (?,?,?)", positionId, name, postLevel);
        jdbcTemplate.update("INSERT INTO sys_user_position (id, user_id, position_id) VALUES (?,?,?)",
                "upp-" + userId, userId, positionId);
    }

    private void addRole(String userId, String roleCode) {
        String roleId = "role-" + roleCode;
        jdbcTemplate.update("INSERT INTO sys_role (id, role_code) VALUES (?,?)", roleId, roleCode);
        jdbcTemplate.update("INSERT INTO sys_user_role (id, user_id, role_id) VALUES (?,?,?)",
                "ur-" + userId, userId, roleId);
    }

    private LoginUser loginUser(String id, String username, String realname) {
        LoginUser user = new LoginUser();
        user.setId(id);
        user.setUsername(username);
        user.setRealname(realname);
        return user;
    }

    private MockedStatic<SecurityUtils> mockShiro(LoginUser principal) {
        MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class);
        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(principal);
        ms.when(SecurityUtils::getSubject).thenReturn(subject);
        return ms;
    }

    /** 二级审批链单据：步骤审批人与组织数据无关时走步骤兜底 */
    private TeamApproval newTwoStepApproval(String tag) {
        TeamApproval approval = new TeamApproval();
        approval.setCategory("加班申请");
        approval.setTitle("W3双写测试-" + tag);
        approval.setApplicantId("u_app");
        approval.setApplicantName("申请人甲");
        approval.setAmount(new BigDecimal("66.00"));
        return approval;
    }

    private List<TeamApprovalStep> twoSteps(String mgrId, String mgrName, String vpId, String vpName) {
        List<TeamApprovalStep> steps = new ArrayList<>(2);
        TeamApprovalStep step1 = new TeamApprovalStep();
        step1.setApproverId(mgrId);
        step1.setApproverName(mgrName);
        step1.setApproverRole("部门主管");
        TeamApprovalStep step2 = new TeamApprovalStep();
        step2.setApproverId(vpId);
        step2.setApproverName(vpName);
        step2.setApproverRole("副总");
        steps.add(step1);
        steps.add(step2);
        return steps;
    }

    private TeamApprovalFlowRecon reconOf(String approvalId) {
        return reconMapper.selectOne(new LambdaQueryWrapper<TeamApprovalFlowRecon>()
                .eq(TeamApprovalFlowRecon::getApprovalId, approvalId));
    }

    private String openTaskAssignee(String processInstanceId) {
        List<Task> tasks = engine.getTaskService().createTaskQuery()
                .processInstanceId(processInstanceId).list();
        assertEquals(1, tasks.size());
        return tasks.get(0).getAssignee();
    }

    // ------------------------------------------------------------------
    // 用例
    // ------------------------------------------------------------------

    @Test
    void submitAnchorsMirrorWithBusinessKeyAndOrgResolvedApprovers() {
        // 组织数据：部长（组织解析应胜出）与 vp 角色用户（角色解析应胜出）
        addSysUser("u_app", "applicant", "申请人甲");
        addSysUser("u_org_mgr", "org_minister", "组织部长");
        addSysUser("u_org_vp", "org_vp_user", "组织副总");
        addSysUser("u_step1", "step_mgr", "步骤主管");
        addSysUser("u_step2", "step_vp", "步骤副总");
        addDepartMember("u_app", "depA");
        addDepartMember("u_org_mgr", "depA");
        addPosition("u_org_mgr", 4, "部长");
        addRole("u_org_vp", "vp");

        TeamApproval approval = newTwoStepApproval("org");
        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u_app", "applicant", "申请人甲"))) {
            approvalService.submit(approval, twoSteps("u_step1", "步骤主管", "u_step2", "步骤副总"));
        }

        TeamApproval saved = approvalMapper.selectById(approval.getId());
        assertNotNull(saved.getProcessInstanceId(), "提交后应锚定流程实例");
        assertEquals(approval.getCode(),
                engine.getRuntimeService().createProcessInstanceQuery()
                        .processInstanceId(saved.getProcessInstanceId())
                        .singleResult()
                        .getBusinessKey(),
                "引擎 businessKey 应=申请单号");

        // 组织解析优先于步骤兜底：一级任务应派给部长（而非步骤上的"步骤主管"）
        assertEquals("org_minister", openTaskAssignee(saved.getProcessInstanceId()));

        // team 侧按链推进（步骤审批人=步骤主管），同步后二级任务应派给 vp 角色用户
        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u_step1", "step_mgr", "步骤主管"))) {
            approvalService.approve(approval.getId(), "同意");
        }
        assertEquals("org_vp_user", openTaskAssignee(saved.getProcessInstanceId()));

        TeamApprovalFlowRecon recon = reconOf(approval.getId());
        assertNotNull(recon);
        assertEquals(Integer.valueOf(0), recon.getDiffFlag(), "对账应一致");
        assertEquals("RUNNING:vpApprove", recon.getEngineStatus());
    }

    @Test
    void fullTwoLevelApproveEndsWithZeroDiff() {
        addSysUser("u_app", "applicant", "申请人甲");
        addSysUser("u_m1", "mgr1", "主管一");
        addSysUser("u_v1", "vp1", "副总一");

        TeamApproval approval = newTwoStepApproval("full");
        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u_app", "applicant", "申请人甲"))) {
            approvalService.submit(approval, twoSteps("u_m1", "主管一", "u_v1", "副总一"));
        }

        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u_m1", "mgr1", "主管一"))) {
            approvalService.approve(approval.getId(), "同意");
        }
        TeamApprovalFlowRecon midRecon = reconOf(approval.getId());
        assertEquals(Integer.valueOf(0), midRecon.getDiffFlag(), "一级批准后对账应一致");
        assertEquals("RUNNING:vpApprove", midRecon.getEngineStatus());
        assertEquals(TeamApprovalStatus.PENDING.getCode(),
                approvalMapper.selectById(approval.getId()).getStatus());

        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u_v1", "vp1", "副总一"))) {
            approvalService.approve(approval.getId(), "同意");
        }
        assertEquals(TeamApprovalStatus.APPROVED.getCode(),
                approvalMapper.selectById(approval.getId()).getStatus());
        TeamApprovalFlowRecon finalRecon = reconOf(approval.getId());
        assertEquals(Integer.valueOf(0), finalRecon.getDiffFlag(), "两级批准后对账应零差异");
        assertEquals("FINISHED:approvedEnd", finalRecon.getEngineStatus());
    }

    @Test
    void syncFailureDoesNotBlockTeamFlowInObservationMode() {
        addSysUser("u_app", "applicant", "申请人甲");
        addSysUser("u_m1", "mgr1", "主管一");
        addSysUser("u_v1", "vp1", "副总一");

        TeamApproval approval = newTwoStepApproval("fail");
        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u_app", "applicant", "申请人甲"))) {
            approvalService.submit(approval, twoSteps("u_m1", "主管一", "u_v1", "副总一"));
        }
        syncService.failSync = true;

        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u_m1", "mgr1", "主管一"))) {
            approvalService.approve(approval.getId(), "同意");
        }
        assertEquals(TeamApprovalStatus.PENDING.getCode(),
                approvalMapper.selectById(approval.getId()).getStatus(), "同步失败不得阻断 team 主流程");
        TeamApprovalFlowRecon recon = reconOf(approval.getId());
        assertEquals(Integer.valueOf(1), recon.getDiffFlag(), "同步失败应登记不一致");
        assertEquals("SYNC_FAILED", recon.getEngineStatus());
        assertNotNull(recon.getDetail());
    }

    @Test
    void engineDrivenModeBlocksOnEngineFailureAndRecovers() {
        addSysUser("u_app", "applicant", "申请人甲");
        addSysUser("u_m1", "mgr1", "主管一");
        addSysUser("u_v1", "vp1", "副总一");

        TeamApproval approval = newTwoStepApproval("driven");
        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u_app", "applicant", "申请人甲"))) {
            approvalService.submit(approval, twoSteps("u_m1", "主管一", "u_v1", "副总一"));
        }
        setEngineDriven(true);
        syncService.failSync = true;

        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u_m1", "mgr1", "主管一"))) {
            assertThrows(JeecgBootBizTipException.class,
                    () -> approvalService.approve(approval.getId(), "同意"),
                    "引擎驱动态：引擎异常应阻断 team 流转");
        }
        TeamApproval afterFailure = approvalMapper.selectById(approval.getId());
        assertEquals(Integer.valueOf(1), afterFailure.getCurrentStep(), "阻断后 team 步骤不得推进");
        assertEquals(TeamApprovalStatus.PENDING.getCode(), afterFailure.getStatus());

        syncService.failSync = false;
        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u_m1", "mgr1", "主管一"))) {
            approvalService.approve(approval.getId(), "同意");
        }
        assertEquals(Integer.valueOf(2), approvalMapper.selectById(approval.getId()).getCurrentStep(),
                "引擎恢复后同一单据应可由引擎驱动继续");
    }

    @Test
    void reconDetectsEngineSideDrift() {
        addSysUser("u_app", "applicant", "申请人甲");
        addSysUser("u_m1", "mgr1", "主管一");
        addSysUser("u_v1", "vp1", "副总一");

        TeamApproval approval = newTwoStepApproval("drift");
        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u_app", "applicant", "申请人甲"))) {
            approvalService.submit(approval, twoSteps("u_m1", "主管一", "u_v1", "副总一"));
        }
        // 引擎侧被外部动作终结（模拟双写期漂移），team 表仍正常流转
        syncService.syncReject(approvalMapper.selectById(approval.getId()).getProcessInstanceId());

        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u_m1", "mgr1", "主管一"))) {
            approvalService.approve(approval.getId(), "同意");
        }
        TeamApprovalFlowRecon recon = reconOf(approval.getId());
        assertEquals(Integer.valueOf(1), recon.getDiffFlag(), "引擎侧漂移应被对账检出");
        assertEquals("SYNC_FAILED", recon.getEngineStatus());
        assertTrue(flowSyncer.listDiffs().stream()
                .anyMatch(r -> approval.getCode().equals(r.getApprovalCode())));
    }

    @Test
    void nonTwoLevelChainSkipsEngineMirror() {
        addSysUser("u_app", "applicant", "申请人甲");
        addSysUser("u_m1", "mgr1", "主管一");

        TeamApproval approval = newTwoStepApproval("one");
        List<TeamApprovalStep> oneStep = twoSteps("u_m1", "主管一", "u_v1", "副总一").subList(0, 1);
        try (MockedStatic<SecurityUtils> ignored = mockShiro(loginUser("u_app", "applicant", "申请人甲"))) {
            approvalService.submit(approval, oneStep);
        }
        assertNull(approvalMapper.selectById(approval.getId()).getProcessInstanceId(),
                "非二级链不应起引擎镜像");
        assertNull(reconOf(approval.getId()), "非二级链不登记对账");
    }

    // ------------------------------------------------------------------
    // Spring 测试配置
    // ------------------------------------------------------------------

    /** 故障可注入的镜像同步器（观测态失败不阻断 / 驱动态失败阻断两态共用它） */
    static class FailingSyncService extends FlowTeamApprovalSyncService {
        volatile boolean failSync = false;

        @Override
        public void syncApprove(String processInstanceId) {
            if (failSync) {
                throw new JeecgBootBizTipException("模拟引擎同步失败", 409);
            }
            super.syncApprove(processInstanceId);
        }

        @Override
        public void syncReject(String processInstanceId) {
            if (failSync) {
                throw new JeecgBootBizTipException("模拟引擎同步失败", 409);
            }
            super.syncReject(processInstanceId);
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan("org.jeecg.modules.team.mapper")
    static class TestConfig {

        /** 事务代理的原始目标实例，供测试直接拨动 engineDriven 开关 */
        static TeamApprovalServiceImpl rawService;

        @Bean
        public DataSource dataSource() {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(DB_URL);
            ds.setUsername("sa");
            ds.setPassword("");
            ds.setDriverClassName("org.h2.Driver");
            ds.setMaximumPoolSize(4);
            return ds;
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            GlobalConfig globalConfig = new GlobalConfig();
            GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
            dbConfig.setIdType(IdType.ASSIGN_ID);
            dbConfig.setTableUnderline(true);
            globalConfig.setDbConfig(dbConfig);
            factory.setGlobalConfig(globalConfig);
            SqlSessionFactory bean = factory.getObject();
            initSchema(new JdbcTemplate(dataSource));
            return bean;
        }

        private void initSchema(JdbcTemplate jdbc) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS team_approval ("
                    + "id VARCHAR(36) PRIMARY KEY, code VARCHAR(32) NOT NULL, category VARCHAR(32) NOT NULL, "
                    + "title VARCHAR(128) NOT NULL, reason VARCHAR(1024), form_data VARCHAR(4096), "
                    + "amount DECIMAL(12,2), status VARCHAR(16) NOT NULL, process_instance_id VARCHAR(64), "
                    + "applicant_id VARCHAR(36), applicant_name VARCHAR(64), start_date DATE, end_date DATE, "
                    + "current_step INT NOT NULL DEFAULT 1, deleted INT NOT NULL DEFAULT 0, "
                    + "create_by VARCHAR(32), create_time TIMESTAMP, update_by VARCHAR(32), update_time TIMESTAMP)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS team_approval_step ("
                    + "id VARCHAR(36) PRIMARY KEY, approval_id VARCHAR(36) NOT NULL, step_no INT NOT NULL, "
                    + "approver_id VARCHAR(36), approver_name VARCHAR(64) NOT NULL, approver_role VARCHAR(64), "
                    + "action VARCHAR(16) NOT NULL DEFAULT 'wait', comment VARCHAR(512), acted_at TIMESTAMP, "
                    + "create_time TIMESTAMP)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS team_approval_flow_recon ("
                    + "id VARCHAR(36) PRIMARY KEY, approval_id VARCHAR(36) NOT NULL, approval_code VARCHAR(32) NOT NULL, "
                    + "process_instance_id VARCHAR(64), team_status VARCHAR(16) NOT NULL, team_current_step INT, "
                    + "engine_status VARCHAR(64), diff_flag INT NOT NULL DEFAULT 0, detail VARCHAR(1024), "
                    + "create_time TIMESTAMP, update_time TIMESTAMP)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS team_audit_log ("
                    + "id VARCHAR(36) PRIMARY KEY, biz_type VARCHAR(32) NOT NULL, biz_id VARCHAR(36) NOT NULL, "
                    + "biz_code VARCHAR(128), action VARCHAR(32) NOT NULL, from_value VARCHAR(64), to_value VARCHAR(64), "
                    + "operator_id VARCHAR(36), operator_name VARCHAR(64), detail VARCHAR(1024), "
                    + "create_by VARCHAR(32), create_time TIMESTAMP, update_by VARCHAR(32), update_time TIMESTAMP)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS sys_user ("
                    + "id VARCHAR(36) PRIMARY KEY, username VARCHAR(64), realname VARCHAR(64), "
                    + "status INT, del_flag VARCHAR(1), create_time TIMESTAMP)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS sys_user_depart ("
                    + "id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36), dep_id VARCHAR(36))");
            jdbc.execute("CREATE TABLE IF NOT EXISTS sys_position ("
                    + "id VARCHAR(36) PRIMARY KEY, name VARCHAR(64), post_level INT)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS sys_user_position ("
                    + "id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36), position_id VARCHAR(36))");
            jdbc.execute("CREATE TABLE IF NOT EXISTS sys_role (id VARCHAR(36) PRIMARY KEY, role_code VARCHAR(64))");
            jdbc.execute("CREATE TABLE IF NOT EXISTS sys_user_role ("
                    + "id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36), role_id VARCHAR(36))");
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public ITeamApprovalService teamApprovalService() {
            rawService = new TeamApprovalServiceImpl();
            return rawService;
        }

        @Bean
        public TeamAuditLogger teamAuditLogger() {
            return new TeamAuditLogger();
        }

        @Bean
        public ITeamApprovalTemplateService teamApprovalTemplateService() {
            // 无模板：submit 回退客户端 steps（本测试关心双写，不覆盖模板线）
            return mock(ITeamApprovalTemplateService.class);
        }

        @Bean
        public FlowApproverResolver flowApproverResolver() {
            return new FlowApproverResolver();
        }

        @Bean
        public org.flowable.engine.TaskService taskService() {
            ensureEngine();
            return engine.getTaskService();
        }

        @Bean
        public org.flowable.engine.RuntimeService runtimeService() {
            ensureEngine();
            return engine.getRuntimeService();
        }

        @Bean
        public org.flowable.engine.HistoryService historyService() {
            ensureEngine();
            return engine.getHistoryService();
        }

        private void ensureEngine() {
            // Spring 测试上下文先于 @BeforeAll 创建，引擎在 Bean 构建时惰性初始化
            if (engine == null) {
                engine = new StandaloneInMemProcessEngineConfiguration()
                        .setHistoryLevel(HistoryLevel.AUDIT)
                        .setAsyncExecutorActivate(false)
                        .buildProcessEngine();
                engine.getRepositoryService().createDeployment()
                        .name("test-team-approval-w3")
                        .addClasspathResource(EXPENSE_RESOURCE)
                        .deploy();
            }
        }

        @Bean
        public FailingSyncService flowTeamApprovalSyncService() {
            ensureEngine();
            syncService = new FailingSyncService();
            ReflectionTestUtils.setField(syncService, "taskService", engine.getTaskService());
            ReflectionTestUtils.setField(syncService, "runtimeService", engine.getRuntimeService());
            ReflectionTestUtils.setField(syncService, "historyService", engine.getHistoryService());
            return syncService;
        }

        @Bean
        public TeamApprovalFlowSyncer teamApprovalFlowSyncer() {
            return new TeamApprovalFlowSyncer();
        }
    }
}
