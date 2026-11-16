package org.jeecg.modules.team.entity;

import java.io.Serializable;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: 团队协作-审批步骤
 * @Author: Kimi Code
 * @Date: 2026-11-17
 * @Version: V1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "团队协作-审批步骤")
@TableName("team_approval_step")
public class TeamApprovalStep implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "ID")
    private String id;

    /** 审批单ID */
    @Schema(description = "审批单ID")
    private String approvalId;

    /** 步骤序号, 从1开始 */
    @Schema(description = "步骤序号, 从1开始")
    private Integer stepNo;

    /** 审批人姓名 */
    @Schema(description = "审批人姓名")
    private String approverName;

    /** 审批人角色说明 */
    @Schema(description = "审批人角色说明")
    private String approverRole;

    /** wait/approved/rejected */
    @Schema(description = "wait/approved/rejected", example = "wait")
    private String action;

    /** 审批意见 */
    @Schema(description = "审批意见")
    private String comment;

    /** 操作时间 */
    @Schema(description = "操作时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date actedAt;

    /** 创建日期 */
    @Schema(description = "创建日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
