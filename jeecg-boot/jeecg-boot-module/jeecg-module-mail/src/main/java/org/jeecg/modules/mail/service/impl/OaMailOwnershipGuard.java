package org.jeecg.modules.mail.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.springframework.stereotype.Component;

/**
 * @Description: 员工邮箱绑定归属校验（F-09：service 层权限兜底）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 规则：登录人 == 绑定归属人（create_by）或登录人是系统管理员（用户名 admin）。
 * 与 team 域 TeamOwnershipGuard 同范式（各自模块自持，不跨模块依赖）。
 * 未登录上下文（定时任务/集成测试等非 HTTP 入口）跳过归属校验——HTTP 入口经
 * Shiro 过滤必有登录态，调用方不应依赖该跳过行为。
 */
@Component
public class OaMailOwnershipGuard {

    private static final String ADMIN_USERNAME = "admin";

    /**
     * 校验当前登录人对指定归属绑定是否有操作权；未登录上下文跳过。
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
     * 当前登录人用户名；未登录返回 null。
     */
    public String currentUsernameOrNull() {
        LoginUser loginUser = currentLoginUserOrNull();
        return loginUser == null ? null : loginUser.getUsername();
    }

    /**
     * 是否管理员；未登录返回 false。
     */
    public boolean isAdmin() {
        LoginUser loginUser = currentLoginUserOrNull();
        return loginUser != null && ADMIN_USERNAME.equals(loginUser.getUsername());
    }

    private LoginUser currentLoginUserOrNull() {
        try {
            return (LoginUser) SecurityUtils.getSubject().getPrincipal();
        } catch (org.apache.shiro.UnavailableSecurityManagerException e) {
            // 无 Shiro 运行环境（纯单元测试/独立进程）：按未登录处理，跳过归属校验
            return null;
        }
    }
}
