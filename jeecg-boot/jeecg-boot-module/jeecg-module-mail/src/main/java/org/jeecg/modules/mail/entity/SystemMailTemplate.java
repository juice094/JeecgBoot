package org.jeecg.modules.mail.entity;

import java.io.Serializable;

import org.jeecg.common.system.base.entity.JeecgEntity;

import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: OA 邮件-系统发信模板（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.1
 *
 * params 为保存时扫描 {key} 生成的 JSON 数组字符串；骨架不实现扫描器。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "OA 邮件-系统发信模板")
@TableName("system_mail_template")
public class SystemMailTemplate extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "模板编码（全局唯一）")
    private String code;

    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "发送账号 id")
    private String accountId;

    @Schema(description = "标题模板")
    private String title;

    @Schema(description = "正文模板（HTML）")
    private String content;

    @Schema(description = "占位符 JSON 数组字符串")
    private String params;

    @Schema(description = "状态：1 启用 / 0 停用")
    private Integer status;
}
