package org.jeecg.modules.flow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 组织链解析口径测试（W3）：H2 内存库造组织数据，验证
 * 部门主管=同学部管理职级（部长优先于副部长）、副总=角色解析、兜底返回 null 不抛错。
 */
class FlowApproverResolverTest {

    private static final String DB_URL =
            "jdbc:h2:mem:resolver;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER";
    private static JdbcTemplate jdbcTemplate;
    private static FlowApproverResolver resolver;

    @BeforeAll
    static void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl(DB_URL);
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        jdbcTemplate = new JdbcTemplate(dataSource);
        resolver = new FlowApproverResolver();
        ReflectionTestUtils.setField(resolver, "jdbcTemplate", jdbcTemplate);
        jdbcTemplate.execute("CREATE TABLE sys_user ("
                + "id VARCHAR(36) PRIMARY KEY, username VARCHAR(64), realname VARCHAR(64), "
                + "status INT, del_flag VARCHAR(1), create_time TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE sys_user_depart ("
                + "id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36), dep_id VARCHAR(36))");
        jdbcTemplate.execute("CREATE TABLE sys_position ("
                + "id VARCHAR(36) PRIMARY KEY, name VARCHAR(64), post_level INT)");
        jdbcTemplate.execute("CREATE TABLE sys_user_position ("
                + "id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36), position_id VARCHAR(36))");
        jdbcTemplate.execute("CREATE TABLE sys_role (id VARCHAR(36) PRIMARY KEY, role_code VARCHAR(64))");
        jdbcTemplate.execute("CREATE TABLE sys_user_role ("
                + "id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(36), role_id VARCHAR(36))");
    }

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM sys_user");
        jdbcTemplate.update("DELETE FROM sys_user_depart");
        jdbcTemplate.update("DELETE FROM sys_position");
        jdbcTemplate.update("DELETE FROM sys_user_position");
        jdbcTemplate.update("DELETE FROM sys_role");
        jdbcTemplate.update("DELETE FROM sys_user_role");
    }

    private void addUser(String id, String username, String realname) {
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

    @Test
    void resolvesMinisterBeforeDeputyMinister() {
        addUser("u_app", "app", "申请人");
        addUser("u_minister", "minister", "部长甲");
        addUser("u_deputy", "deputy", "副部长乙");
        addDepartMember("u_app", "dep1");
        addDepartMember("u_minister", "dep1");
        addDepartMember("u_deputy", "dep1");
        addPosition("u_minister", 4, "部长");
        addPosition("u_deputy", 5, "副部长");

        assertEquals("minister", resolver.resolveDeptManagerUsername("u_app"));
    }

    @Test
    void resolvesDeputyMinisterWhenNoMinister() {
        addUser("u_app", "app", "申请人");
        addUser("u_deputy", "deputy", "副部长乙");
        addDepartMember("u_app", "dep1");
        addDepartMember("u_deputy", "dep1");
        addPosition("u_deputy", 5, "副部长");

        assertEquals("deputy", resolver.resolveDeptManagerUsername("u_app"));
    }

    @Test
    void returnsNullWhenNoManagerLevelInDepart() {
        addUser("u_app", "app", "申请人");
        addUser("u_staff", "staff", "职员丙");
        addDepartMember("u_app", "dep1");
        addDepartMember("u_staff", "dep1");
        addPosition("u_staff", 6, "职员");

        assertNull(resolver.resolveDeptManagerUsername("u_app"));
    }

    @Test
    void returnsNullWhenApplicantHasNoDepart() {
        addUser("u_app", "app", "申请人");
        assertNull(resolver.resolveDeptManagerUsername("u_app"));
        assertNull(resolver.resolveDeptManagerUsername(" "));
        assertNull(resolver.resolveDeptManagerUsername(null));
    }

    @Test
    void resolvesVpByRoleCode() {
        addUser("u_vp", "vpuser", "副总丁");
        jdbcTemplate.update("INSERT INTO sys_role (id, role_code) VALUES ('r1','vp')");
        jdbcTemplate.update("INSERT INTO sys_user_role (id, user_id, role_id) VALUES ('ur1','u_vp','r1')");

        assertEquals("vpuser", resolver.resolveVpUsername("u_app"));
    }

    @Test
    void returnsNullWhenVpRoleAbsent() {
        assertNull(resolver.resolveVpUsername("u_app"));
    }

    @Test
    void resolvesUsernameByIdAndToleratesUnknown() {
        addUser("u1", "zhangsan", "张三");
        assertEquals("zhangsan", resolver.resolveUsernameById("u1"));
        assertNull(resolver.resolveUsernameById("nobody"));
        assertNull(resolver.resolveUsernameById(null));
    }
}
