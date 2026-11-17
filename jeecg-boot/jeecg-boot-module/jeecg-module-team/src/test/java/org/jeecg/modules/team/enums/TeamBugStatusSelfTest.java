package org.jeecg.modules.team.enums;

/**
 * 纯 Java 自测（无 JUnit 依赖前提下的最小可运行检查）：
 * 模块 pom 无 test 依赖且 pom 为禁区，暂以 main + 显式断言执行，
 * 后续 pom 开放 test 依赖后迁移为 JUnit 用例。
 *
 * 运行方式：mvn test-compile 后
 *   java -ea -cp <module>/target/classes;<module>/target/test-classes org.jeecg.modules.team.enums.TeamBugStatusSelfTest
 */
public final class TeamBugStatusSelfTest {

    private static int checked = 0;

    public static void main(String[] args) {
        // 合法正向流转
        assertTransit("open", "processing", true);
        assertTransit("open", "closed", true);
        assertTransit("processing", "resolved", true);
        assertTransit("processing", "open", true);
        assertTransit("resolved", "closed", true);
        assertTransit("resolved", "open", true);

        // 非法流转
        assertTransit("open", "resolved", false);
        assertTransit("open", "open", false);
        assertTransit("processing", "closed", false);
        assertTransit("resolved", "processing", false);
        assertTransit("closed", "open", false);
        assertTransit("closed", "processing", false);

        // 非法状态码 / 边界
        assertTrue(TeamBugStatus.fromCode("junk") == null, "非法码解析应为 null");
        assertTrue(TeamBugStatus.fromCode(null) == null, "null 码解析应为 null");
        assertTrue(TeamBugStatus.fromCode(" OPEN ") == TeamBugStatus.OPEN, "码应忽略大小写与首尾空白");

        System.out.println("TeamBugStatusSelfTest PASSED, assertions=" + checked);
    }

    private static void assertTransit(String fromCode, String toCode, boolean expected) {
        TeamBugStatus from = TeamBugStatus.fromCode(fromCode);
        TeamBugStatus to = TeamBugStatus.fromCode(toCode);
        assertTrue(from.canTransitTo(to) == expected,
                "transit " + fromCode + " -> " + toCode + " expected " + expected);
    }

    private static void assertTrue(boolean condition, String message) {
        checked++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private TeamBugStatusSelfTest() {
    }
}
