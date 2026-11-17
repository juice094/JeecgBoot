package org.jeecg.modules.team.vo;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description: 团队协作-仪表盘聚合 VO（/team/dashboard/summary）
 * @Author: Kimi Code
 * @Date: 2026-11-17
 * @Version: V1.1
 * <p>
 * sys 侧聚合（最新公告 + 未读数）为 D-72 宿裁授权例外：team 域直读 sys_announcement /
 * sys_announcement_send，不经过管理组出口（ISysBaseAPI 无现成计数方法）。
 */
@Data
@Schema(description = "团队协作-仪表盘聚合")
public class TeamDashboardSummary implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 待审审批单数（team_approval status=pending）
     */
    @Schema(description = "待审审批单数", example = "3")
    private Long pendingApprovalCount;

    /**
     * 我的未完成任务数（team_task 指派给我且非 done）
     */
    @Schema(description = "我的未完成任务数", example = "5")
    private Long myUnfinishedTaskCount;

    /**
     * 未关闭 Bug 数（team_bug status in open/processing/resolved）
     */
    @Schema(description = "未关闭 Bug 数", example = "2")
    private Long openBugCount;

    /**
     * 未读公告/通知数（sys_announcement_send read_flag=0，口径见接口文档）
     */
    @Schema(description = "未读公告数", example = "4")
    private Long unreadNoticeCount;

    /**
     * 最新已发布公告（默认 5 条，按 send_time 倒序）
     */
    @Schema(description = "最新已发布公告")
    private List<NoticeItem> latestNotices;

    /**
     * 公告摘要项（字段来源 sys_announcement；titile 为上游既有拼写，不改）
     */
    @Data
    @Schema(description = "公告摘要项")
    public static class NoticeItem implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "公告ID")
        private String id;

        /**
         * 上游列名即 titile（sys_announcement.titile），保持原样
         */
        @Schema(description = "标题（上游字段 titile）")
        private String titile;

        @Schema(description = "发布人")
        private String sender;

        @Schema(description = "发布时间")
        private String sendTime;
    }
}
