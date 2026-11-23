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
import org.springframework.format.annotation.DateTimeFormat;

/**
 * @Description: 团队协作-审批双写对账（team 状态 vs Flowable 引擎实例状态，W3）
 * @Author: Kimi Code
 * @Date: 2026-11-23
 * @Version: V1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "团队协作-审批双写对账")
@TableName("team_approval_flow_recon")
public class TeamApprovalFlowRecon implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "ID")
    private String id;

    /** team_approval.id */
    @Schema(description = "审批单ID")
    private String approvalId;

    /** 申请单号（OA-YYYYMMDD-NNN） */
    @Schema(description = "申请单号")
    private String approvalCode;

    /** 流程实例 ID（引擎未挂接时为空） */
    @Schema(description = "流程实例ID")
    private String processInstanceId;

    /** team 侧状态（TeamApprovalStatus 落库码） */
    @Schema(description = "team 侧状态")
    private String teamStatus;

    /** team 侧当前步骤序号 */
    @Schema(description = "team 侧当前步骤序号")
    private Integer teamCurrentStep;

    /** 引擎侧观测快照：RUNNING:<节点> / FINISHED:<endEvent> / DELETED / NOT_FOUND / SYNC_FAILED */
    @Schema(description = "引擎侧观测快照")
    private String engineStatus;

    /** 不一致标记 1=不一致 0=一致 */
    @Schema(description = "不一致标记 1=不一致 0=一致")
    private Integer diffFlag;

    /** 差异/失败说明 */
    @Schema(description = "差异/失败说明")
    private String detail;

    @Schema(description = "创建日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @Schema(description = "更新日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
