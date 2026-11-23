package org.jeecg.modules.mail.entity;

import java.io.Serializable;

import org.jeecg.common.system.base.entity.JeecgEntity;

import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: OA 邮件-邮件与业务关联（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.2
 *
 * 邮件转待办/发起流程时写入，供双向反查（需求文档 B-6）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "OA 邮件-邮件与业务关联")
@TableName("oa_mail_biz_link")
public class OaMailBizLink extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "邮件索引 id")
    private String messageIndexId;

    @Schema(description = "业务类型（team_task / team_approval 等）")
    private String bizType;

    @Schema(description = "业务单据 id")
    private String bizId;
}
