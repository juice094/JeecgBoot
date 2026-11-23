package org.jeecg.modules.mail.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;

import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.mail.delivery.ImapSmtpConnector;
import org.jeecg.modules.mail.delivery.MailConnectFailedException;
import org.jeecg.modules.mail.dto.ConnectionTestResult;
import org.jeecg.modules.mail.dto.MailAccountBindReqDTO;
import org.jeecg.modules.mail.dto.MailServerConfig;
import java.util.Map;

import org.jeecg.modules.mail.entity.OaMailAccount;
import org.jeecg.modules.mail.mapper.OaMailAccountMapper;
import org.jeecg.modules.mail.mapper.OaMailMessageIndexMapper;
import org.jeecg.modules.mail.support.AesMailPasswordCodec;
import org.jeecg.modules.mail.support.MailPasswordCodec;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.jeecg.modules.mail.entity.OaMailMessageIndex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * @Description: OaMailServiceImpl B-1 单测（Mapper/Connector 走 Mockito，codec 用真实 AES）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 未登录上下文下归属守卫跳过（与 TeamOwnershipGuard 同口径），归属正反用例见
 * OaMailOwnershipGuardTest。
 */
class OaMailServiceImplTest {

    private static final String TEST_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private OaMailAccountMapper mapper;
    private OaMailMessageIndexMapper indexMapper;
    private ImapSmtpConnector connector;
    private MailPasswordCodec codec;
    private OaMailServiceImpl service;

