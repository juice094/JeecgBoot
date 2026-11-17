package org.jeecg.modules.team.controller;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.team.service.ITeamDashboardService;
import org.jeecg.modules.team.vo.TeamDashboardSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @Description: 团队协作-仪表盘聚合
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.1
 * <p>
 * sys 侧聚合已落地（D-72 宿裁授权例外，报告留痕）：team 域直读 sys_announcement /
 * sys_announcement_send（未读口径 = send 表 read_flag=0 行数；最新公告 = 已发布按 send_time 倒序 5 条）。
 * ISysBaseAPI 无现成计数方法，此为唯一越出口点。
 */
@Tag(name = "团队协作-仪表盘",
        description = "全局约定：统一返回 Result 包装（success/message/code/result/timestamp）；"
                + "401=未登录或登录态过期，403=无权限（角色未勾选 team:dashboard:summary 按钮权限）。")
@RestController
@RequestMapping("/team/dashboard")
public class TeamDashboardController {

    @Autowired
    private ITeamDashboardService dashboardService;

    @AutoLog(value = "团队协作-仪表盘-聚合查询")
    @Operation(summary = "团队协作-仪表盘-聚合查询（待审审批数/我的未完成任务数/未关闭Bug数）")
    @RequiresPermissions("team:dashboard:summary")
    @GetMapping(value = "/summary")
    public Result<TeamDashboardSummary> summary() {
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser == null) {
            return Result.error("未登录或登录态已过期");
        }
        return Result.OK(dashboardService.summarize(loginUser.getId()));
    }
}
