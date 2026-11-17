package org.jeecg.modules.mail.dto;

import java.util.Date;
import java.util.List;

import org.jeecg.modules.mail.entity.OaMailFolderState;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 同步状态响应（接口文档 §4.2 GET /accounts/{id}/sync/status）
 * @Author: mail-dev
 * @Date: 2026-11-17
 */
@Data
@Schema(description = "同步状态")
public class MailSyncStatusDTO {

    @Schema(description = "员工邮箱账号 id")
    private String accountId;

    @Schema(description = "是否正在同步")
    private boolean running;

    @Schema(description = "最近同步时间")
    private Date lastSyncTime;

    @Schema(description = "文件夹游标状态")
    private List<OaMailFolderState> folders;
}
