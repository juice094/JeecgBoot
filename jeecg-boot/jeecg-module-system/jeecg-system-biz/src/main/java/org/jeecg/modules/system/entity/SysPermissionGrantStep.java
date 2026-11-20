package org.jeecg.modules.system.entity;

import java.io.Serializable;
import java.util.Date;

import org.jeecg.common.system.base.entity.JeecgEntity;
import org.springframework.format.annotation.DateTimeFormat;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: 权限确权-审批步骤
 * @Author: juice094
 * @Date: 2026-11-19
 * @Version: V1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "权限确权-审批步骤")
@TableName("sys_permission_grant_step")
public class SysPermissionGrantStep extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "授权单ID")
    private String grantId;

    @Schema(description = "步骤序号")
    private Integer stepNo;

    @Schema(description = "审批人")
    private String approver;

    @Schema(description = "动作: wait/approve/reject/return")
    private String action;

    @Schema(description = "意见")
    private String comment;

    @Schema(description = "处理时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date actedAt;
}
