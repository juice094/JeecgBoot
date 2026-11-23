package org.jeecg.modules.team.controller;

import java.util.ArrayList;
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
import org.jeecg.modules.team.entity.TeamApprovalFlowRecon;
import org.jeecg.modules.team.entity.TeamApprovalStep;
import org.jeecg.modules.team.entity.TeamApprovalTemplate;
import org.jeecg.modules.team.service.ITeamApprovalService;
import org.jeecg.modules.team.service.ITeamApprovalTemplateService;
import org.jeecg.modules.team.service.impl.TeamApprovalFlowSyncer;
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
 * @Description: 团队协作-审批单（轻量状态机 v2）
 * @Author: juice094
 * @Date: 2026-11-18
 * @Version: V2.0
 * <p>
 * 提交 body 示例：{"approval":{"category":"加班申请","title":"...","reason":"...","startDate":"2026-11-20","endDate":"2026-11-21"},
 * "steps":[{"approverName":"贺致远","approverRole":"部门主管"}]}
 * steps 可省略：服务端优先按 category 查模板生成审批链；模板缺失时再回退到客户端 steps。
 */
@Tag(name = "团队协作-审批",
        description = "全局约定：统一返回 Result 包装；401=未登录，403=无权限；业务错误 success=false。")
@RestController
@RequestMapping("/team/approval")
public class TeamApprovalController extends JeecgController<TeamApproval, ITeamApprovalService> {
    @Autowired
    private ITeamApprovalService approvalService;

    @Autowired
    private ITeamApprovalTemplateService templateService;

    @Autowired
    private TeamApprovalFlowSyncer flowSyncer;

    @AutoLog(value = "团队协作-审批-分页列表")
    @Operation(summary = "团队协作-审批-分页列表（tab 过滤：pending/returned/approved/rejected/canceled/全部）")
    @RequiresPermissions("team:approval:list")
    @GetMapping(value = "/list")
    public Result<IPage<TeamApproval>> queryPageList(TeamApproval approval,
                                                     @Parameter(name = "approverId", description = "按审批人过滤（只能查自己，用于“我已审批”）")
                                                     @RequestParam(name = "approverId", required = false) String approverId,
                                                     @Parameter(name = "pageNo", description = "页码，从 1 开始", example = "1")
                                                     @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                     @Parameter(name = "pageSize", description = "每页条数", example = "10")
                                                     @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                     HttpServletRequest req) {
        QueryWrapper<TeamApproval> queryWrapper = QueryGenerator.initQueryWrapper(approval, req.getParameterMap());
        if (StringUtils.isNotBlank(approverId)) {
            LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
            if (loginUser == null) {
                return Result.error("未登录或登录态已过期");
            }
            if (!approverId.equals(loginUser.getId())) {
                return Result.error("无权按他人身份查询已办单据");
            }
            List<String> ids = approvalService.listApprovalIdsByApprover(loginUser.getId(), loginUser.getRealname());
            if (ids.isEmpty()) {
                Page<TeamApproval> empty = new Page<>(pageNo, pageSize);
                empty.setTotal(0);
                empty.setRecords(new ArrayList<>());
                return Result.OK(empty);
            }
            queryWrapper.in("id", ids);
        }
        queryWrapper.orderByDesc("create_time");
        Page<TeamApproval> page = new Page<>(pageNo, pageSize);
        IPage<TeamApproval> pageList = approvalService.page(page, queryWrapper);
        return Result.OK(pageList);
    }

