package org.jeecg.modules.team.vo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.jeecg.modules.team.entity.TeamAuditLog;
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.entity.TeamTaskComment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 团队协作-任务详情聚合 VO（/team/task/detail）
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 * <p>
 * 详情页一次拉取所需数据：任务主体 + 评论列表 + 时间线事件。
 * 时间线复用 team_audit_log（只追加不改写），不额外建事件表。
 */
@Data
@Schema(description = "团队协作-任务详情聚合")
public class TeamTaskDetail implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "任务主体")
    private TeamTask task;

    @Schema(description = "未完成依赖任务 id 列表（无依赖或未完成任务恒为空数组）")
    private List<String> blockedBy = new ArrayList<>();

    @Schema(description = "评论列表（创建时间升序）")
    private List<TeamTaskComment> comments = new ArrayList<>();

    @Schema(description = "时间线事件（创建时间升序）")
    private List<TeamAuditLog> timeline = new ArrayList<>();
}
