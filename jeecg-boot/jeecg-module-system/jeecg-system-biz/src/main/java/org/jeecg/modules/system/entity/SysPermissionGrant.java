package org.jeecg.modules.system.entity;

import java.io.Serializable;
import java.util.Date;

import org.jeecg.common.system.base.entity.JeecgEntity;
import org.springframework.format.annotation.DateTimeFormat;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: 权限确权-授权关系（确权主体）
 * @Author: juice094
 * @Date: 2026-11-19
 * @Version: V1.0
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "权限确权-授权关系")
@TableName("sys_permission_grant")
public class SysPermissionGrant extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "授权单号")
    private String grantNo;

    @Schema(description = "主体类型: USER/ROLE/DEPT")
    private String subjectType;

    @Schema(description = "主体ID")
    private String subjectId;

    @Schema(description = "权限ID")
    private String permissionId;

    @Schema(description = "数据规则ID串")
    private String dataRuleIds;

    @Schema(description = "数据范围: SELF/DEPT/DEPT_AND_CHILD/GLOBAL")
    private String scopeType;

    @Schema(description = "状态: draft/pending/returned/approved/rejected/canceled/active/expired/revoked")
    private String status;

    @Schema(description = "生效起点")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date validFrom;

    @Schema(description = "生效终点(NULL=长期)")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date validUntil;

    @Schema(description = "申请理由")
    private String applyReason;

    @Schema(description = "申请人")
    private String applicant;

    @Schema(description = "末次审批人")
    private String approver;

    @Schema(description = "当前审批步骤")
    private Integer currentStep;

    @Schema(description = "来源: APPLY/TEMPLATE/MIGRATION")
    private String source;

    @Schema(description = "备注")
    private String extNote;

    @Schema(description = "到期提醒是否已发送: 0未发送 1已发送")
    private Integer expiringNotified;

    @TableLogic(value = "0", delval = "1")
    @Schema(description = "删除标志: 0正常 1已删除")
    private Integer deleted;
}
