package org.jeecg.modules.team.vo;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jeecg.modules.team.entity.TeamTask;

/**
 * 纯 Java 自测（无 JUnit 依赖前提下的最小可运行检查）：
 * TeamTaskBoard.of 分组逻辑——三态归列、脏状态丢弃、原顺序保留、空入参。
 *
 * 运行方式：mvn test-compile 后
 *   java -ea -cp <module>/target/classes;<module>/target/test-classes org.jeecg.modules.team.vo.TeamTaskBoardSelfTest
 */
public final class TeamTaskBoardSelfTest {

    private static int checked = 0;

    public static void main(String[] args) {
        TeamTask t1 = task("t1", "todo", 1);
        TeamTask t2 = task("t2", "doing", 2);
        TeamTask t3 = task("t3", "done", 3);
        TeamTask t4 = task("t4", "todo", 4);
        TeamTask dirty = task("t5", "junk", 5);

        TeamTaskBoard board = TeamTaskBoard.of(Arrays.asList(t1, t2, t3, t4, dirty));

        assertTrue(board.getTodo().size() == 2, "todo 列应为 2，实际 " + board.getTodo().size());
        assertTrue(board.getDoing().size() == 1, "doing 列应为 1");
        assertTrue(board.getDone().size() == 1, "done 列应为 1");
        assertTrue(board.getTodo().get(0).getId().equals("t1"), "组内顺序应保持输入顺序");
        assertTrue(board.getTodo().get(1).getId().equals("t4"), "组内顺序应保持输入顺序");
        assertTrue(board.getDoing().get(0).getId().equals("t2"), "doing 归列错误");

        // 空入参 / 全脏数据
        assertTrue(TeamTaskBoard.of(null).getTodo().isEmpty(), "null 入参应返回空板");
        assertTrue(TeamTaskBoard.of(Collections.emptyList()).getDone().isEmpty(), "空列表应返回空板");
        TeamTaskBoard dirtyOnly = TeamTaskBoard.of(Collections.singletonList(dirty));
        assertTrue(dirtyOnly.getTodo().isEmpty() && dirtyOnly.getDoing().isEmpty() && dirtyOnly.getDone().isEmpty(),
                "脏状态应丢弃不入列");

        // 三态码口径与枚举一致
        List<String> codes = TeamTaskBoard.boardStatusCodes();
        assertTrue(codes.size() == 3 && codes.contains("todo") && codes.contains("doing") && codes.contains("done"),
                "boardStatusCodes 应为 todo/doing/done 三态");

        System.out.println("TeamTaskBoardSelfTest PASSED, assertions=" + checked);
    }

    private static TeamTask task(String id, String status, int sortNo) {
        TeamTask task = new TeamTask();
        task.setId(id);
        task.setStatus(status);
        task.setSortNo(sortNo);
        return task;
    }

    private static void assertTrue(boolean condition, String message) {
        checked++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private TeamTaskBoardSelfTest() {
    }
}
