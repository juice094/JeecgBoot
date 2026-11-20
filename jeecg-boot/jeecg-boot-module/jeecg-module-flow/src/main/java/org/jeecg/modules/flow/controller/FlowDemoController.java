package org.jeecg.modules.flow.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.config.shiro.IgnoreAuth;
import org.jeecg.modules.flow.service.FlowLeaveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flowable 最小请假流程端点，用于 W1 引擎落地端到端冒烟
 */
@Slf4j
@Tag(name = "Flowable最小请假流程")
@RestController
@RequestMapping("/flow/demo")
public class FlowDemoController {

    @Autowired
    private FlowLeaveService flowLeaveService;

    @IgnoreAuth
    @Operation(summary = "启动请假流程")
    @PostMapping("/leave/start")
    public Result<String> start(@RequestParam(name = "applicant") String applicant,
                                @RequestParam(name = "days") Integer days,
                                @RequestParam(name = "reason", required = false) String reason) {
        String processInstanceId = flowLeaveService.startLeaveProcess(applicant, days, reason);
        return Result.OK("流程实例启动成功: " + processInstanceId);
    }

    @IgnoreAuth
    @Operation(summary = "主管审批（自动归档完结）")
    @PostMapping("/leave/approve")
    public Result<String> approve(@RequestParam(name = "processInstanceId") String processInstanceId,
                                  @RequestParam(name = "approved", defaultValue = "true") boolean approved) {
        boolean finished = flowLeaveService.approve(processInstanceId, approved);
        return Result.OK(finished ? "实例已完结" : "实例仍在运行");
    }

    @IgnoreAuth
    @Operation(summary = "查询流程实例状态")
    @GetMapping("/leave/status")
    public Result<Map<String, Object>> status(@RequestParam(name = "processInstanceId") String processInstanceId) {
        boolean finished = flowLeaveService.isProcessFinished(processInstanceId);
        List<String> openTasks = flowLeaveService.listOpenTaskNames(processInstanceId);
        Map<String, Object> data = new HashMap<>(2);
        data.put("finished", finished);
        data.put("openTasks", openTasks);
        return Result.OK(data);
    }
}
