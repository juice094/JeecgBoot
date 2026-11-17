package org.jeecg.modules.mail.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.jeecg.modules.mail.delivery.ImapSmtpConnector;
import org.jeecg.modules.mail.dto.MailServerConfig;
import org.jeecg.modules.mail.entity.OaMailAccount;
import org.jeecg.modules.mail.entity.OaMailFolderState;
import org.jeecg.modules.mail.entity.OaMailMessageIndex;
import org.jeecg.modules.mail.mapper.OaMailAccountMapper;
import org.jeecg.modules.mail.mapper.OaMailFolderStateMapper;
import org.jeecg.modules.mail.mapper.OaMailMessageIndexMapper;
import org.jeecg.modules.mail.support.AesMailPasswordCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.Wrapper;

/**
 * @Description: 同步引擎 GreenMail 正路径集成测试（B-2 stretch）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 本地起 GreenMail 的 IMAP/SMTP 服务，真实投递 → 真实 IMAP 增量同步 → 验证信封索引提取。
 * Mapper 层 Mockito（无数据库），I/O 层全真实。验证分级：集成测试（进程内真实协议交互）。
 */
class MailSyncGreenMailTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_IMAP);

    private static final String TEST_KEY = java.util.Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    @Test
    @SuppressWarnings("unchecked")
    void doSync_againstGreenMail_indexesDeliveredMessage() throws Exception {
        // 准备：真实账号 + 真实投递一封邮件
        GreenMailUser user = greenMail.setUser("me@local.test", "secret");
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(ServerSetupTest.SMTP.getPort());
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setTo("me@local.test");
        helper.setFrom("boss@local.test", "老板");
        helper.setSubject("GreenMail 同步验证");
        helper.setText("这是一封集成测试邮件正文。", false);
        sender.send(message);
        assertTrue(greenMail.waitForIncomingEmail(5000, 1), "邮件未在超时前投递到 GreenMail");

        // 引擎：真实 IMAP，Mapper 走 mock
        OaMailAccountMapper accountMapper = mock(OaMailAccountMapper.class);
        OaMailFolderStateMapper folderStateMapper = mock(OaMailFolderStateMapper.class);
        OaMailMessageIndexMapper indexMapper = mock(OaMailMessageIndexMapper.class);
        when(folderStateMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(folderStateMapper.selectOne(any(Wrapper.class), eq(false))).thenReturn(null);
        when(folderStateMapper.insert(any(OaMailFolderState.class))).thenReturn(1);
        when(folderStateMapper.updateById(any(OaMailFolderState.class))).thenReturn(1);
        when(indexMapper.selectOne(any(Wrapper.class), eq(false))).thenReturn(null);
        when(indexMapper.insert(any(OaMailMessageIndex.class))).thenReturn(1);
        when(indexMapper.updateById(any(OaMailMessageIndex.class))).thenReturn(1);

        MailSyncEngine engine = new MailSyncEngine();
        ReflectionTestUtils.setField(engine, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(engine, "folderStateMapper", folderStateMapper);
        ReflectionTestUtils.setField(engine, "indexMapper", indexMapper);
        ReflectionTestUtils.setField(engine, "connector", new ImapSmtpConnector());
        ReflectionTestUtils.setField(engine, "passwordCodec", new AesMailPasswordCodec(TEST_KEY));

        MailServerConfig imap = new MailServerConfig();
        imap.setHost("127.0.0.1");
        imap.setPort(ServerSetupTest.IMAP.getPort());
        imap.setSsl(false);
        imap.setUsername("me@local.test");
        imap.setPassword("secret");
        Store store = new ImapSmtpConnector().openImapStore(imap);
        try {
            OaMailAccount account = new OaMailAccount();
            account.setId("acc-green");
            engine.doSync(account, store);
        } finally {
            store.close();
        }

        // 断言：真实信封被正确提取入库，游标推进
        ArgumentCaptor<OaMailMessageIndex> indexCaptor = ArgumentCaptor.forClass(OaMailMessageIndex.class);
        verify(indexMapper).insert(indexCaptor.capture());
        OaMailMessageIndex row = indexCaptor.getValue();
        assertEquals("acc-green", row.getAccountId());
        assertEquals("Inbox", row.getCanonicalFolder());
        assertEquals("GreenMail 同步验证", row.getSubject());
        assertEquals("老板", row.getFromName());
        assertNotNull(row.getImapUid());
        assertTrue(row.getImapUid() > 0);
        assertEquals(8, row.getFromAddressHash().length());
        assertNotNull(row.getReceivedTime());

        ArgumentCaptor<OaMailFolderState> stateCaptor = ArgumentCaptor.forClass(OaMailFolderState.class);
        verify(folderStateMapper).updateById(stateCaptor.capture());
        assertEquals(row.getImapUid(), stateCaptor.getValue().getLastUid());
    }
}
