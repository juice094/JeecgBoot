package org.jeecg.modules.team.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.team.entity.TeamAuditLog;
import org.jeecg.modules.team.service.ITeamAuditLogService;
import org.jeecg.modules.team.service.impl.TeamAuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
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
 * @Description: 团队协作-审计日志查询（管理端追溯用，只读）
 * @Author: juice094
 * @Date: 2026-11-17
 * @Version: V1.0
 * <p>
 * 权限说明：暂不加 @RequiresPermissions——最小权限补丁（F-09）是宿拍板项，
 * 随整体补丁统一处理；审计表本身只追加、无写接口。
 */
@Tag(name = "团队协作-审计日志",
        description = "全局约定：统一返回 Result 包装（success/message/code/result/timestamp）；"
                + "401=未登录或登录态过期，403=无权限（角色未勾选 team:audit:list 按钮权限）。"
                + "本组只读：审计表只追加、无写接口。")
@RestController
@RequestMapping("/team/audit")
public class TeamAuditLogController extends JeecgController<TeamAuditLog, TeamAuditLogService> {

    @Autowired
    private TeamAuditLogService auditLogService;

    @AutoLog(value = "团队协作-审计-分页列表")
    @Operation(summary = "团队协作-审计-分页列表（bizType/action 等字段过滤，create_time 倒序）",
            description = "过滤示例：?bizType=approval&action=reject；单条追溯：?bizId=<单据ID>。")
    @RequiresPermissions("team:audit:list")
    @GetMapping(value = "/list")
    public Result<IPage<TeamAuditLog>> queryPageList(TeamAuditLog auditLog,
                                                     @Parameter(name = "pageNo", description = "页码，从 1 开始", example = "1")
                                                     @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                     @Parameter(name = "pageSize", description = "每页条数", example = "10")
                                                     @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                     HttpServletRequest req) {
        QueryWrapper<TeamAuditLog> queryWrapper = QueryGenerator.initQueryWrapper(auditLog, req.getParameterMap());
        queryWrapper.orderByDesc("create_time");
        Page<TeamAuditLog> page = new Page<>(pageNo, pageSize);
        IPage<TeamAuditLog> pageList = auditLogService.page(page, queryWrapper);
        return Result.OK(pageList);
    }
}
