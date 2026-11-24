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
 * @Description: 团队协作-任务
 * @Author: juice094 (issue #2/#10)
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

    /**
     * 租户内序号，用于 #12 式引用（V3.9.6_7）
     */
    @Schema(description = "租户内序号，用于 #12 式引用", example = "12")
    private Integer taskNo;

    /**
     * 任务标题
     */
    @Schema(description = "任务标题", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    /**
     * 任务描述
     */
    @Schema(description = "任务描述")
    private String description;

    /**
     * 状态: todo/doing/done（与前端 src/constants/task.ts 对齐）
     */
    @Schema(description = "状态: todo/doing/done", example = "todo")
    private String status;

    /**
     * 标签: 需求/开发/缺陷
     */
    @Schema(description = "标签: 需求/开发/缺陷")
    private String tag;

    /**
     * 优先级: 高/中/低
     */
    @Schema(description = "优先级: 高/中/低", example = "中")
    private String priority;

    /**
     * 被指派人(sys_user.id)
     */
    @Schema(description = "被指派人(sys_user.id)")
    private String assigneeId;

    /**
     * 被指派人姓名冗余
     */
    @Schema(description = "被指派人姓名冗余")
    private String assigneeName;

    /**
     * 截止日期
     */
    @Schema(description = "截止日期")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date dueDate;

    /**
     * 看板列内排序
     */
    @Schema(description = "看板列内排序", example = "0")
    private Integer sortNo;

    /**
     * 逻辑删除 0=正常 1=已删（D-69a，V3.9.6_5）
     */
    @Schema(description = "逻辑删除 0=正常 1=已删", hidden = true)
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;

    /**
     * 阻塞标记 1=被未完成依赖阻塞 0=正常（V3.9.6_16 派生位，重算入口 TeamTaskDependencyService；
     * TeamTaskStatus 六态/三态机零改动）
     */
    @Schema(description = "阻塞标记 1=被未完成依赖阻塞 0=正常")
    private Integer blocked;

    /**
     * 任务标签列表（team_task_label 关联查询，仅列表/详情返回用，非库列；V3.9.6_16）
     */
    @Schema(description = "任务标签列表")
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private java.util.List<TeamLabel> labels;
}
