package org.jeecg.modules.team.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.team.service.ITeamIssueService;
import org.jeecg.modules.team.vo.TeamIssueRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @Description: /issues 跨 kind 聚合（task/bug/approval 统一列表，GitHub /issues 语义）
 * @Author: Kimi Code (P1 issues line)
 * @Date: 2026-11-23
 * @Version: V1.0
 *
 * 权限：整组挂 team:task:list（/issues 与任务列表同一阅读面）。
 * 状态映射（open/closed 二态）与展示编号规则见 TeamIssueRow 注释。
 */
@Tag(name = "团队协作-/issues 聚合",
        description = "跨 kind 统一工作项列表；统一返回 Result 包装；业务错误 success=false。")
@RestController
@RequestMapping("/team/issue")
public class TeamIssueController {

    @Autowired
    private ITeamIssueService teamIssueService;

    /**
     * 跨 kind 聚合分页列表
     */
    @AutoLog(value = "团队协作-/issues 聚合列表")
    @Operation(summary = "跨 kind 聚合分页列表",
            description = "kind=all/task/bug/approval；keyword 模糊匹配标题与展示编号；"
                    + "assigneeId 按各域处理人字段过滤（task→assigneeId，bug→reporterId，approval→applicantId）；"
                    + "state=open/closed/all（映射表见 TeamIssueRow）；统一按 create_time desc 排序。")
    @RequiresPermissions("team:task:list")
    @GetMapping(value = "/list")
    public Result<IPage<TeamIssueRow>> list(
            @Parameter(name = "kind", description = "工作项类型", example = "all")
            @RequestParam(name = "kind", defaultValue = "all") String kind,
            @Parameter(name = "keyword", description = "标题/编号模糊匹配")
            @RequestParam(name = "keyword", required = false) String keyword,
            @Parameter(name = "assigneeId", description = "处理人(sys_user.id)")
            @RequestParam(name = "assigneeId", required = false) String assigneeId,
            @Parameter(name = "state", description = "统一状态", example = "open")
            @RequestParam(name = "state", defaultValue = "all") String state,
            @Parameter(name = "pageNo", description = "页码，从 1 开始", example = "1")
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @Parameter(name = "pageSize", description = "每页条数", example = "10")
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.OK(teamIssueService.issuePage(kind, keyword, assigneeId, state, pageNo, pageSize));
    }
}
