package org.jeecg.modules.team.entity;

import java.io.Serializable;
import java.util.Date;

import org.jeecg.common.system.base.entity.JeecgEntity;
import org.springframework.format.annotation.DateTimeFormat;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: 团队协作-审批单
 * @Author: Kimi Code
 * @Date: 2026-11-17
 * @Version: V1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "团队协作-审批单")
@TableName("team_approval")
public class TeamApproval extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 申请单号 OA-YYYYMMDD-NNN */
    @Schema(description = "申请单号")
    private String code;

    /** 申请类别: 请假申请/出差申请/费用报销等 */
    @Schema(description = "申请类别", example = "加班申请")
    private String category;

    /** 事项标题 */
    @Schema(description = "事项标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    /** 申请事由 */
    @Schema(description = "申请事由")
    private String reason;

    /** 状态: pending/approved/rejected */
    @Schema(description = "状态: pending/approved/rejected", example = "pending")
    private String status;

    /** 申请人(sys_user.id) */
    @Schema(description = "申请人(sys_user.id)")
    private String applicantId;

    /** 申请人姓名冗余 */
    @Schema(description = "申请人姓名冗余")
    private String applicantName;

    /** 开始日期 */
    @Schema(description = "开始日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date startDate;

    /** 结束日期 */
    @Schema(description = "结束日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date endDate;

    /** 当前待审步骤序号 */
    @Schema(description = "当前待审步骤序号", example = "1")
    private Integer currentStep;
}
