package org.jeecg.modules.team.entity;

import java.io.Serializable;
import java.util.Date;

import org.jeecg.common.system.base.entity.JeecgEntity;
import org.springframework.format.annotation.DateTimeFormat;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: 团队协作-审批单
 * @Author: juice094
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

    /**
     * 申请单号 OA-YYYYMMDD-NNN
     */
    @Schema(description = "申请单号")
    private String code;

    /**
     * 申请类别: 请假申请/出差申请/费用报销等
     */
    @Schema(description = "申请类别", example = "加班申请")
    private String category;

    /**
     * 事项标题
     */
    @Schema(description = "事项标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    /**
     * 申请事由
     */
    @Schema(description = "申请事由")
    private String reason;

    /**
     * 类别专属字段值（JSON 原文；按模板 field_schema 校验后落库）
     */
    @Schema(description = "类别专属字段值（JSON）")
    private String formData;

    /**
     * 金额（从 form_data.amount 提取；供范围查询、聚合、金额分级审批）
     */
    @Schema(description = "金额", example = "3800.00")
    private java.math.BigDecimal amount;

    /**
     * 状态: pending/approved/rejected
     */
    @Schema(description = "状态: pending/approved/rejected", example = "pending")
    private String status;

    /**
     * Flowable 流程实例 ID（W3 双写锚定，引擎侧 businessKey=code；非二级链无镜像时为空）
     */
    @Schema(description = "流程实例ID（W3 双写锚定）")
    private String processInstanceId;

    /**
     * 申请人(sys_user.id)
     */
    @Schema(description = "申请人(sys_user.id)")
    private String applicantId;

    /**
     * 申请人姓名冗余
     */
    @Schema(description = "申请人姓名冗余")
    private String applicantName;

    /**
     * 开始日期
     */
    @Schema(description = "开始日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date startDate;

    /**
     * 结束日期
     */
    @Schema(description = "结束日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date endDate;

    /**
     * 当前待审步骤序号
     */
    @Schema(description = "当前待审步骤序号", example = "1")
    private Integer currentStep;

    /**
     * 逻辑删除 0=正常 1=已删（D-69a，V3.9.6_5）
     */
    @Schema(description = "逻辑删除 0=正常 1=已删", hidden = true)
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
