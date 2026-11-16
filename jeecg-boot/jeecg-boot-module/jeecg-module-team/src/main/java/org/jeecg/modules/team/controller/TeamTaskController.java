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
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.service.ITeamTaskService;
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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @Description: 团队协作-任务
 * @Author: Kimi Code (issue #2/#10)
 * @Date: 2026-11-17
 * @Version: V1.0
 *
 * 权限说明（试点骨架）：
 * - 未标注 @RequiresPermissions，即所有登录用户可访问（Shiro 默认拦截 /jeecg-boot/** 非排除路径）。
 * - 后续接入按钮权限时：菜单挂上权限编码（如 team:task:list/add/edit/delete），
 *   方法补 @RequiresPermissions("team:task:list")，勿绕过数据权限/多租户拦截器。
 */
@Tag(name = "团队协作-任务")
@RestController
@RequestMapping("/team/task")
public class TeamTaskController extends JeecgController<TeamTask, ITeamTaskService> {
    @Autowired
    private ITeamTaskService teamTaskService;

    /**
     * 分页列表查询
     * 前端合同：jeecg-team-web 任务看板（pageNo/pageSize，返回 records/total）
     */
    @AutoLog(value = "团队协作-任务-分页列表查询")
    @Operation(summary = "团队协作-任务-分页列表查询")
    @GetMapping(value = "/list")
    public Result<IPage<TeamTask>> queryPageList(TeamTask teamTask,
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest req) {
        QueryWrapper<TeamTask> queryWrapper = QueryGenerator.initQueryWrapper(teamTask, req.getParameterMap());
        queryWrapper.orderByAsc("sort_no").orderByDesc("create_time");
        Page<TeamTask> page = new Page<>(pageNo, pageSize);
        IPage<TeamTask> pageList = teamTaskService.page(page, queryWrapper);
        return Result.OK(pageList);
    }

    /**
     * 添加
     */
    @AutoLog(value = "团队协作-任务-添加")
    @Operation(summary = "团队协作-任务-添加")
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody TeamTask teamTask) {
        if (StringUtils.isBlank(teamTask.getTitle())) {
            return Result.error("任务标题不能为空");
        }
        if (StringUtils.isBlank(teamTask.getStatus())) {
            teamTask.setStatus("todo");
        }
        if (StringUtils.isBlank(teamTask.getPriority())) {
            teamTask.setPriority("中");
        }
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser != null) {
            teamTask.setCreateBy(loginUser.getUsername());
        }
        teamTask.setCreateTime(new Date());
        teamTaskService.save(teamTask);
        return Result.OK("添加成功！");
    }

    /**
     * 编辑
     */
    @AutoLog(value = "团队协作-任务-编辑")
    @Operation(summary = "团队协作-任务-编辑")
    @PutMapping(value = "/edit")
    public Result<String> edit(@RequestBody TeamTask teamTask) {
        TeamTask entity = teamTaskService.getById(teamTask.getId());
        if (entity == null) {
            return Result.error("未找到对应数据");
        }
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser != null) {
            teamTask.setUpdateBy(loginUser.getUsername());
        }
        teamTask.setUpdateTime(new Date());
        teamTaskService.updateById(teamTask);
        return Result.OK("编辑成功!");
    }

    /**
     * 通过id删除
     */
    @AutoLog(value = "团队协作-任务-通过id删除")
    @Operation(summary = "团队协作-任务-通过id删除")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(@RequestParam(name = "id", required = true) String id) {
        teamTaskService.removeById(id);
        return Result.OK("删除成功!");
    }

    /**
     * 批量删除
     */
    @AutoLog(value = "团队协作-任务-批量删除")
    @Operation(summary = "团队协作-任务-批量删除")
    @DeleteMapping(value = "/deleteBatch")
    public Result<String> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        this.teamTaskService.removeByIds(Arrays.asList(ids.split(",")));
        return Result.OK("批量删除成功!");
    }

    /**
     * 通过id查询
     */
    @Operation(summary = "团队协作-任务-通过id查询")
    @GetMapping(value = "/queryById")
    public Result<TeamTask> queryById(@Parameter(name = "id", description = "主键", required = true) @RequestParam(name = "id", required = true) String id) {
        TeamTask teamTask = teamTaskService.getById(id);
        if (teamTask == null) {
            return Result.error("未找到对应数据");
        }
        return Result.OK(teamTask);
    }
}
