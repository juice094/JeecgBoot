package org.jeecg.modules.mail.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.system.vo.LoginUser;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * @Description: OaMailOwnershipGuard 归属校验单测（mockStatic Shiro，范式同 team 域）
 * @Author: mail-dev
 * @Date: 2026-11-17
 */
class OaMailOwnershipGuardTest {

    private OaMailOwnershipGuard guard = new OaMailOwnershipGuard();

    private LoginUser user(String username) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUsername(username);
        return loginUser;
    }

    @Test
    void loggedOutContext_skipsCheck() {
        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(null);
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::getSubject).thenReturn(subject);
            assertDoesNotThrow(() -> guard.requireOwnerOrAdmin("anyone", "邮箱绑定"));
        }
    }

    @Test
    void owner_matches_passes() {
        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(user("zhangsan"));
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::getSubject).thenReturn(subject);
            assertDoesNotThrow(() -> guard.requireOwnerOrAdmin("zhangsan", "邮箱绑定"));
            assertFalse(guard.isAdmin());
            assertTrue("zhangsan".equals(guard.currentUsernameOrNull()));
        }
    }

    @Test
    void admin_passes() {
        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(user("admin"));
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::getSubject).thenReturn(subject);
            assertDoesNotThrow(() -> guard.requireOwnerOrAdmin("lisi", "邮箱绑定"));
            assertTrue(guard.isAdmin());
        }
    }

    @Test
    void otherUser_deniedWithContext() {
        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(user("wangwu"));
        try (MockedStatic<SecurityUtils> ms = mockStatic(SecurityUtils.class)) {
            ms.when(SecurityUtils::getSubject).thenReturn(subject);
            JeecgBootException ex = assertThrows(JeecgBootException.class,
                    () -> guard.requireOwnerOrAdmin("lisi", "邮箱绑定"));
            assertTrue(ex.getMessage().contains("归属人=lisi"));
            assertTrue(ex.getMessage().contains("登录人=wangwu"));
        }
    }
}
