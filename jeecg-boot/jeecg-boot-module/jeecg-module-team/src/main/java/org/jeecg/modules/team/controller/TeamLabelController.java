package org.jeecg.modules.team.controller;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.team.entity.TeamLabel;
import org.jeecg.modules.team.service.ITeamLabelService;
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
 * @Description: 团队协作-标签（V3.9.6_16，/issues P1 标签体系）
 * @Author: Kimi Code (P1 issues line)
 * @Date: 2026-11-23
 * @Version: V1.0
 *
 * 权限：读 team:task:list；写 team:task:edit（与任务编辑同一权限面）。
 * 删除标签会级联清理 team_task_label 关联（同事务，service 层完成）。
 */
@Tag(name = "团队协作-标签",
        description = "全局约定：统一返回 Result 包装；业务错误 success=false。")
@RestController
@RequestMapping("/team/label")
public class TeamLabelController {

    @Autowired
    private ITeamLabelService teamLabelService;

    /**
     * 标签分页列表（keyword 模糊匹配名称）
     */
    @AutoLog(value = "团队协作-标签-分页列表")
    @Operation(summary = "团队协作-标签-分页列表")
    @RequiresPermissions("team:task:list")
    @GetMapping(value = "/list")
    public Result<IPage<TeamLabel>> queryPageList(TeamLabel teamLabel,
                                                  @Parameter(name = "pageNo", description = "页码，从 1 开始", example = "1")
                                                  @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                  @Parameter(name = "pageSize", description = "每页条数", example = "10")
                                                  @RequestParam(name = "pageSize", defaultValue = "100") Integer pageSize) {
        QueryWrapper<TeamLabel> queryWrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(teamLabel.getName())) {
            queryWrapper.like("name", teamLabel.getName());
        }
        queryWrapper.orderByAsc("name");
        Page<TeamLabel> page = new Page<>(pageNo, pageSize);
        return Result.OK(teamLabelService.page(page, queryWrapper));
    }

    /**
     * 添加标签（名称必填、去空格；重名由 uk_team_label_name 兜底返回业务错误）
     */
    @AutoLog(value = "团队协作-标签-添加")
    @Operation(summary = "团队协作-标签-添加", description = "错误场景：名称为空 success=false「标签名不能为空」。")
    @RequiresPermissions("team:task:edit")
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody TeamLabel teamLabel) {
        if (teamLabel == null || StringUtils.isBlank(teamLabel.getName())) {
            return Result.error("标签名不能为空");
        }
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser != null) {
            teamLabel.setCreateBy(loginUser.getUsername());
        }
        teamLabel.setCreateTime(new Date());
        teamLabelService.save(teamLabel);
        return Result.OK("添加成功！");
    }

    /**
     * 编辑标签
     */
    @AutoLog(value = "团队协作-标签-编辑")
    @Operation(summary = "团队协作-标签-编辑", description = "错误场景：单据不存在「未找到对应数据」。")
    @RequiresPermissions("team:task:edit")
    @PutMapping(value = "/edit")
    public Result<String> edit(@RequestBody TeamLabel teamLabel) {
        if (teamLabelService.getById(teamLabel.getId()) == null) {
            return Result.error("未找到对应数据");
        }
        teamLabel.setUpdateTime(new Date());
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser != null) {
            teamLabel.setUpdateBy(loginUser.getUsername());
        }
        teamLabelService.updateById(teamLabel);
        return Result.OK("编辑成功!");
    }

    /**
     * 删除标签（级联清理任务关联，同事务）
     */
    @AutoLog(value = "团队协作-标签-删除")
    @Operation(summary = "团队协作-标签-删除", description = "级联删除 team_task_label 关联行（同事务）。")
    @RequiresPermissions("team:task:edit")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(
            @Parameter(name = "id", description = "标签ID", required = true) @RequestParam(name = "id", required = true) String id) {
        if (teamLabelService.getById(id) == null) {
            return Result.error("未找到对应数据");
        }
        teamLabelService.deleteWithLinks(id);
        return Result.OK("删除成功!");
    }

    /**
     * 批量删除标签（同样级联清理关联）
     */
    @AutoLog(value = "团队协作-标签-批量删除")
    @Operation(summary = "团队协作-标签-批量删除")
    @RequiresPermissions("team:task:edit")
    @DeleteMapping(value = "/deleteBatch")
    public Result<String> deleteBatch(
            @Parameter(name = "ids", description = "标签ID，多个逗号分隔", required = true) @RequestParam(name = "ids", required = true) String ids) {
        List<String> idList = Arrays.stream(ids.split(","))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        teamLabelService.deleteBatchWithLinks(idList);
        return Result.OK("批量删除成功!");
    }
}
