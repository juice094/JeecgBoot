package org.jeecg.modules.team.entity;

import java.io.Serializable;

import org.jeecg.common.system.base.entity.JeecgEntity;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: 团队协作-任务评论（V3.9.6_7）
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 * <p>
 * 评论与事件流分离：评论有正文与编辑语义，事件只有元数据。
 * 正文存 Markdown 原文，前端负责渲染。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "团队协作-任务评论")
@TableName("team_task_comment")
public class TeamTaskComment extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 任务ID
     */
    @Schema(description = "任务ID")
    private String taskId;

    /**
     * 作者(sys_user.id)
     */
    @Schema(description = "作者(sys_user.id)")
    private String authorId;

    /**
     * 作者姓名冗余
     */
    @Schema(description = "作者姓名冗余")
    private String authorName;

    /**
     * 评论正文（Markdown 原文）
     */
    @Schema(description = "评论正文（Markdown 原文）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String body;

    /**
     * 逻辑删除 0=正常 1=已删
     */
    @Schema(description = "逻辑删除", hidden = true)
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
