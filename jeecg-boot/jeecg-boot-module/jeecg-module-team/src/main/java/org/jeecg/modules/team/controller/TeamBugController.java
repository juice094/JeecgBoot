package org.jeecg.modules.team.controller;

import java.util.Arrays;
import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.team.entity.TeamBug;
import org.jeecg.modules.team.service.ITeamBugService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @Description: 团队协作-Bug反馈
 * @Author: Kimi Code
 * @Date: 2026-11-17
 * @Version: V1.0
 */
@Tag(name = "团队协作-Bug反馈")
@RestController
@RequestMapping("/team/bug")
public class TeamBugController extends JeecgController<TeamBug, ITeamBugService> {
    @Autowired
    private ITeamBugService bugService;

    @AutoLog(value = "团队协作-Bug-分页列表")
    @Operation(summary = "团队协作-Bug-分页列表")
    @GetMapping(value = "/list")
    public Result<IPage<TeamBug>> queryPageList(TeamBug bug,
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest req) {
        QueryWrapper<TeamBug> queryWrapper = QueryGenerator.initQueryWrapper(bug, req.getParameterMap());
        queryWrapper.orderByDesc("create_time");
        Page<TeamBug> page = new Page<>(pageNo, pageSize);
        IPage<TeamBug> pageList = bugService.page(page, queryWrapper);
        return Result.OK(pageList);
    }

    @AutoLog(value = "团队协作-Bug-提交反馈")
    @Operation(summary = "团队协作-Bug-提交反馈")
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody TeamBug bug) {
        if (StringUtils.isBlank(bug.getDescription())) {
            return Result.error("问题描述不能为空");
        }
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser != null) {
            bug.setReporterId(loginUser.getId());
            bug.setReporterName(loginUser.getRealname());
            bug.setCreateBy(loginUser.getUsername());
        }
        bug.setStatus("open");
        bug.setCreateTime(new Date());
        bugService.save(bug);
        return Result.OK("反馈已提交");
    }

    @AutoLog(value = "团队协作-Bug-状态流转")
    @Operation(summary = "团队协作-Bug-状态流转（open/processing/resolved/closed）")
    @PutMapping(value = "/status")
    public Result<String> changeStatus(@RequestParam(name = "id", required = true) String id,
            @RequestParam(name = "status", required = true) String status) {
        TeamBug bug = bugService.getById(id);
        if (bug == null) {
            return Result.error("未找到对应数据");
        }
        bug.setStatus(status);
        bug.setUpdateTime(new Date());
        bugService.updateById(bug);
        return Result.OK("状态已更新");
    }

    @AutoLog(value = "团队协作-Bug-通过id删除")
    @Operation(summary = "团队协作-Bug-通过id删除")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(@RequestParam(name = "id", required = true) String id) {
        bugService.removeById(id);
        return Result.OK("删除成功!");
    }

    @AutoLog(value = "团队协作-Bug-批量删除")
    @Operation(summary = "团队协作-Bug-批量删除")
    @DeleteMapping(value = "/deleteBatch")
    public Result<String> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        bugService.removeByIds(Arrays.asList(ids.split(",")));
        return Result.OK("批量删除成功!");
    }

    @Operation(summary = "团队协作-Bug-通过id查询")
    @GetMapping(value = "/queryById")
    public Result<TeamBug> queryById(@RequestParam(name = "id", required = true) String id) {
        TeamBug bug = bugService.getById(id);
        if (bug == null) {
            return Result.error("未找到对应数据");
        }
        return Result.OK(bug);
    }
}
