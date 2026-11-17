package org.jeecg.modules.team.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.springframework.stereotype.Component;

/**
 * @Description: 团队协作-归属校验（F-09：高危端点 service 层权限兜底）
 * @Author: Kimi Code
 * @Date: 2026-11-17
 * @Version: V1.0
 *
 * 规则：登录人 == 数据归属人（创建人/指派人）或登录人是系统管理员（用户名 admin）。
 * 超管放行与 jeecg ShiroRealm 的超管语义一致；异常文案带归属现场。
 *
 * 未登录上下文（定时任务/集成测试等非 HTTP 入口）跳过归属校验——HTTP 入口经 Shiro
 * 过滤必有登录态，与模块既有 `if (loginUser != null)` 惯例一致；调用方在 HTTP 链路
 * 不应依赖该跳过行为。
 */
@Component
public class TeamOwnershipGuard {

    private static final String ADMIN_USERNAME = "admin";

    /**
     * 校验当前登录人对指定归属数据是否有操作权；未登录上下文跳过
     *
     * @param ownerUsername 数据归属人登录名（create_by）
     * @param bizDesc       业务描述（用于异常文案）
     */
    public void requireOwnerOrAdmin(String ownerUsername, String bizDesc) {
        LoginUser loginUser = currentLoginUserOrNull();
        if (loginUser == null) {
            return;
        }
        if (ADMIN_USERNAME.equals(loginUser.getUsername())) {
            return;
        }
        if (StringUtils.isNotBlank(ownerUsername) && ownerUsername.equals(loginUser.getUsername())) {
            return;
        }
        throw new JeecgBootException("无权限操作" + bizDesc + ": 归属人=" + ownerUsername
                + "，登录人=" + loginUser.getUsername());
    }

    /**
     * 任务状态流转归属：指派人、创建人或管理员；未登录上下文跳过
     */
    public void requireTaskOperator(String assigneeId, String assigneeName,
            String ownerUsername, String bizId) {
        LoginUser loginUser = currentLoginUserOrNull();
        if (loginUser == null) {
            return;
        }
        if (ADMIN_USERNAME.equals(loginUser.getUsername())) {
            return;
        }
        if (StringUtils.isNotBlank(assigneeId) && assigneeId.equals(loginUser.getId())) {
            return;
        }
        if (StringUtils.isNotBlank(ownerUsername) && ownerUsername.equals(loginUser.getUsername())) {
            return;
        }
        throw new JeecgBootException("无权限流转任务: id=" + bizId
                + "（指派人=" + assigneeName + "，创建人=" + ownerUsername
                + "，登录人=" + loginUser.getRealname() + "）");
    }

    private LoginUser currentLoginUserOrNull() {
        return (LoginUser) SecurityUtils.getSubject().getPrincipal();
    }
}
