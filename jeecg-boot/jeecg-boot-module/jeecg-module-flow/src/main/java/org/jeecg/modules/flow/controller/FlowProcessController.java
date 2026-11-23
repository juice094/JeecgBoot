package org.jeecg.modules.flow.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootBizTipException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.flow.enums.FlowApproveAction;
import org.jeecg.modules.flow.service.FlowApproveService;
import org.jeecg.modules.flow.service.FlowExpenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 流程查询与审批端点（W2）。全部端点挂 @RequiresPermissions（team:flow:*，W2 权限码入库），
 * 申请人/办理人均取当前登录人；办理人链（deptManager/vp）由发起参数显式传入（W3 再接组织链）。
 */
@Slf4j
@Tag(name = "流程审批")
@RestController
@RequestMapping("/flow")
public class FlowProcessController {

    private static final int ERR_CODE_UNAUTHORIZED = 401;

    @Autowired
    private FlowExpenseService flowExpenseService;
    @Autowired
    private FlowApproveService flowApproveService;

    @RequiresPermissions("team:flow:process:list")
    @Operation(summary = "流程定义列表（含在途实例数）")
    @GetMapping("/process/list")
    public Result<List<Map<String, Object>>> processList() {
        return Result.OK(flowApproveService.listProcessDefinitions());
    }

    @RequiresPermissions("team:flow:process:start")
    @Operation(summary = "发起报销流程")
    @PostMapping("/process/start")
    public Result<String> startExpense(
            @Parameter(description = "报销金额") @RequestParam(name = "amount") BigDecimal amount,
            @Parameter(description = "报销事由") @RequestParam(name = "reason") String reason,
            @Parameter(description = "附件说明", required = false) @RequestParam(name = "attachmentNote", required = false) String attachmentNote,
            @Parameter(description = "部门主管办理人") @RequestParam(name = "deptManager") String deptManager,
            @Parameter(description = "副总办理人") @RequestParam(name = "vp") String vp) {
        String processInstanceId = flowExpenseService.startExpenseProcess(
                currentUsername(), amount, reason, attachmentNote, deptManager, vp);
        return Result.OK("流程实例启动成功: " + processInstanceId);
    }

    @RequiresPermissions("team:flow:approve:todo")
    @Operation(summary = "我的待办")
    @GetMapping("/approve/todo")
    public Result<List<Map<String, Object>>> todo() {
        return Result.OK(flowApproveService.listTodoTasks(currentUsername()));
    }

    @RequiresPermissions("team:flow:approve:done")
    @Operation(summary = "我的已办")
    @GetMapping("/approve/done")
    public Result<List<Map<String, Object>>> done() {
        return Result.OK(flowApproveService.listDoneTasks(currentUsername()));
    }

    @RequiresPermissions("team:flow:approve:handle")
    @Operation(summary = "审批办理（通过/驳回）")
    @PostMapping("/approve/handle")
    public Result<String> handle(
            @Parameter(description = "任务 ID") @RequestParam(name = "taskId") String taskId,
            @Parameter(description = "动作：approve 通过 / reject 驳回") @RequestParam(name = "action") FlowApproveAction action,
            @Parameter(description = "审批意见", required = false) @RequestParam(name = "comment", required = false) String comment) {
        String processInstanceId = flowApproveService.handleTask(currentUsername(), taskId, action, comment);
        return Result.OK("办理成功: " + processInstanceId);
    }

    @RequiresPermissions("team:flow:instance:detail")
    @Operation(summary = "流程实例详情（进度+历史轨迹）")
    @GetMapping("/instance/detail")
    public Result<Map<String, Object>> detail(
            @Parameter(description = "流程实例 ID") @RequestParam(name = "processInstanceId") String processInstanceId) {
        return Result.OK(flowApproveService.instanceDetail(processInstanceId));
    }

    private String currentUsername() {
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser == null) {
            throw new JeecgBootBizTipException("未登录或会话已过期", ERR_CODE_UNAUTHORIZED);
        }
        return loginUser.getUsername();
    }
}
