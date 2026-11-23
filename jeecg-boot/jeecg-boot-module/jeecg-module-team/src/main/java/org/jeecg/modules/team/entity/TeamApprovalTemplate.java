package org.jeecg.modules.team.entity;

import java.io.Serializable;

import org.jeecg.common.system.base.entity.JeecgEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: 团队协作-审批模板
 * @Author: juice094
 * @Date: 2026-11-18
 * @Version: V1.0
 * <p>
 * 每个 category 至多一条启用模板（唯一索引 uk_template_category）。
 * submit 时按 category 查模板，按模板步骤生成 team_approval_step。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "团队协作-审批模板")
@TableName("team_approval_template")
public class TeamApprovalTemplate extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "申请类别，与 team_approval.category 对齐")
    private String category;

    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "模板说明")
    private String description;

    /**
     * 字段契约 JSON 数组：[{key,label,type,required,options,min,max}]
     * 前端按此渲染表单，后端按此复核（不只信前端）。
     */
    @Schema(description = "字段契约 JSON")
    private String fieldSchema;

    @Schema(description = "是否启用 1启用 0停用")
    private Integer enabled;
}
