package org.jeecg.modules.team.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.jeecg.modules.team.vo.TeamIssueRow;

/**
 * @Description: /issues 跨 kind 聚合查询 Service
 * @Author: Kimi Code (P1 issues line)
 * @Date: 2026-11-23
 * @Version: V1.0
 */
public interface ITeamIssueService {

    /**
     * 跨 kind 聚合分页查询（task/bug/approval 三源内存合并）
     *
     * @param kind       all/task/bug/approval（null 视为 all）
     * @param keyword    标题/展示编号模糊匹配（null 不限）
     * @param assigneeId 处理人过滤（task→assignee_id；bug→reporter_id；approval→applicant_id）
     * @param state      open/closed/all（null 视为 all）
     * @param pageNo     页码，从 1 开始
     * @param pageSize   每页条数
     * @return 统一聚合行分页（create_time desc）
     */
    IPage<TeamIssueRow> issuePage(String kind, String keyword, String assigneeId,
            String state, int pageNo, int pageSize);
}
