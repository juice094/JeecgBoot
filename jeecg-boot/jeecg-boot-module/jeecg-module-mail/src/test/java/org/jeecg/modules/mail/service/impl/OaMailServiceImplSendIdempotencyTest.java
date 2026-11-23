package org.jeecg.modules.mail.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.Properties;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.mail.constant.MailRedisConstants;
import org.jeecg.modules.mail.delivery.ImapSmtpConnector;
import org.jeecg.modules.mail.dto.SendMailMessageReqDTO;
import org.jeecg.modules.mail.entity.OaMailAccount;
import org.jeecg.modules.mail.mapper.OaMailAccountMapper;
import org.jeecg.modules.mail.mapper.OaMailMessageIndexMapper;
import org.jeecg.modules.mail.support.AesMailPasswordCodec;
import org.jeecg.modules.mail.support.MailPasswordCodec;
import org.jeecg.modules.mail.sync.MailMessageOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * @Description: 员工写信幂等单测（D-20261119-87 ①）
 * @Author: mail-dev
 * @Date: 2026-11-23
 *
 * StringRedisTemplate 走 Mockito；锁定口径：首次 incr=1 放行并设 TTL 24h、
 * 同 key 24h 内重复提交拒绝且只发一次、空 key 跳过检查、Redis 挂 fail-fast 拒发。
 */
@SuppressWarnings("unchecked")
class OaMailServiceImplSendIdempotencyTest {

    private static final String TEST_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private OaMailAccountMapper mapper;
    private ImapSmtpConnector connector;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private OaMailServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(OaMailAccountMapper.class);
        connector = mock(ImapSmtpConnector.class);
        MailPasswordCodec codec = new AesMailPasswordCodec(TEST_KEY);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new OaMailServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "indexMapper", mock(OaMailMessageIndexMapper.class));
        ReflectionTestUtils.setField(service, "connector", connector);
        ReflectionTestUtils.setField(service, "passwordCodec", codec);
        ReflectionTestUtils.setField(service, "ownershipGuard", new OaMailOwnershipGuard());
        ReflectionTestUtils.setField(service, "operator", mock(MailMessageOperator.class));
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);
        when(mapper.selectById("acc-1")).thenReturn(account(codec));
        when(connector.buildSmtpSession(any())).thenReturn(Session.getInstance(new Properties()));
    }

    private OaMailAccount account(MailPasswordCodec codec) {
        OaMailAccount account = new OaMailAccount();
        account.setId("acc-1");
        account.setEmail("me@example.com");
        account.setImapHost("imap.example.com");
        account.setImapPort(993);
        account.setImapUsername("u");
        account.setImapPassword(codec.encode("imap-pwd"));
        account.setSmtpHost("smtp.example.com");
        account.setSmtpPort(465);
        account.setSmtpUsername("u");
        account.setSmtpPassword(codec.encode("smtp-pwd"));
        return account;
    }

    private SendMailMessageReqDTO request(String idempotencyKey) {
        SendMailMessageReqDTO req = new SendMailMessageReqDTO();
        req.setAccountId("acc-1");
        req.setTo("to@example.com");
        req.setSubject("hello");
        req.setHtml("<p>hi</p>");
        req.setIdempotencyKey(idempotencyKey);
        return req;
    }

    @Test
    void sendWithKey_firstSubmission_acquiresSlotWithTtlAndSendsOnce() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);

        String result = service.sendMessage(request("idem-key-1"));

        assertEquals("发送成功", result);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOps).increment(key.capture());
        assertTrue(key.getValue().startsWith(MailRedisConstants.SEND_IDEMPOTENT_KEY_PREFIX + "acc-1:"));
        verify(redisTemplate).expire(eq(key.getValue()),
                eq(java.time.Duration.ofSeconds(MailRedisConstants.SEND_IDEMPOTENT_TTL_SECONDS)));
        verify(connector, times(1)).send(any(), any(MimeMessage.class));
    }

    @Test
    void sendWithKey_duplicateWithinTtl_rejectedAndSendsOnlyOnce() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L, 2L);

        service.sendMessage(request("idem-key-1"));
        JeecgBootException ex = assertThrows(JeecgBootException.class,
                () -> service.sendMessage(request("idem-key-1")));

        assertTrue(ex.getMessage().contains("重复提交"));
        verify(connector, times(1)).send(any(), any(MimeMessage.class));
    }

    @Test
    void sendWithoutKey_skipsIdempotencyCheckAndSends() throws Exception {
        String result = service.sendMessage(request(null));

        assertEquals("发送成功", result);
        verifyNoInteractions(valueOps);
        verify(connector, times(1)).send(any(), any(MimeMessage.class));
    }

    @Test
    void sendWithKey_redisDown_throwsFailFastAndDoesNotSend() throws Exception {
        when(valueOps.increment(anyString()))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThrows(JeecgBootException.class, () -> service.sendMessage(request("idem-key-1")));
        verify(connector, never()).send(any(), any(MimeMessage.class));
    }
}
