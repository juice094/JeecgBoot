package org.jeecg.modules.team.entity;

import java.io.Serializable;

import org.jeecg.common.system.base.entity.JeecgEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: 团队协作-标签（V3.9.6_16，/issues P1 标签体系）
 * @Author: Kimi Code (P1 issues line)
 * @Date: 2026-11-23
 * @Version: V1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "团队协作-标签")
@TableName("team_label")
public class TeamLabel extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 标签名（库内唯一）
     */
    @Schema(description = "标签名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    /**
     * 展示色（十六进制，如 #1f883d）
     */
    @Schema(description = "展示色（十六进制，如 #1f883d）")
    private String color;
}
