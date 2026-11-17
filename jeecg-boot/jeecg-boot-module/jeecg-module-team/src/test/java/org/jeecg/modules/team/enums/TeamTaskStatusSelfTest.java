package org.jeecg.modules.team.enums;

/**
 * 纯 Java 自测（无 JUnit 依赖前提下的最小可运行检查，同 TeamBugStatusSelfTest）。
 *
 * 运行方式：mvn test-compile 后
 *   java -ea -cp <module>/target/classes;<module>/target/test-classes org.jeecg.modules.team.enums.TeamTaskStatusSelfTest
 */
public final class TeamTaskStatusSelfTest {

    private static int checked = 0;

    public static void main(String[] args) {
        // 合法正向流转
        assertTransit("todo", "doing", true);
        assertTransit("todo", "done", true);
        assertTransit("doing", "todo", true);
        assertTransit("doing", "done", true);
        assertTransit("done", "todo", true);
        assertTransit("done", "doing", true);

        // 非法流转
        assertTransit("todo", "todo", false);
        assertTransit("doing", "doing", false);
        assertTransit("done", "done", false);

        // 非法状态码 / 边界
        assertTrue(TeamTaskStatus.fromCode("junk") == null, "非法码解析应为 null");
        assertTrue(TeamTaskStatus.fromCode(null) == null, "null 码解析应为 null");
        assertTrue(TeamTaskStatus.fromCode(" Done ") == TeamTaskStatus.DONE, "码应忽略大小写与首尾空白");

        System.out.println("TeamTaskStatusSelfTest PASSED, assertions=" + checked);
    }

    private static void assertTransit(String fromCode, String toCode, boolean expected) {
        TeamTaskStatus from = TeamTaskStatus.fromCode(fromCode);
        TeamTaskStatus to = TeamTaskStatus.fromCode(toCode);
        assertTrue(from.canTransitTo(to) == expected,
                "transit " + fromCode + " -> " + toCode + " expected " + expected);
    }

    private static void assertTrue(boolean condition, String message) {
        checked++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private TeamTaskStatusSelfTest() {
    }
}
