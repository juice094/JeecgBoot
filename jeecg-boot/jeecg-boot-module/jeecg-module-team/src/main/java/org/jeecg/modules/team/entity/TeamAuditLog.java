package org.jeecg.modules.team.entity;

import java.io.Serializable;

import org.jeecg.common.system.base.entity.JeecgEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * @Description: 团队协作-审计日志（关键动作留痕，表 team_audit_log，V3.9.6_4）
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 * <p>
 * 只追加不改写：业务状态机（task/approval/bug 的 changeStatus/approve/reject/submit）
 * 在事务内统一埋点；审计行本身不允许更新删除。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "团队协作-审计日志")
@TableName("team_audit_log")
public class TeamAuditLog extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 业务类型: task/approval/bug
     */
    @Schema(description = "业务类型: task/approval/bug", example = "bug")
    private String bizType;

    /**
     * 业务单据ID
     */
    @Schema(description = "业务单据ID")
    private String bizId;

    /**
     * 业务单号/标题冗余（便于人读检索）
     */
    @Schema(description = "业务单号/标题冗余")
    private String bizCode;

    /**
     * 动作: submit/change_status/approve/reject
     */
    @Schema(description = "动作: submit/change_status/approve/reject", example = "change_status")
    private String action;

    /**
     * 变更前值（状态码；审批为步骤快照）
     */
    @Schema(description = "变更前值")
    private String fromValue;

    /**
     * 变更后值（状态码；审批为步骤快照）
     */
    @Schema(description = "变更后值")
    private String toValue;

    /**
     * 操作人(sys_user.id)
     */
    @Schema(description = "操作人(sys_user.id)")
    private String operatorId;

    /**
     * 操作人姓名冗余
     */
    @Schema(description = "操作人姓名冗余")
    private String operatorName;

    /**
     * 补充说明（异常上下文/审批意见摘要）
     */
    @Schema(description = "补充说明")
    private String detail;
}
