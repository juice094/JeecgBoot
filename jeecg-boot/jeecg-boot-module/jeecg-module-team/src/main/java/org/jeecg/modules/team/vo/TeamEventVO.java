package org.jeecg.modules.team.vo;

import java.io.Serializable;
import java.util.Date;

import org.jeecg.modules.team.entity.TeamAuditLog;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 团队协作-业务事件流推送体（SSE /team/event/stream 的事件载荷）
 * @Author: juice094
 * @Date: 2026-11-20
 * @Version: V1.0
 *
 * 从 TeamAuditLog 投影而来：事件不是日志，是递出来的事实——
 * 字段集即客户端渲染时间线所需的最小闭包，不带 detail/operatorId 等追溯冗余。
 */
@Data
@Schema(description = "团队协作-业务事件")
public class TeamEventVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "审计行ID（增量游标）")
    private String id;

    @Schema(description = "业务类型: task/approval/bug")
    private String bizType;

    @Schema(description = "业务单据ID")
    private String bizId;

    @Schema(description = "业务单号/标题冗余")
    private String bizCode;

    @Schema(description = "动作: submit/change_status/approve/reject")
    private String action;

    @Schema(description = "变更前值")
    private String fromValue;

    @Schema(description = "变更后值")
    private String toValue;

    @Schema(description = "发生时间")
    private Date createTime;

    @Schema(description = "操作人账号")
    private String createBy;

    public static TeamEventVO from(TeamAuditLog log) {
        TeamEventVO vo = new TeamEventVO();
        vo.setId(log.getId());
        vo.setBizType(log.getBizType());
        vo.setBizId(log.getBizId());
        vo.setBizCode(log.getBizCode());
        vo.setAction(log.getAction());
        vo.setFromValue(log.getFromValue());
        vo.setToValue(log.getToValue());
        vo.setCreateTime(log.getCreateTime());
        vo.setCreateBy(log.getCreateBy());
        return vo;
    }
}
