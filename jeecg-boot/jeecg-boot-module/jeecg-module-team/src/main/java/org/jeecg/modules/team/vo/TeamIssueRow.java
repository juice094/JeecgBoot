package org.jeecg.modules.team.vo;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import org.jeecg.modules.team.entity.TeamLabel;

/**
 * @Description: /issues 跨 kind 聚合行（task/bug/approval 统一视图，V3.9.6_16）
 * @Author: Kimi Code (P1 issues line)
 * @Date: 2026-11-23
 * @Version: V1.0
 *
 * 状态映射表（统一 open/closed 二态）：
 *   task     todo/doing → open；done → closed
 *   bug      open/processing → open；resolved/closed → closed
 *   approval draft/pending/returned → open；approved/rejected/canceled → closed
 *
 * 展示编号 code：任务 T-<taskNo>；缺陷 B-<bugNo>；审批 OA 单号原样
 */
@Data
@Accessors(chain = true)
@Schema(description = "/issues 聚合行")
public class TeamIssueRow implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "工作项类型: task/bug/approval")
    private String kind;

    @Schema(description = "展示编号（T-12/B-3/OA-20261123-001）")
    private String code;

    @Schema(description = "业务单据主键（分流详情用）")
    private String bizId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "统一状态: open/closed")
    private String state;

    @Schema(description = "处理人姓名（task=指派人；bug=处理人，空则提交人；approval=申请人）")
    private String assigneeName;

    @Schema(description = "标签列表（仅 task 有标签体系，其余恒为空数组）")
    private List<TeamLabel> labels;

    @Schema(description = "是否被未完成依赖阻塞（仅 task 有效）")
    private Boolean blocked;

    @Schema(description = "未完成依赖任务 id 列表（仅 task 有效；bug/approval 恒为空数组）")
    private List<String> blockedBy;

    @Schema(description = "创建时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @Schema(description = "到期日（task=截止日期；approval=结束日期；bug 无）")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd")
    private Date dueDate;
}
