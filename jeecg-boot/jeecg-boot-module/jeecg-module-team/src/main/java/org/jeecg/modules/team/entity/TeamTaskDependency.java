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
 * @Description: 团队协作-任务依赖（V3.9.6_16）
 * @Author: Kimi Code (P1 issues line)
 * @Date: 2026-11-23
 * @Version: V1.0
 *
 * 语义：task_id 依赖 depends_on_task_id——被依赖任务完成后依赖方才解除 blocked，
 * 且 changeStatus 进入 doing 前要求依赖全部完成。
 * ponytail: 不做成环检测（pilot 量级，人为治理；成环时 blocked 重算是单向有界的，不死循环）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "团队协作-任务依赖")
@TableName("team_task_dependency")
public class TeamTaskDependency implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "ID")
    private String id;

    /**
     * 依赖方任务 team_task.id
     */
    @Schema(description = "依赖方任务ID")
    private String taskId;

    /**
     * 被依赖任务 team_task.id
     */
    @Schema(description = "被依赖任务ID")
    private String dependsOnTaskId;

    /**
     * 创建日期
     */
    @Schema(description = "创建日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;
}
