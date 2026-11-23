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
 * @Description: 团队协作-Bug反馈
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "团队协作-Bug反馈")
@TableName("team_bug")
public class TeamBug extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 问题描述
     */
    @Schema(description = "问题描述", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    /**
     * 缺陷序号（#B-n 展示编号，服务端赋值，V3.9.6_16；口径同 task_no）
     */
    @Schema(description = "缺陷序号（#B-n 展示编号）", example = "3")
    private Integer bugNo;

    /**
     * 截图附件路径
     */
    @Schema(description = "截图附件路径")
    private String screenshotPath;

    /**
     * 状态: open/processing/resolved/closed
     */
    @Schema(description = "状态: open/processing/resolved/closed", example = "open")
    private String status;

    /**
     * 提交人(sys_user.id)
     */
    @Schema(description = "提交人(sys_user.id)")
    private String reporterId;

    /**
     * 提交人姓名冗余
     */
    @Schema(description = "提交人姓名冗余")
    private String reporterName;

    /**
     * 处理人姓名
     */
    @Schema(description = "处理人姓名")
    private String handlerName;

    /**
     * 逻辑删除 0=正常 1=已删（D-69a，V3.9.6_5）
     */
    @Schema(description = "逻辑删除 0=正常 1=已删", hidden = true)
    @TableLogic(value = "0", delval = "1")
    private Integer deleted;
}
