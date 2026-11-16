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
 * @Description: 团队协作-任务
 * @Author: Kimi Code (issue #2/#10)
 * @Date: 2026-11-17
 * @Version: V1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "团队协作-任务")
@TableName("team_task")
public class TeamTask extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 任务标题 */
    @Schema(description = "任务标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    /** 任务描述 */
    @Schema(description = "任务描述")
    private String description;

    /** 状态: todo/doing/done（与前端 src/constants/task.ts 对齐） */
    @Schema(description = "状态: todo/doing/done", example = "todo")
    private String status;

    /** 标签: 需求/开发/缺陷 */
    @Schema(description = "标签: 需求/开发/缺陷")
    private String tag;

    /** 优先级: 高/中/低 */
    @Schema(description = "优先级: 高/中/低", example = "中")
    private String priority;

    /** 被指派人(sys_user.id) */
    @Schema(description = "被指派人(sys_user.id)")
    private String assigneeId;

    /** 被指派人姓名冗余 */
    @Schema(description = "被指派人姓名冗余")
    private String assigneeName;

    /** 截止日期 */
    @Schema(description = "截止日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date dueDate;

    /** 看板列内排序 */
    @Schema(description = "看板列内排序", example = "0")
    private Integer sortNo;
}
