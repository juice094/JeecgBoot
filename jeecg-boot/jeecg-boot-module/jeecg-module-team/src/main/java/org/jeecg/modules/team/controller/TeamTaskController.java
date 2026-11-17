package org.jeecg.modules.team.controller;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.common.system.vo.LoginUser;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.modules.team.entity.TeamTask;
import org.jeecg.modules.team.enums.TeamAuditAction;
import org.jeecg.modules.team.enums.TeamAuditBizType;
import org.jeecg.modules.team.service.ITeamTaskService;
import org.jeecg.modules.team.service.impl.TeamAuditLogger;
import org.jeecg.modules.team.vo.TeamTaskBoard;
import org.jeecg.modules.team.vo.TeamTaskDetail;
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
 * @Author: juice094 (issue #2/#10)
 * @Date: 2026-11-17
 * @Version: V1.0
 * <p>
 * 权限说明（F-09）：全部端点挂 @RequiresPermissions（team:task:*），
 * jeecg 超管（admin）默认放行；普通角色需在角色管理勾选对应按钮权限，否则 403。
 * 高危操作 service 层另有归属校验（编辑/删除限创建人或管理员，流转限指派人/创建人/管理员）。
 */
@Tag(name = "团队协作-任务",
        description = "全局约定：统一返回 Result 包装（success/message/code/result/timestamp）；"
                + "401=未登录或登录态过期，403=无权限（角色未勾选 team:task:* 按钮权限）；"
                + "业务错误 success=false，message 为中文错误文案（状态机拒绝、归属校验失败等）。")
@RestController
@RequestMapping("/team/task")
public class TeamTaskController extends JeecgController<TeamTask, ITeamTaskService> {
    @Autowired
    private ITeamTaskService teamTaskService;

    @Autowired
    private TeamAuditLogger auditLogger;

    /**
     * 分页列表查询
     * 前端合同：jeecg-team-web 任务看板（pageNo/pageSize，返回 records/total）
     */
    @AutoLog(value = "团队协作-任务-分页列表查询")
    @Operation(summary = "团队协作-任务-分页列表查询",
            description = "实体非空字段自动转为等值过滤（QueryGenerator 口径）；排序 sort_no 升序、create_time 降序。")
    @RequiresPermissions("team:task:list")
    @GetMapping(value = "/list")
    public Result<IPage<TeamTask>> queryPageList(TeamTask teamTask,
                                                 @Parameter(name = "pageNo", description = "页码，从 1 开始", example = "1")
                                                 @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                 @Parameter(name = "pageSize", description = "每页条数", example = "10")
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
    @Operation(summary = "团队协作-任务-添加",
            description = "服务端强制 status=todo（建单即 done 会被覆盖）；priority 缺省「中」。"
                    + "错误场景：标题为空 success=false「任务标题不能为空」。")
    @RequiresPermissions("team:task:add")
    @PostMapping(value = "/add")
    public Result<String> add(@RequestBody TeamTask teamTask) {
        if (StringUtils.isBlank(teamTask.getTitle())) {
            return Result.error("任务标题不能为空");
        }
        // 新建任务一律从 todo 开始：状态变更只能走 /status 状态机，避免建单即 done 绕过校验。
        teamTask.setStatus("todo");
        if (StringUtils.isBlank(teamTask.getPriority())) {
            teamTask.setPriority("中");
        }
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser != null) {
            teamTask.setCreateBy(loginUser.getUsername());
        }
        teamTask.setCreateTime(new Date());
        // 租户内序号：并发下依赖 uk_team_task_no 唯一索引兜底
        if (teamTask.getTaskNo() == null) {
            teamTask.setTaskNo(teamTaskService.nextTaskNo());
        }
        teamTaskService.save(teamTask);

        // 时间线首条事件：建单
        auditLogger.log(TeamAuditBizType.TASK, teamTask.getId(), teamTask.getTitle(),
                TeamAuditAction.CREATE, null, teamTask.getStatus(), null);
        return Result.OK("添加成功！");
    }

    /**
     * 编辑
     */
    @AutoLog(value = "团队协作-任务-编辑")
    @Operation(summary = "团队协作-任务-编辑",
            description = "状态字段以库中原值回填（防前端篡改），归属校验在 service 层。"
                    + "错误场景：单据不存在「未找到对应数据」；非创建人或管理员被 service 层拒绝。")
    @RequiresPermissions("team:task:edit")
    @PutMapping(value = "/edit")
    public Result<String> edit(@RequestBody TeamTask teamTask) {
        if (teamTaskService.getById(teamTask.getId()) == null) {
            return Result.error("未找到对应数据");
        }
        // 归属校验、状态回填（强制库中原值）与落库均在 service.edit（F-09）
        teamTaskService.edit(teamTask);
        return Result.OK("编辑成功!");
    }

    /**
     * 状态流转（状态机：todo→doing/done；doing→todo/done；done→todo/doing）
     */
    @AutoLog(value = "团队协作-任务-状态流转")
    @Operation(summary = "团队协作-任务-状态流转",
            description = "状态机：todo↔doing↔done 双向可达（6 种流转全合法）；done 不是终态。"
                    + "错误场景：目标状态非 todo/doing/done，或任务不存在/已逻辑删，success=false 且 message 给出 from→to 说明。")
    @RequiresPermissions("team:task:status")
    @PutMapping(value = "/status")
    public Result<String> changeStatus(
            @Parameter(name = "id", description = "任务ID", required = true, example = "demo-task-001")
            @RequestParam(name = "id", required = true) String id,
            @Parameter(name = "status", description = "目标状态: todo/doing/done", required = true, example = "doing")
            @RequestParam(name = "status", required = true) String status) {
        teamTaskService.changeStatus(id, status);
        return Result.OK("状态已更新");
    }

    /**
     * 临期任务：截止日期在 days 天内且未完成；scope=me 只看指派给当前登录人的
     */
    @AutoLog(value = "团队协作-任务-临期查询")
    @Operation(summary = "团队协作-任务-临期查询（默认7天内未完成，scope=me 只看我的）",
            description = "错误场景：days 超出 0-90 返回 success=false「临期窗口非法」。")
    @RequiresPermissions("team:task:dueSoon")
    @GetMapping(value = "/dueSoon")
    public Result<List<TeamTask>> dueSoon(
            @Parameter(name = "days", description = "临期窗口天数（0-90）") @RequestParam(name = "days", defaultValue = "7") int days,
            @Parameter(name = "scope", description = "me=仅当前登录人被指派任务") @RequestParam(name = "scope", required = false) String scope) {
        String assigneeId = null;
        if ("me".equals(scope)) {
            LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
            assigneeId = loginUser != null ? loginUser.getId() : null;
        }
        return Result.OK(teamTaskService.dueSoon(days, assigneeId));
    }

    /**
     * 任务看板：todo/doing/done 三组一次拉取（组内 sort_no asc, create_time desc）
     * 响应示例：{"todo":[{任务}],"doing":[{任务}],"done":[{任务}]}
     */
    @AutoLog(value = "团队协作-任务-看板")
    @Operation(summary = "团队协作-任务-看板（按状态分三组一次拉取）",
            description = "响应结构 {\"todo\":[...],\"doing\":[...],\"done\":[...]}，组内按 sort_no 升序、"
                    + "create_time 降序；不分页，任务量上行后需改分页或按列拉取。")
    @RequiresPermissions("team:task:board")
    @GetMapping(value = "/board")
    public Result<TeamTaskBoard> board() {
        return Result.OK(teamTaskService.board());
    }

    /**
     * 通过id删除（逻辑删：deleted=1；限创建人或管理员，service 层校验）
     */
    @AutoLog(value = "团队协作-任务-通过id删除")
    @Operation(summary = "团队协作-任务-通过id删除",
            description = "逻辑删除（deleted=1），限创建人或管理员；对外契约不变。"
                    + "错误场景：非创建人或管理员被 service 层拒绝（success=false）。")
    @RequiresPermissions("team:task:delete")
    @DeleteMapping(value = "/delete")
    public Result<String> delete(
            @Parameter(name = "id", description = "任务ID", required = true, example = "demo-task-001")
            @RequestParam(name = "id", required = true) String id) {
        teamTaskService.deleteOne(id);
        return Result.OK("删除成功!");
    }

    /**
     * 批量删除（逻辑删；逐单归属校验，任一不过整体拒绝）
     */
    @AutoLog(value = "团队协作-任务-批量删除")
    @Operation(summary = "团队协作-任务-批量删除",
            description = "逻辑删除（deleted=1），逐单限创建人或管理员。错误场景：任一单归属校验不过，整体拒绝。")
    @RequiresPermissions("team:task:delete")
    @DeleteMapping(value = "/deleteBatch")
    public Result<String> deleteBatch(
            @Parameter(name = "ids", description = "任务ID，多个逗号分隔", required = true, example = "demo-task-001,demo-task-002")
            @RequestParam(name = "ids", required = true) String ids) {
        this.teamTaskService.deleteBatch(Arrays.asList(ids.split(",")));
        return Result.OK("批量删除成功!");
    }

    /**
     * 任务详情聚合：任务主体 + 评论 + 时间线，详情页一次拉取
     */
    @AutoLog(value = "团队协作-任务-详情聚合")
    @Operation(summary = "团队协作-任务-详情聚合",
            description = "返回 { task, comments, timeline }；timeline 复用 team_audit_log，按创建时间升序。"
                    + "错误场景：任务不存在「未找到对应数据」。")
    @RequiresPermissions("team:task:queryById")
    @GetMapping(value = "/detail")
    public Result<TeamTaskDetail> detail(
            @Parameter(name = "id", description = "任务ID", required = true)
            @RequestParam(name = "id", required = true) String id) {
        return Result.OK(teamTaskService.detail(id));
    }

    /**
     * 通过id查询
     */
    @Operation(summary = "团队协作-任务-通过id查询")
    @RequiresPermissions("team:task:queryById")
    @GetMapping(value = "/queryById")
    public Result<TeamTask> queryById(@Parameter(name = "id", description = "主键", required = true) @RequestParam(name = "id", required = true) String id) {
        TeamTask teamTask = teamTaskService.getById(id);
        if (teamTask == null) {
            return Result.error("未找到对应数据");
        }
        return Result.OK(teamTask);
    }
}
