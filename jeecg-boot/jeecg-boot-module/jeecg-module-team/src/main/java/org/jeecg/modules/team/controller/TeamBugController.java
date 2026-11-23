package org.jeecg.modules.team.controller;

import java.util.Arrays;
import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @Description: 团队协作-Bug反馈
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 * <p>
 * 权限说明（F-09）：全部端点挂 @RequiresPermissions（team:bug:*），jeecg 超管默认放行；
 * 普通角色需在角色管理勾选对应按钮权限，否则 403。删除限提交人或管理员（service 层校验）。
 */
@Tag(name = "团队协作-Bug反馈",
        description = "全局约定：统一返回 Result 包装（success/message/code/result/timestamp）；"
                + "401=未登录或登录态过期，403=无权限（角色未勾选 team:bug:* 按钮权限）；"
                + "业务错误 success=false，message 为中文错误文案（状态机拒绝、归属校验失败等）。")
@RestController
@RequestMapping("/team/bug")
public class TeamBugController extends JeecgController<TeamBug, ITeamBugService> {
    @Autowired
    private ITeamBugService bugService;

    @AutoLog(value = "团队协作-Bug-分页列表")
    @Operation(summary = "团队协作-Bug-分页列表")
    @RequiresPermissions("team:bug:list")
    @GetMapping(value = "/list")
    public Result<IPage<TeamBug>> queryPageList(TeamBug bug,
                                                @Parameter(name = "pageNo", description = "页码，从 1 开始", example = "1")
                                                @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                @Parameter(name = "pageSize", description = "每页条数", example = "10")
                                                @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                HttpServletRequest req) {
        QueryWrapper<TeamBug> queryWrapper = QueryGenerator.initQueryWrapper(bug, req.getParameterMap());
        queryWrapper.orderByDesc("create_time");
        Page<TeamBug> page = new Page<>(pageNo, pageSize);
        IPage<TeamBug> pageList = bugService.page(page, queryWrapper);
        return Result.OK(pageList);
    }

    @AutoLog(value = "团队协作-Bug-提交反馈")
    @Operation(summary = "团队协作-Bug-提交反馈",
            description = "提交人与提交时间由服务端回填；初始状态强制 open。"
                    + "错误场景：问题描述为空 success=false「问题描述不能为空」。")
    @RequiresPermissions("team:bug:add")
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
    @Operation(summary = "团队协作-Bug-状态流转",
            description = "状态机：open→processing/closed；processing→resolved/open；resolved→closed/open；closed 为终态不可再流转。"
                    + "错误场景：目标状态非法、closed 终态再流转、或单据不存在/已逻辑删，success=false 且 message 给出 from→to 说明。")
    @RequiresPermissions("team:bug:status")
    @PutMapping(value = "/status")
    public Result<String> changeStatus(@Parameter(name = "id", description = "Bug 主键", required = true) @RequestParam(name = "id", required = true) String id,
                                       @Parameter(name = "status", description = "目标状态（open/processing/resolved/closed）", required = true) @RequestParam(name = "status", required = true) String status) {
        bugService.changeStatus(id, status);
        return Result.OK("状态已更新");
    }

    @AutoLog(value = "团队协作-Bug-通过id删除")
    @Operation(summary = "团队协作-Bug-通过id删除",
            description = "逻辑删除（deleted=1），限提交人或管理员；对外契约不变。"
                    + "错误场景：非提交人或管理员被 service 层拒绝（success=false）。")
    @RequiresPermissions("team:bug:delete")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(@Parameter(name = "id", description = "Bug 主键", required = true) @RequestParam(name = "id", required = true) String id) {
        bugService.deleteOne(id);
        return Result.OK("删除成功!");
    }

    @AutoLog(value = "团队协作-Bug-批量删除")
    @Operation(summary = "团队协作-Bug-批量删除",
            description = "逻辑删除（deleted=1），逐单限提交人或管理员。错误场景：任一单归属校验不过，整体拒绝。")
    @RequiresPermissions("team:bug:delete")
    @DeleteMapping(value = "/deleteBatch")
    public Result<String> deleteBatch(
            @Parameter(name = "ids", description = "Bug 主键，多个逗号分隔", required = true, example = "demo-bug-001,demo-bug-002")
            @RequestParam(name = "ids", required = true) String ids) {
        for (String bid : Arrays.asList(ids.split(","))) {
            bugService.deleteOne(bid);
        }
        return Result.OK("批量删除成功!");
    }

    @Operation(summary = "团队协作-Bug-通过id查询")
    @RequiresPermissions("team:bug:queryById")
    @GetMapping(value = "/queryById")
    public Result<TeamBug> queryById(@Parameter(name = "id", description = "Bug 主键", required = true) @RequestParam(name = "id", required = true) String id) {
        TeamBug bug = bugService.getById(id);
        if (bug == null) {
            return Result.error("未找到对应数据");
        }
        return Result.OK(bug);
    }
}