    @AutoLog(value = "团队协作-审批-提交申请")
    @Operation(summary = "团队协作-审批-提交申请（模板优先，回退到 steps）",
            description = "申请人为当前登录人（服务端回填）。审批链来源：优先按 category 查启用模板；模板缺失时回退到 body.steps。"
                    + "校验：标题非空、起止日期先后、审批链不得含申请人。")
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
            description = "条件更新防并发双推。错误：状态非 pending、步骤已被操作、越权、自审。")
    @RequiresPermissions("team:approval:approve")
    @PostMapping(value = "/approve")
    public Result<String> approve(@Parameter(name = "id", description = "审批单ID", required = true) @RequestParam(name = "id", required = true) String id,
                                  @Parameter(name = "comment", description = "审批意见（可空）") @RequestParam(name = "comment", required = false) String comment) {
        approvalService.approve(id, comment);
        return Result.OK("已通过");
    }

    @AutoLog(value = "团队协作-审批-拒绝")
    @Operation(summary = "团队协作-审批-拒绝当前步骤",
            description = "守卫口径与 /approve 一致；拒绝后单据终态 rejected，不可再操作。")
    @RequiresPermissions("team:approval:reject")
    @PostMapping(value = "/reject")
    public Result<String> reject(@Parameter(name = "id", description = "审批单ID", required = true) @RequestParam(name = "id", required = true) String id,
                                 @Parameter(name = "comment", description = "审批意见（可空）") @RequestParam(name = "comment", required = false) String comment) {
        approvalService.reject(id, comment);
        return Result.OK("已拒绝");
    }

    @AutoLog(value = "团队协作-审批-退回申请人")
    @Operation(summary = "团队协作-审批-退回至申请人",
            description = "当前步骤标记 returned，整单状态 returned，申请人可 resubmit。守卫口径与 /approve 一致。")
    @RequiresPermissions("team:approval:approve")
    @PostMapping(value = "/return")
    public Result<String> returnToApplicant(@Parameter(name = "id", description = "审批单ID", required = true) @RequestParam(name = "id", required = true) String id,
                                            @Parameter(name = "comment", description = "退回说明（可空）") @RequestParam(name = "comment", required = false) String comment) {
        approvalService.returnToApplicant(id, comment);
        return Result.OK("已退回");
    }

    @AutoLog(value = "团队协作-审批-撤回")
    @Operation(summary = "团队协作-审批-撤回（申请人）",
            description = "仅 pending 且所有步骤均未操作时允许，终态 canceled。")
    @RequiresPermissions("team:approval:submit")
    @PostMapping(value = "/cancel")
    public Result<String> cancel(@Parameter(name = "id", description = "审批单ID", required = true) @RequestParam(name = "id", required = true) String id) {
        approvalService.cancel(id);
        return Result.OK("已撤回");
    }

    @AutoLog(value = "团队协作-审批-重提")
    @Operation(summary = "团队协作-审批-重提（申请人）",
            description = "仅 returned 状态可调用；按模板/传入 steps 重建审批链，状态回 pending。")
    @RequiresPermissions("team:approval:submit")
    @PostMapping(value = "/resubmit")
    public Result<TeamApproval> resubmit(@RequestBody TeamApprovalResubmitBody body) {
        if (body == null || body.getId() == null) {
            return Result.error("缺少 id");
        }
        return Result.OK(approvalService.resubmit(body.getId(), body.getSteps()));
    }

    @Operation(summary = "团队协作-审批-步骤链查询")
    @RequiresPermissions("team:approval:steps")
    @GetMapping(value = "/steps")
    public Result<List<TeamApprovalStep>> steps(@Parameter(name = "id", description = "审批单ID", required = true) @RequestParam(name = "id", required = true) String id) {
        return Result.OK(approvalService.listSteps(id));
    }

    @AutoLog(value = "团队协作-审批-待我审批")
    @Operation(summary = "团队协作-审批-待我审批分页",
            description = "approver_id 优先匹配；历史数据（approver_id 为空）按 approver_name 兜底。")
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
        return Result.OK(approvalService.todo(loginUser.getId(), loginUser.getRealname(), pageNo, pageSize));
    }

    @Operation(summary = "团队协作-审批-按类别取字段契约",
            description = "前端按返回的 fieldSchema 渲染表单。类别无启用模板或模板未配字段时返回 null，"
                    + "前端应回退到通用表单（仅标题/事由/日期）。")
    @RequiresPermissions("team:approval:submit")
    @GetMapping(value = "/template/schema")
    public Result<TeamApprovalTemplate> templateSchema(
            @Parameter(name = "category", description = "申请类别", required = true)
            @RequestParam(name = "category") String category) {
        return Result.OK(templateService.findEnabledByCategory(category));
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

    @AutoLog(value = "团队协作-审批-双写对账")
    @Operation(summary = "团队协作-审批-双写对账（team 状态 vs 引擎实例状态）",
            description = "内部观测用（W3，charter R2）。默认返回不一致清单（diffOnly=true），"
                    + "diffOnly=false 返回全部对账行。引擎状态口径：RUNNING:<节点>/FINISHED:<endEvent>/DELETED/SYNC_FAILED。")
    @RequiresPermissions("team:approval:list")
    @GetMapping(value = "/flow/recon")
    public Result<List<TeamApprovalFlowRecon>> flowRecon(
            @Parameter(name = "diffOnly", description = "true=仅不一致清单；false=全部对账行")
            @RequestParam(name = "diffOnly", defaultValue = "true") boolean diffOnly) {
        return Result.OK(diffOnly ? flowSyncer.listDiffs() : flowSyncer.listAll());
    }

    /**
     * 提交请求体：单据 + 可选步骤模板
     */
    @Schema(description = "审批提交请求体（单据 + 可选步骤模板）",
            example = "{\"approval\":{\"category\":\"加班申请\",\"title\":\"版本冲刺加班\",\"startDate\":\"2026-11-20\",\"endDate\":\"2026-11-20\"},"
                    + "\"steps\":[{\"approverName\":\"贺致远\",\"approverRole\":\"部门主管\"}]}")
    public static class TeamApprovalSubmitBody {
        @Schema(description = "审批单（申请人服务端回填；category/title 必填）")
        private TeamApproval approval;
        @Schema(description = "可选审批步骤模板；省略时按 category 取模板")
        private List<TeamApprovalStep> steps;

        /** 已在 approval.formData 中传递；此处保留 setter 兼容旧调用 */

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

    /**
     * 重提请求体：id + 可选步骤（模板优先）
     */
    @Schema(description = "审批重提请求体")
    public static class TeamApprovalResubmitBody {
        @Schema(description = "审批单ID")
        private String id;
        @Schema(description = "可选新步骤；省略时按 category 取模板")
        private List<TeamApprovalStep> steps;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public List<TeamApprovalStep> getSteps() {
            return steps;
        }

        public void setSteps(List<TeamApprovalStep> steps) {
            this.steps = steps;
        }
    }
}
