package org.jeecg.modules.team.controller;

import java.util.List;

import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.modules.team.entity.TeamTaskComment;
import org.jeecg.modules.team.service.ITeamTaskCommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @Description: 团队协作-任务评论
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 * <p>
 * 权限：读走 team:task:queryById，写走 team:task:edit（评论视为编辑任务上下文）。
 */
@Tag(name = "团队协作-任务评论", description = "任务详情页评论区；正文为 Markdown 原文，前端渲染。")
@RestController
@RequestMapping("/team/task/comment")
public class TeamTaskCommentController {

    @Autowired
    private ITeamTaskCommentService commentService;

    @AutoLog(value = "团队协作-任务评论-列表")
    @Operation(summary = "按任务查评论", description = "按创建时间升序返回，供详情页时间线正序渲染。")
    @RequiresPermissions("team:task:queryById")
    @GetMapping(value = "/list")
    public Result<List<TeamTaskComment>> list(
            @Parameter(name = "taskId", description = "任务ID", required = true)
            @RequestParam(name = "taskId", required = true) String taskId) {
        return Result.OK(commentService.listByTask(taskId));
    }

    @AutoLog(value = "团队协作-任务评论-新增")
    @Operation(summary = "新增评论",
            description = "错误场景：任务不存在「任务不存在」；正文为空「评论内容不能为空」；超长「评论内容过长」。")
    @RequiresPermissions("team:task:edit")
    @PostMapping(value = "/add")
    public Result<TeamTaskComment> add(
            @Parameter(name = "taskId", description = "任务ID", required = true)
            @RequestParam(name = "taskId", required = true) String taskId,
            @RequestBody TeamTaskComment comment) {
        return Result.OK(commentService.addComment(taskId, comment != null ? comment.getBody() : null));
    }
}
