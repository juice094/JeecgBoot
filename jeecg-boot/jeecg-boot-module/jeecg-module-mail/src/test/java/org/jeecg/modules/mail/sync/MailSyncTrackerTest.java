package org.jeecg.modules.mail.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.mail.constant.MailRedisConstants;
import org.jeecg.modules.mail.delivery.MailDeliveryExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * @Description: MailSyncTracker Redis 化单测（D-20261119-87 ③）
 * @Author: mail-dev
 * @Date: 2026-11-23
 *
 * StringRedisTemplate 走 Mockito；锁定口径：SET NX EX 占位、同账号重入拒绝、
 * 任务结束删 key、Redis 挂 submit fail-fast、isRunning 按 false 兜底。
 */
@SuppressWarnings("unchecked")
class MailSyncTrackerTest {

    private static final String ACCOUNT_ID = "acc-1";
    private static final String EXPECTED_KEY = MailRedisConstants.SYNC_RUNNING_KEY_PREFIX + ACCOUNT_ID;

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private MailDeliveryExecutor deliveryExecutor;
    private MailSyncEngine syncEngine;
    private MailSyncTracker tracker;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        deliveryExecutor = mock(MailDeliveryExecutor.class);
        syncEngine = mock(MailSyncEngine.class);
        tracker = new MailSyncTracker();
        ReflectionTestUtils.setField(tracker, "stringRedisTemplate", redisTemplate);
        ReflectionTestUtils.setField(tracker, "deliveryExecutor", deliveryExecutor);
        ReflectionTestUtils.setField(tracker, "syncEngine", syncEngine);
    }

    @Test
    void submit_firstTime_acquiresLockWithNxExAndSubmits() {
        when(valueOps.setIfAbsent(eq(EXPECTED_KEY), eq("1"), any(Duration.class))).thenReturn(true);

        String taskId = tracker.submit(ACCOUNT_ID);

        assertEquals(ACCOUNT_ID, taskId);
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).setIfAbsent(eq(EXPECTED_KEY), eq("1"), ttl.capture());
        assertEquals(MailRedisConstants.SYNC_RUNNING_TTL_SECONDS, ttl.getValue().getSeconds());
        verify(deliveryExecutor).submit(any(Runnable.class));
    }

    @Test
    void submit_alreadyRunning_doesNotSubmitAgain() {
        when(valueOps.setIfAbsent(eq(EXPECTED_KEY), eq("1"), any(Duration.class))).thenReturn(false);

        String taskId = tracker.submit(ACCOUNT_ID);

        assertEquals(ACCOUNT_ID, taskId);
        verify(deliveryExecutor, never()).submit(any(Runnable.class));
    }

    @Test
    void submit_redisDown_throwsFailFastAndDoesNotSubmit() {
        when(valueOps.setIfAbsent(eq(EXPECTED_KEY), eq("1"), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThrows(JeecgBootException.class, () -> tracker.submit(ACCOUNT_ID));
        verify(deliveryExecutor, never()).submit(any(Runnable.class));
    }

    @Test
    void taskCompletes_releasesLock() {
        when(valueOps.setIfAbsent(eq(EXPECTED_KEY), eq("1"), any(Duration.class))).thenReturn(true);
        AtomicReference<Runnable> task = new AtomicReference<>();
        doAnswer(invocation -> {
            task.set(invocation.getArgument(0));
            return null;
        }).when(deliveryExecutor).submit(any(Runnable.class));

        tracker.submit(ACCOUNT_ID);
        task.get().run();

        verify(syncEngine).syncAccount(ACCOUNT_ID);
        verify(redisTemplate).delete(EXPECTED_KEY);
    }

    @Test
    void taskFails_releasesLockAndSwallowsException() {
        when(valueOps.setIfAbsent(eq(EXPECTED_KEY), eq("1"), any(Duration.class))).thenReturn(true);
        AtomicReference<Runnable> task = new AtomicReference<>();
        doAnswer(invocation -> {
            task.set(invocation.getArgument(0));
            return null;
        }).when(deliveryExecutor).submit(any(Runnable.class));
        org.mockito.Mockito.doThrow(new RuntimeException("imap down"))
                .when(syncEngine).syncAccount(ACCOUNT_ID);

        tracker.submit(ACCOUNT_ID);
        task.get().run();

        verify(redisTemplate, times(1)).delete(EXPECTED_KEY);
    }

    @Test
    void isRunning_reflectsKeyPresence() {
        when(redisTemplate.hasKey(EXPECTED_KEY)).thenReturn(true);
        assertTrue(tracker.isRunning(ACCOUNT_ID));
        when(redisTemplate.hasKey(EXPECTED_KEY)).thenReturn(false);
        assertFalse(tracker.isRunning(ACCOUNT_ID));
    }

    @Test
    void isRunning_redisDown_returnsFalseAndDoesNotThrow() {
        when(redisTemplate.hasKey(anyString()))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        assertFalse(tracker.isRunning(ACCOUNT_ID));
    }
}
