package org.jeecg.modules.team.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.team.entity.TeamApproval;
import org.jeecg.modules.team.entity.TeamApprovalStep;
import org.jeecg.modules.team.service.ITeamApprovalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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
 * @Description: 团队协作-审批单（轻量状态机）
 * @Author: Kimi Code
 * @Date: 2026-11-17
 * @Version: V1.0
 *
 * 提交 body 示例：{"category":"加班申请","title":"...","reason":"...","startDate":"2026-11-20","endDate":"2026-11-21",
 *   "steps":[{"approverName":"贺致远","approverRole":"部门主管"},{"approverName":"黄茜茜","approverRole":"副总经理"}]}
 */
@Tag(name = "团队协作-审批")
@RestController
@RequestMapping("/team/approval")
public class TeamApprovalController extends JeecgController<TeamApproval, ITeamApprovalService> {
    @Autowired
    private ITeamApprovalService approvalService;

    @AutoLog(value = "团队协作-审批-分页列表")
    @Operation(summary = "团队协作-审批-分页列表（tab 过滤：pending/approved/rejected/全部）")
    @GetMapping(value = "/list")
    public Result<IPage<TeamApproval>> queryPageList(TeamApproval approval,
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
            HttpServletRequest req) {
        QueryWrapper<TeamApproval> queryWrapper = QueryGenerator.initQueryWrapper(approval, req.getParameterMap());
        queryWrapper.orderByDesc("create_time");
        Page<TeamApproval> page = new Page<>(pageNo, pageSize);
        IPage<TeamApproval> pageList = approvalService.page(page, queryWrapper);
        return Result.OK(pageList);
    }

    @AutoLog(value = "团队协作-审批-提交申请")
    @Operation(summary = "团队协作-审批-提交申请（含审批步骤模板）")
    @PostMapping(value = "/submit")
    public Result<TeamApproval> submit(@RequestBody TeamApprovalSubmitBody body) {
        TeamApproval approval = body.getApproval();
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser != null) {
            approval.setApplicantId(loginUser.getId());
            approval.setApplicantName(loginUser.getRealname());
            approval.setCreateBy(loginUser.getUsername());
        }
        TeamApproval saved = approvalService.submit(approval, body.getSteps());
        return Result.OK(saved);
    }

    @AutoLog(value = "团队协作-审批-通过当前步骤")
    @Operation(summary = "团队协作-审批-通过当前步骤")
    @PostMapping(value = "/approve")
    public Result<String> approve(@RequestParam(name = "id", required = true) String id,
            @RequestParam(name = "comment", required = false) String comment) {
        approvalService.approve(id, comment);
        return Result.OK("已通过");
    }

    @AutoLog(value = "团队协作-审批-拒绝")
    @Operation(summary = "团队协作-审批-拒绝当前步骤")
    @PostMapping(value = "/reject")
    public Result<String> reject(@RequestParam(name = "id", required = true) String id,
            @RequestParam(name = "comment", required = false) String comment) {
        approvalService.reject(id, comment);
        return Result.OK("已拒绝");
    }

    @Operation(summary = "团队协作-审批-步骤链查询")
    @GetMapping(value = "/steps")
    public Result<List<TeamApprovalStep>> steps(@RequestParam(name = "id", required = true) String id) {
        return Result.OK(approvalService.listSteps(id));
    }

    @Operation(summary = "团队协作-审批-通过id查询")
    @GetMapping(value = "/queryById")
    public Result<TeamApproval> queryById(@RequestParam(name = "id", required = true) String id) {
        TeamApproval approval = approvalService.getById(id);
        if (approval == null) {
            return Result.error("未找到对应数据");
        }
        return Result.OK(approval);
    }

    /** 提交请求体：单据 + 步骤模板 */
    public static class TeamApprovalSubmitBody {
        private TeamApproval approval;
        private List<TeamApprovalStep> steps;

        public TeamApproval getApproval() {
            return approval;
        }

        public void setApproval(TeamApproval approval) {
            this.approval = approval;
        }

        public List<TeamApprovalStep> getSteps() {
            return steps;
        }

        public void setSteps(List<TeamApprovalStep> steps) {
            this.steps = steps;
        }
    }
}
