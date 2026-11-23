package org.jeecg.modules.team.entity;

import java.io.Serializable;

import org.jeecg.common.system.base.entity.JeecgEntity;

import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: 团队协作-表单模板（issue #3，表 team_form_template，V3.9.6_5）
 * @Author: Kimi Code
 * @Date: 2026-11-17
 * @Version: V1.0
 *
 * stepTemplate 为 JSON 文本（[{approverName, approverRole}]），
 * 由 TemplateStepParser 统一解析与校验；formFields 试点预留（动态渲染后置）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "团队协作-表单模板")
@TableName("team_form_template")
public class TeamFormTemplate extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 模板编码 */
    @Schema(description = "模板编码", example = "leave")
    private String code;

    /** 模板名称（页面展示） */
    @Schema(description = "模板名称", example = "请假申请")
    private String name;

    /** 左侧导航分组 */
    @Schema(description = "左侧导航分组", example = "考勤与假期")
    private String groupName;

    /** 申请类别（落 team_approval.category） */
    @Schema(description = "申请类别", example = "请假申请")
    private String category;

    /** 表单页提示语 */
    @Schema(description = "表单页提示语")
    private String formTip;

    /** 表单字段配置（试点预留，动态渲染后置） */
    @Schema(description = "表单字段配置（试点预留）")
    private String formFields;

    /** 默认审批步骤模板 JSON：[{approverName, approverRole}] */
    @Schema(description = "默认审批步骤模板 JSON", example = "[{\"approverName\":\"贺致远\",\"approverRole\":\"部门主管\"}]")
    private String stepTemplate;

    /** 是否启用 1=启用 */
    @Schema(description = "是否启用 1=启用", example = "1")
    private Integer enabled;

    /** 排序 */
    @Schema(description = "排序", example = "1")
    private Integer sortNo;
}
