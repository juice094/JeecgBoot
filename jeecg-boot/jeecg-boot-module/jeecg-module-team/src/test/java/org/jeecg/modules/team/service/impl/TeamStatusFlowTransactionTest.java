package org.jeecg.modules.team.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Date;
import java.util.List;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.jeecg.modules.team.entity.TeamAuditLog;
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.enums.TeamAuditAction;
import org.jeecg.modules.team.enums.TeamAuditBizType;
import org.jeecg.modules.team.mapper.TeamAuditLogMapper;
import org.jeecg.modules.team.mapper.TeamTaskMapper;
import org.jeecg.modules.team.service.ITeamTaskCommentService;
import org.jeecg.modules.team.service.ITeamTaskDependencyService;
import org.jeecg.modules.team.service.ITeamTaskService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;

/**
 * 状态流转 + 审计「同事务」集成测试（真实 MySQL，本地 127.0.0.1:3306/jeecg-boot）。
 *
 * 验证单元测试覆盖不到的核心不变量：changeStatus 的 @Transactional 是否真的把
 * 「状态更新」与「审计写入」放进同一事务、失败时一起回滚。
 *
 *  - successPathCommitsStatusAndAudit：审计正常 → 状态落库 + 审计行落库（均提交）。
 *  - auditFailureRollsBackStatus：审计写入抛异常 → 状态更新回滚（DB 仍为 todo）、无审计行。
 *
 * 前置：本地 MySQL 可连（root/root）。不可达时整类跳过（Assumptions），不阻塞无库环境。
 * 层级：集成（真实库 + Spring 事务代理 + MyBatis-Plus），非单元。
 */
@SpringJUnitConfig(TeamStatusFlowTransactionTest.TestConfig.class)
class TeamStatusFlowTransactionTest {

    private static final String DB_URL =
            "jdbc:mysql://127.0.0.1:3306/jeecg-boot?characterEncoding=UTF-8&useUnicode=true&useSSL=false&serverTimezone=Asia/Shanghai";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "root";

    @Autowired
    private ITeamTaskService taskService;

    @Autowired
    private TeamTaskMapper taskMapper;

    @Autowired
    private TeamAuditLogMapper auditLogMapper;

    @Autowired
    private TogglableAuditLogger togglableAuditLogger;

    @BeforeAll
    static void requireLocalMysql() {
        try (Connection ignored = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)) {
            // 可连，继续
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "本地 MySQL 不可达，跳过集成测试: " + e.getMessage());
        }
    }

    @Configuration
    @EnableTransactionManagement
    @MapperScan("org.jeecg.modules.team.mapper")
    static class TestConfig {

        @Bean
        public DataSource dataSource() {
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(DB_URL);
            ds.setUsername(DB_USER);
            ds.setPassword(DB_PASS);
            ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
            ds.setMaximumPoolSize(3);
            ds.setConnectionTimeout(5000);
            ds.setInitializationFailTimeout(-1);
            return ds;
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
            return factory.getObject();
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public ITeamTaskService teamTaskService() {
            return new TeamTaskServiceImpl();
        }

        @Bean
        public TogglableAuditLogger teamAuditLogger() {
            return new TogglableAuditLogger();
        }

        // F-09 归属校验组件（未登录上下文跳过，本测试 mock 的 principal 为 null）
        @Bean
        public TeamOwnershipGuard teamOwnershipGuard() {
            return new TeamOwnershipGuard();
        }

        // M3 评论线引入：TeamTaskServiceImpl 持有 ITeamTaskCommentService；
        // changeStatus 不使用它，mock 默认值即可满足依赖注入
        @Bean
        public ITeamTaskCommentService teamTaskCommentService() {
            return mock(ITeamTaskCommentService.class);
        }

        // P1 依赖线引入：changeStatus 完成时重算依赖方 blocked；
        // 本测试任务无依赖行，真实实现查询 team_task_dependency 为空即短路
        @Bean
        public ITeamTaskDependencyService teamTaskDependencyService() {
            return new TeamTaskDependencyServiceImpl();
        }
    }

    /** 可切换失败的业务审计器：fail=false 走真实插入，fail=true 抛异常模拟审计写失败。 */
    static class TogglableAuditLogger extends TeamAuditLogger {
        volatile boolean fail = false;

        @Override
        public void log(TeamAuditBizType bizType, String bizId, String bizCode, TeamAuditAction action,
                String fromValue, String toValue, String detail) {
            if (fail) {
                throw new IllegalStateException("模拟审计写入失败");
            }
            super.log(bizType, bizId, bizCode, action, fromValue, toValue, detail);
        }
    }

    private MockedStatic<SecurityUtils> mockShiro() {
        MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class);
        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(null);
        ms.when(SecurityUtils::getSubject).thenReturn(subject);
        return ms;
    }

    private String insertTodoTask(String title) {
        TeamTask t = new TeamTask();
        t.setId("it-" + System.nanoTime());
        t.setTitle(title);
        t.setStatus("todo");
        t.setPriority("中");
        t.setSortNo(0);
        t.setCreateTime(new Date());
        taskMapper.insert(t);
        return t.getId();
    }

    private void cleanup(String taskId) {
        taskMapper.deleteById(taskId);
        auditLogMapper.delete(new QueryWrapper<TeamAuditLog>().eq("biz_id", taskId));
    }

    @Test
    void successPathCommitsStatusAndAudit() {
        String id = insertTodoTask("集成测试-成功路径");
        togglableAuditLogger.fail = false;
        try (MockedStatic<SecurityUtils> ignored = mockShiro()) {
            taskService.changeStatus(id, "doing");

            assertEquals("doing", taskMapper.selectById(id).getStatus(), "状态应已提交为 doing");

            List<TeamAuditLog> logs = auditLogMapper.selectList(
                    new QueryWrapper<TeamAuditLog>().eq("biz_id", id));
            assertEquals(1, logs.size(), "应恰好写入一条审计");
            assertEquals("todo", logs.get(0).getFromValue());
            assertEquals("doing", logs.get(0).getToValue());
        } finally {
            cleanup(id);
        }
    }

    @Test
    void auditFailureRollsBackStatus() {
        String id = insertTodoTask("集成测试-回滚路径");
        togglableAuditLogger.fail = true;
        try (MockedStatic<SecurityUtils> ignored = mockShiro()) {
            assertThrows(IllegalStateException.class, () -> taskService.changeStatus(id, "doing"));

            assertEquals("todo", taskMapper.selectById(id).getStatus(),
                    "审计写失败后状态更新必须回滚，DB 仍为 todo");
            assertEquals(0, auditLogMapper.selectList(
                    new QueryWrapper<TeamAuditLog>().eq("biz_id", id)).size(), "不应留下审计行");
        } finally {
            togglableAuditLogger.fail = false;
            cleanup(id);
        }
    }
}
