package org.jeecg.modules.flow.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.exception.JeecgBootBizTipException;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.flow.enums.FlowApproveAction;
import org.jeecg.modules.flow.service.FlowApproveService;
import org.jeecg.modules.flow.service.FlowExpenseService;
import org.jeecg.modules.flow.service.FlowLeaveService;
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
    private static final int ERR_CODE_BAD_REQUEST = 400;
    /** 流程类型 key：报销（expense-process.bpmn20.xml 的 process id 为 expenseProcess，此处用短 key） */
    private static final String PROCESS_KEY_EXPENSE = "expense";
    /** 流程类型 key：请假 */
    private static final String PROCESS_KEY_LEAVE = "leave";

    @Autowired
    private FlowExpenseService flowExpenseService;
    @Autowired
    private FlowLeaveService flowLeaveService;
    @Autowired
    private FlowApproveService flowApproveService;

    @RequiresPermissions("team:flow:process:list")
    @Operation(summary = "流程定义列表（含在途实例数）")
    @GetMapping("/process/list")
    public Result<List<Map<String, Object>>> processList() {
        return Result.OK(flowApproveService.listProcessDefinitions());
    }

    @RequiresPermissions("team:flow:process:start")
    @Operation(summary = "发起流程（报销/请假统一收口）")
    @PostMapping("/process/start")
    public Result<String> start(
            @Parameter(description = "流程类型：expense 报销 / leave 请假", required = true) @RequestParam(name = "processKey") String processKey,
            @Parameter(description = "报销金额（报销必填，大于 0）", required = false) @RequestParam(name = "amount", required = false) BigDecimal amount,
            @Parameter(description = "事由", required = false) @RequestParam(name = "reason", required = false) String reason,
            @Parameter(description = "附件说明", required = false) @RequestParam(name = "attachmentNote", required = false) String attachmentNote,
            @Parameter(description = "请假天数（请假必填，1-365 的整数）", required = false) @RequestParam(name = "days", required = false) String days,
            @Parameter(description = "部门主管办理人（报销必填）", required = false) @RequestParam(name = "deptManager", required = false) String deptManager,
            @Parameter(description = "副总办理人（报销必填）", required = false) @RequestParam(name = "vp", required = false) String vp) {
        String username = currentUsername();
        String processInstanceId;
        switch (processKey) {
            case PROCESS_KEY_EXPENSE:
                processInstanceId = flowExpenseService.startExpenseProcess(
                        username, amount, reason, attachmentNote, deptManager, vp);
                break;
            case PROCESS_KEY_LEAVE:
                processInstanceId = flowLeaveService.startLeaveProcess(username, parseLeaveDays(days), reason);
                break;
            default:
                throw new JeecgBootBizTipException("不支持的流程类型: " + processKey, ERR_CODE_BAD_REQUEST);
        }
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

    /**
     * 严格解析请假天数：缺参/非整数（如 3.5）直接 400，
     * 不走 Integer 自动转换（Spring 转换器对 3.5 会静默截断为 3）。
     * 区间校验（1-365）在 FlowLeaveService 内收口。
     */
    private Integer parseLeaveDays(String days) {
        if (StringUtils.isBlank(days)) {
            throw new JeecgBootBizTipException("请假天数不能为空", ERR_CODE_BAD_REQUEST);
        }
        try {
            return Integer.valueOf(days.trim());
        } catch (NumberFormatException e) {
            throw new JeecgBootBizTipException("请假天数必须是 1-365 的整数", ERR_CODE_BAD_REQUEST);
        }
    }

    private String currentUsername() {
        LoginUser loginUser = (LoginUser) SecurityUtils.getSubject().getPrincipal();
        if (loginUser == null) {
            throw new JeecgBootBizTipException("未登录或会话已过期", ERR_CODE_UNAUTHORIZED);
        }
        return loginUser.getUsername();
    }
}
