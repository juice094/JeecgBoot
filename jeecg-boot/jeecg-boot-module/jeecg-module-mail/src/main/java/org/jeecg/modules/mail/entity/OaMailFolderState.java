package org.jeecg.modules.mail.entity;

import java.io.Serializable;

import org.jeecg.common.system.base.entity.JeecgEntity;

import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: OA 邮件-文件夹同步状态（骨架）
 * @Author: juice094（原型线）
 * @Date: 2026-11-17
 * @Version: V0.2
 *
 * canonical 为空表示未识别，需人工映射，同步不得静默跳过（需求文档 B-2）。
 * 同步 error 时不回退 last_uid，下次续跑。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "OA 邮件-文件夹同步状态")
@TableName("oa_mail_folder_state")
public class OaMailFolderState extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "员工邮箱账号 id")
    private String accountId;

    @Schema(description = "远端文件夹名")
    private String remoteName;

    @Schema(description = "规范文件夹（空=未识别）")
    private String canonical;

    @Schema(description = "同步游标")
    private Long lastUid;
}
