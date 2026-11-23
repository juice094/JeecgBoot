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
 * @Description: 团队协作-审批模板步骤
 * @Author: juice094
 * @Date: 2026-11-18
 * @Version: V1.0
 * <p>
 * 仅 SEQ（依次）在本期生效；ALL/ANY 字段预留，不影响主链路。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "团队协作-审批模板步骤")
@TableName("team_approval_template_step")
public class TeamApprovalTemplateStep implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "ID")
    private String id;

    @Schema(description = "模板ID")
    private String templateId;

    @Schema(description = "步骤序号，从1开始")
    private Integer stepNo;

    @Schema(description = "审批人 sys_user.id")
    private String approverId;

    @Schema(description = "审批人姓名")
    private String approverName;

    @Schema(description = "审批人角色说明")
    private String approverRole;

    @Schema(description = "签核类型 SEQ/ALL/ANY（本期仅 SEQ 生效）")
    private String signType;

    @Schema(description = "创建日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