    /**
     * LambdaQueryWrapper 解析 lambda 依赖 MyBatis-Plus 的 TableInfo 缓存；
     * 纯单测无 Spring 容器，需手动初始化，否则 getSqlSegment() 抛
     * "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OaMailMessageIndex.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(OaMailAccountMapper.class);
        connector = mock(ImapSmtpConnector.class);
        codec = new AesMailPasswordCodec(TEST_KEY);
        service = new OaMailServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        indexMapper = mock(OaMailMessageIndexMapper.class);
        ReflectionTestUtils.setField(service, "indexMapper", indexMapper);
        ReflectionTestUtils.setField(service, "connector", connector);
        ReflectionTestUtils.setField(service, "passwordCodec", codec);
        ReflectionTestUtils.setField(service, "ownershipGuard", new OaMailOwnershipGuard());
    }

    private MailAccountBindReqDTO request(String email, String imapPassword, String smtpPassword) {
        MailServerConfig imap = new MailServerConfig();
        imap.setHost("imap.example.com");
        imap.setPort(993);
        imap.setSsl(true);
        imap.setUsername("u");
        imap.setPassword(imapPassword);
        MailServerConfig smtp = new MailServerConfig();
        smtp.setHost("smtp.example.com");
        smtp.setPort(587);
        smtp.setSsl(false);
        smtp.setUsername("u");
        smtp.setPassword(smtpPassword);
        MailAccountBindReqDTO req = new MailAccountBindReqDTO();
        req.setEmail(email);
        req.setImap(imap);
        req.setSmtp(smtp);
        return req;
    }

    private ConnectionTestResult allOk() {
        ConnectionTestResult result = new ConnectionTestResult();
        result.setImapOk(true);
        result.setSmtpOk(true);
        return result;
    }

    @Test
    void bind_imapFails_throwsConnectFailedAndNothingPersisted() {
        ConnectionTestResult failed = new ConnectionTestResult();
        failed.setImapOk(false);
        failed.setSmtpOk(true);
        failed.setImapErrorCode("imap_auth_failed");
        failed.setErrorCode("imap_auth_failed");
        when(connector.testBoth(any(), any())).thenReturn(failed);
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        MailConnectFailedException ex = assertThrows(MailConnectFailedException.class,
                () -> service.bind(request("a@example.com", "p1", "p2")));
        assertTrue(ex.getMessage().contains("IMAP 连接失败"));
        assertTrue(ex.getMessage().contains("imap_auth_failed"));
        assertTrue(ex.getTestResult() != null && !ex.getTestResult().isImapOk());
        verify(mapper, never()).insert(any(OaMailAccount.class));
    }

    @Test
    void bind_bothOk_persistsWithEncryptedPasswords() {
        when(connector.testBoth(any(), any())).thenReturn(allOk());
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(mapper.insert(any(OaMailAccount.class))).thenReturn(1);

        String id = service.bind(request("b@example.com", "imap-plain", "smtp-plain"));

        ArgumentCaptor<OaMailAccount> captor = ArgumentCaptor.forClass(OaMailAccount.class);
        verify(mapper).insert(captor.capture());
        OaMailAccount saved = captor.getValue();
        assertNotEquals("imap-plain", saved.getImapPassword());
        assertNotEquals("smtp-plain", saved.getSmtpPassword());
        assertEquals("imap-plain", codec.decode(saved.getImapPassword()));
        assertEquals("smtp-plain", codec.decode(saved.getSmtpPassword()));
        assertEquals(Integer.valueOf(1), saved.getTestStatus());
    }

    @Test
    void bind_duplicateEmail_rejected() {
        when(connector.testBoth(any(), any())).thenReturn(allOk());
        when(mapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        JeecgBootException ex = assertThrows(JeecgBootException.class,
                () -> service.bind(request("dup@example.com", "p", "p")));
        assertTrue(ex.getMessage().contains("已绑定"));
        verify(connector, never()).testBoth(any(), any());
    }

    @Test
    void bind_missingPassword_rejectedBeforeAnyIO() {
        JeecgBootException ex = assertThrows(JeecgBootException.class,
                () -> service.bind(request("c@example.com", " ", "p2")));
        assertTrue(ex.getMessage().contains("IMAP 密码不能为空"));
        verify(connector, never()).testBoth(any(), any());
    }

    @Test
    void updateAccount_blankPassword_keepsExistingCipher() {
        OaMailAccount existing = new OaMailAccount();
        existing.setId("acc-1");
        existing.setEmail("d@example.com");
        existing.setImapHost("imap.old.com");
        existing.setImapPort(993);
        existing.setImapSsl(true);
        existing.setImapUsername("u");
        existing.setImapPassword(codec.encode("old-imap"));
        existing.setSmtpHost("smtp.old.com");
        existing.setSmtpPort(587);
        existing.setSmtpSsl(false);
        existing.setSmtpUsername("u");
        existing.setSmtpPassword(codec.encode("old-smtp"));
        when(mapper.selectById("acc-1")).thenReturn(existing);
        when(connector.testBoth(any(), any())).thenReturn(allOk());
        when(mapper.updateById(any(OaMailAccount.class))).thenReturn(1);

        MailAccountBindReqDTO req = request("d@example.com", "  ", "new-smtp");
        service.updateAccount("acc-1", req);

        ArgumentCaptor<OaMailAccount> captor = ArgumentCaptor.forClass(OaMailAccount.class);
        verify(mapper).updateById(captor.capture());
        OaMailAccount updated = captor.getValue();
        // IMAP 密码留空 → 保留旧密文；SMTP 密码非空 → 重加密
        assertEquals(existing.getImapPassword(), updated.getImapPassword());
        assertEquals("new-smtp", codec.decode(updated.getSmtpPassword()));
        assertEquals("old-imap", codec.decode(updated.getImapPassword()));
    }

    private OaMailAccount ownedAccount(String id) {
        OaMailAccount acc = new OaMailAccount();
        acc.setId(id);
        acc.setEmail("owner@example.com");
        return acc;
    }

    @Test
    void pageMessages_keywordAndUnread_produceLikeAndReadFlagFilters() {
        when(mapper.selectById("acc-1")).thenReturn(ownedAccount("acc-1"));
        when(indexMapper.selectPage(any(), any(Wrapper.class))).thenReturn(new Page<>());

        service.pageMessages("acc-1", "Inbox", "发票", true, 1, 10);

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(indexMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        // 关键字 -> LIKE 片段；未读 -> read_flag 等值条件
        assertTrue(sql.toLowerCase().contains("like"),
                "keyword 应生成 LIKE 条件，实际 SQL: " + sql);
        assertTrue(sql.toLowerCase().contains("read_flag"),
                "unreadOnly=true 应追加 read_flag 条件，实际 SQL: " + sql);
    }

    @Test
    void pageMessages_noKeywordNoUnread_onlyAccountAndFolderFilters() {
        when(mapper.selectById("acc-1")).thenReturn(ownedAccount("acc-1"));
        when(indexMapper.selectPage(any(), any(Wrapper.class))).thenReturn(new Page<>());

        service.pageMessages("acc-1", "Inbox", null, null, 1, 10);

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(indexMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase();
        // 仅 accountId + canonicalFolder 两个条件
        assertTrue(!sql.contains("read_flag"),
                "unreadOnly 为空时不应追加 read_flag 条件，实际 SQL: " + sql);
        assertTrue(!sql.contains("like"),
                "keyword 为空时不应生成 LIKE 条件，实际 SQL: " + sql);
    }

    @Test
    void pageMessages_keywordOver100_rejectedBeforeQuery() {
        when(mapper.selectById("acc-1")).thenReturn(ownedAccount("acc-1"));
        String tooLong = "a".repeat(101);

        JeecgBootException ex = assertThrows(JeecgBootException.class,
                () -> service.pageMessages("acc-1", null, tooLong, null, 1, 10));
        assertTrue(ex.getMessage().contains("关键字过长"));
        verify(indexMapper, never()).selectPage(any(), any());
    }

    @Test
    void pageMessages_blankAccountId_rejected() {
        JeecgBootException ex = assertThrows(JeecgBootException.class,
                () -> service.pageMessages("  ", null, null, null, 1, 10));
        assertTrue(ex.getMessage().contains("accountId 不能为空"));
    }

    @Test
    void unbind_unknownId_rejected() {
        when(mapper.selectById("ghost")).thenReturn(null);
        JeecgBootException ex = assertThrows(JeecgBootException.class, () -> service.unbind("ghost"));
        assertTrue(ex.getMessage().contains("不存在"));
    }
}
