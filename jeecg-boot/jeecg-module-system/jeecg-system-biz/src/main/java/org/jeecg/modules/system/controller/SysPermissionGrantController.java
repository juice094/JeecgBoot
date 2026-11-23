package org.jeecg.modules.system.controller;

import java.util.List;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.system.entity.SysPermissionGrant;
import org.jeecg.modules.system.service.ISysPermissionGrantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * @Description: 权限确权-授权申请/审批/回收
 * @Author: juice094
 * @Date: 2026-11-19
 * @Version: V1.0
 * <p>
 * 契约见 docs/permission-governance/PERMISSION-GOVERNANCE.md §7。
 */
@Slf4j
@Tag(name = "权限确权-授权治理")
@RestController
@RequestMapping("/sys/permissionGrant")
public class SysPermissionGrantController {

    @Autowired
    private ISysPermissionGrantService permissionGrantService;

    @Operation(summary = "提交授权申请")
    @RequiresPermissions("system:permissionGrant:apply")
    @PostMapping("/apply")
    public Result<SysPermissionGrant> apply(@RequestBody SysPermissionGrant grant) {
        return Result.OK(permissionGrantService.apply(grant));
    }

    @Operation(summary = "审批通过")
    @RequiresPermissions("system:permissionGrant:approve")
    @PostMapping("/approve")
    public Result<String> approve(@RequestParam("id") String id,
                                  @RequestParam(value = "comment", required = false) String comment) {
        permissionGrantService.approve(id, comment);
        return Result.OK("审批通过");
    }

    @Operation(summary = "拒绝")
    @RequiresPermissions("system:permissionGrant:approve")
    @PostMapping("/reject")
    public Result<String> reject(@RequestParam("id") String id,
                                 @RequestParam(value = "comment", required = false) String comment) {
        permissionGrantService.reject(id, comment);
        return Result.OK("已拒绝");
    }

    @Operation(summary = "退回")
    @RequiresPermissions("system:permissionGrant:approve")
    @PostMapping("/return")
    public Result<String> returnBack(@RequestParam("id") String id,
                                     @RequestParam(value = "comment", required = false) String comment) {
        permissionGrantService.returnBack(id, comment);
        return Result.OK("已退回");
    }

    @Operation(summary = "重提")
    @RequiresPermissions("system:permissionGrant:apply")
    @PostMapping("/resubmit")
    public Result<String> resubmit(@RequestParam("id") String id) {
        permissionGrantService.resubmit(id);
        return Result.OK("已重提");
    }

    @Operation(summary = "撤销")
    @RequiresPermissions("system:permissionGrant:apply")
    @PostMapping("/cancel")
    public Result<String> cancel(@RequestParam("id") String id) {
        permissionGrantService.cancel(id);
        return Result.OK("已撤销");
    }

    @Operation(summary = "回收已生效授权")
    @RequiresPermissions("system:permissionGrant:revoke")
    @PostMapping("/revoke")
    public Result<String> revoke(@RequestParam("id") String id,
                                 @RequestParam("comment") String comment) {
        permissionGrantService.revoke(id, comment);
        return Result.OK("已回收");
    }

    @Operation(summary = "分页查询")
    @RequiresPermissions("system:permissionGrant:list")
    @GetMapping("/page")
    public Result<IPage<SysPermissionGrant>> page(@RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                                                  @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                                                  @RequestParam(name = "status", required = false) String status,
                                                  @RequestParam(name = "subjectType", required = false) String subjectType) {
        return Result.OK(permissionGrantService.pageQuery(pageNo, pageSize, status, subjectType));
    }

    @Operation(summary = "我发起的 / 待我审批的")
    @RequiresPermissions("system:permissionGrant:list")
    @GetMapping("/my")
    public Result<List<SysPermissionGrant>> my() {
        return Result.OK(permissionGrantService.myGrants());
    }
}
