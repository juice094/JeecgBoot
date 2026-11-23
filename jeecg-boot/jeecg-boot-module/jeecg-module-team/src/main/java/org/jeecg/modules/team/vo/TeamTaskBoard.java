package org.jeecg.modules.team.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.enums.TeamTaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 团队协作-任务看板 VO（/team/task/board）
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 * <p>
 * 响应示例：
 * { "todo": [ {任务...} ], "doing": [ {任务...} ], "done": [ {任务...} ] }
 * 组内排序由查询保证（sort_no asc, create_time desc），本类只负责分组。
 */
@Data
@Schema(description = "团队协作-任务看板（按状态分三组）")
public class TeamTaskBoard implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "待办列")
    private List<TeamTask> todo = new ArrayList<>();

    @Schema(description = "进行中列")
    private List<TeamTask> doing = new ArrayList<>();

    @Schema(description = "已完成列")
    private List<TeamTask> done = new ArrayList<>();

    /**
     * 平铺任务列表按状态码分入三列；不在三态内的脏数据丢弃（不入任何列）。
     * 纯静态逻辑，供无库自测。
     */
    public static TeamTaskBoard of(List<TeamTask> tasks) {
        TeamTaskBoard board = new TeamTaskBoard();
        if (tasks == null || tasks.isEmpty()) {
            return board;
        }
        board.setTodo(collect(tasks, TeamTaskStatus.TODO));
        board.setDoing(collect(tasks, TeamTaskStatus.DOING));
        board.setDone(collect(tasks, TeamTaskStatus.DONE));
        return board;
    }

    private static List<TeamTask> collect(List<TeamTask> tasks, TeamTaskStatus status) {
        return tasks.stream()
                .filter(task -> status.getCode().equals(task.getStatus()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 看板覆盖的三态（查询条件与分组口径的唯一来源）
     */
    public static List<String> boardStatusCodes() {
        return Arrays.asList(
                TeamTaskStatus.TODO.getCode(), TeamTaskStatus.DOING.getCode(), TeamTaskStatus.DONE.getCode());
    }
}
