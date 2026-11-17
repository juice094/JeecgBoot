package org.jeecg.modules.team.service;

import org.jeecg.modules.team.vo.TeamDashboardSummary;

/**
 * @Description: 团队协作-仪表盘聚合 Service
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 */
public interface ITeamDashboardService {

    /**
     * 汇总 team 侧三计数：待审审批数 / 我的未完成任务数 / 未关闭 Bug 数
     *
     * @param loginUserId 当前登录用户 id（我的任务计数用），不允许为 null
     * @return 聚合结果
     */
    TeamDashboardSummary summarize(String loginUserId);
}
