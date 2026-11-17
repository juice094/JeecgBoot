package org.jeecg.modules.mail.sync;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.jeecg.modules.mail.entity.OaMailAccount;
import org.jeecg.modules.mail.mapper.OaMailAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.Wrapper;

/**
 * @Description: MailSyncScheduler 触发逻辑单测（零 Spring，不起真实调度）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 锁定口径：默认关闭不查库不提交；开启后到期账号逐个入队；单轮限量。
 */
class MailSyncSchedulerTest {

    private OaMailAccountMapper accountMapper;
    private MailSyncTracker syncTracker;
    private MailSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        accountMapper = mock(OaMailAccountMapper.class);
        syncTracker = mock(MailSyncTracker.class);
        scheduler = new MailSyncScheduler();
        ReflectionTestUtils.setField(scheduler, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(scheduler, "syncTracker", syncTracker);
    }

    private OaMailAccount account(String id) {
        OaMailAccount account = new OaMailAccount();
        account.setId(id);
        return account;
    }

    @Test
    @SuppressWarnings("unchecked")
    void disabled_doesNothing() {
        ReflectionTestUtils.setField(scheduler, "syncEnabled", false);
        scheduler.scanDueAccounts();
        verify(accountMapper, never()).selectList(any(Wrapper.class));
        verify(syncTracker, never()).submit(any(String.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void enabled_dueAccounts_submitted() {
        ReflectionTestUtils.setField(scheduler, "syncEnabled", true);
        when(accountMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(account("a1"), account("a2")));

        scheduler.scanDueAccounts();

        verify(syncTracker).submit("a1");
        verify(syncTracker).submit("a2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void enabled_noneDue_noSubmit() {
        ReflectionTestUtils.setField(scheduler, "syncEnabled", true);
        when(accountMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        scheduler.scanDueAccounts();
        verify(syncTracker, never()).submit(any(String.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void submitFailure_doesNotBreakOthers() {
        ReflectionTestUtils.setField(scheduler, "syncEnabled", true);
        when(accountMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(account("a1"), account("a2")));
        org.mockito.Mockito.doThrow(new RuntimeException("队列已满"))
                .when(syncTracker).submit("a1");

        scheduler.scanDueAccounts();

        verify(syncTracker).submit("a2");
    }
}
