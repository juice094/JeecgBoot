package org.jeecg.modules.team.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @Description: 团队协作-审批单（轻量状态机）
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 * <p>
 * 提交 body 示例：{"category":"加班申请","title":"...","reason":"...","startDate":"2026-11-20","endDate":"2026-11-21",
 * "steps":[{"approverName":"贺致远","approverRole":"部门主管"},{"approverName":"黄茜茜","approverRole":"副总经理"}]}
 */
@Tag(name = "团队协作-审批",
        description = "全局约定：统一返回 Result 包装（success/message/code/result/timestamp）；"
                + "401=未登录或登录态过期，403=无权限（角色未勾选 team:approval:* 按钮权限）；"
                + "业务错误 success=false，message 为中文错误文案（越权审批、重复审批、状态机拒绝等）。")
@RestController
@RequestMapping("/team/approval")
public class TeamApprovalController extends JeecgController<TeamApproval, ITeamApprovalService> {
    @Autowired
    private ITeamApprovalService approvalService;

    @AutoLog(value = "团队协作-审批-分页列表")
    @Operation(summary = "团队协作-审批-分页列表（tab 过滤：pending/approved/rejected/全部）")
    @RequiresPermissions("team:approval:list")
    @GetMapping(value = "/list")
    public Result<IPage<TeamApproval>> queryPageList(TeamApproval approval,
                                                     @Parameter(name = "pageNo", description = "页码，从 1 开始", example = "1")
                                                     @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                     @Parameter(name = "pageSize", description = "每页条数", example = "10")
                                                     @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                     HttpServletRequest req) {
        QueryWrapper<TeamApproval> queryWrapper = QueryGenerator.initQueryWrapper(approval, req.getParameterMap());
        queryWrapper.orderByDesc("create_time");
        Page<TeamApproval> page = new Page<>(pageNo, pageSize);
        IPage<TeamApproval> pageList = approvalService.page(page, queryWrapper);
        return Result.OK(pageList);
    }

    @AutoLog(value = "团队协作-审批-提交申请")
    @Operation(summary = "团队协作-审批-提交申请（含审批步骤模板）",
            description = "申请人为当前登录人（服务端回填，不可指定他人）。步骤按 stepNo 从 1 顺序执行，全通过=approved，任一拒绝=rejected。"
                    + "错误场景（均 success=false）：事项标题为空；开始日期晚于结束日期；步骤模板为空；步骤缺审批人。")
    @RequiresPermissions("team:approval:submit")
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
    @Operation(summary = "团队协作-审批-通过当前步骤",
            description = "错误场景（均 success=false）：单据不存在或状态非 pending「审批单当前状态为 X，不可审批」；"
                    + "当前步骤已被操作「禁止重复审批」；当前登录人非当前步骤审批人「越权审批: 当前步骤审批人为 …，登录人为 …」；"
                    + "申请人审批自己的单据「申请人不能审批自己的单据」。")
    @RequiresPermissions("team:approval:approve")
    @PostMapping(value = "/approve")
    public Result<String> approve(@Parameter(name = "id", description = "审批单ID", required = true) @RequestParam(name = "id", required = true) String id,
                                  @Parameter(name = "comment", description = "审批意见（可空）") @RequestParam(name = "comment", required = false) String comment) {
        approvalService.approve(id, comment);
        return Result.OK("已通过");
    }

    @AutoLog(value = "团队协作-审批-拒绝")
    @Operation(summary = "团队协作-审批-拒绝当前步骤",
            description = "守卫口径与 /approve 一致（越权/重复/自审/非 pending 均 success=false）；拒绝后单据终态 rejected，不可再操作。")
    @RequiresPermissions("team:approval:reject")
    @PostMapping(value = "/reject")
    public Result<String> reject(@Parameter(name = "id", description = "审批单ID", required = true) @RequestParam(name = "id", required = true) String id,
                                 @Parameter(name = "comment", description = "审批意见（可空）") @RequestParam(name = "comment", required = false) String comment) {
        approvalService.reject(id, comment);
        return Result.OK("已拒绝");
    }

    @Operation(summary = "团队协作-审批-步骤链查询")
    @RequiresPermissions("team:approval:steps")
    @GetMapping(value = "/steps")
    public Result<List<TeamApprovalStep>> steps(@Parameter(name = "id", description = "审批单ID", required = true) @RequestParam(name = "id", required = true) String id) {
        return Result.OK(approvalService.listSteps(id));
    }

    @AutoLog(value = "团队协作-审批-待我审批")
    @Operation(summary = "团队协作-审批-待我审批分页",
            description = "两步走：step 表按登录人姓名+wait 查，回主表过滤 pending 且 current_step 匹配。"
                    + "已知限制：步骤表只冗余审批人姓名，重名者会互相看到对方待办；严格身份校验待 team_approval_step "
                    + "加 approver_id 列后升级（与越权校验同一立项）。")
    @RequiresPermissions("team:approval:todo")
    @GetMapping(value = "/todo")
    public Result<IPage<TeamApproval>> todo(
            @Parameter(name = "pageNo", description = "页码，从 1 开始", example = "1")
            @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
            @Parameter(name = "pageSize", description = "每页条数", example = "10")
            @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize) {
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser == null) {
            return Result.error("未登录或登录态已过期");
        }
        return Result.OK(approvalService.todo(loginUser.getRealname(), pageNo, pageSize));
    }

    @Operation(summary = "团队协作-审批-通过id查询")
    @RequiresPermissions("team:approval:queryById")
    @GetMapping(value = "/queryById")
    public Result<TeamApproval> queryById(@Parameter(name = "id", description = "审批单ID", required = true) @RequestParam(name = "id", required = true) String id) {
        TeamApproval approval = approvalService.getById(id);
        if (approval == null) {
            return Result.error("未找到对应数据");
        }
        return Result.OK(approval);
    }

    /**
     * 提交请求体：单据 + 步骤模板
     */
    @Schema(description = "审批提交请求体（单据 + 步骤模板）",
            example = "{\"approval\":{\"category\":\"加班申请\",\"title\":\"版本冲刺加班\",\"startDate\":\"2026-11-20\",\"endDate\":\"2026-11-20\"},"
                    + "\"steps\":[{\"approverName\":\"贺致远\",\"approverRole\":\"部门主管\"}]}")
    public static class TeamApprovalSubmitBody {
        @Schema(description = "审批单（申请人服务端回填；category/title 必填）")
        private TeamApproval approval;
        @Schema(description = "审批步骤模板，按数组顺序执行；每步 approverName 必填")
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
