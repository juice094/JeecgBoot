package org.jeecg.modules.mail.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeMessage;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.mail.delivery.ImapSmtpConnector;
import org.jeecg.modules.mail.dto.OaMailMessageDetailDTO;
import org.jeecg.modules.mail.dto.SendMailMessageReqDTO;
import org.jeecg.modules.mail.entity.OaMailAccount;
import org.jeecg.modules.mail.entity.OaMailFolderState;
import org.jeecg.modules.mail.entity.OaMailMessageIndex;
import org.jeecg.modules.mail.mapper.OaMailAccountMapper;
import org.jeecg.modules.mail.mapper.OaMailFolderStateMapper;
import org.jeecg.modules.mail.mapper.OaMailMessageIndexMapper;
import org.jeecg.modules.mail.service.impl.OaMailServiceImpl;
import org.jeecg.modules.mail.support.AesMailPasswordCodec;
import org.jeecg.modules.mail.support.MailBodyExtractor;
import org.jeecg.modules.mail.support.MailPasswordCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * @Description: B-3/B-5/B-6 GreenMail 集成测试（消息级操作）
 * @Author: mail-dev（B-3/B-5/B-6 续作）
 * @Date: 2026-11-18
 *
 * 真实 SMTP 投递 → 真实 IMAP 抓取正文/已读回写/删除（永久与 Trash 两路）→ 经绑定发送。
 * Mapper 层 Mockito（无数据库），协议层全真实。文件夹映射用调用序应答（canonicalOf 先于
 * resolveRemoteFolder(TRASH)，顺序由实现保证）。
 */
class OaMailMessageOpsGreenMailTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_IMAP);

    private static final String TEST_KEY = java.util.Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private OaMailAccountMapper accountMapper;
    private OaMailMessageIndexMapper indexMapper;
    private OaMailFolderStateMapper folderStateMapper;
    private OaMailServiceImpl service;
    private MailMessageOperator operator;
    private ImapSmtpConnector connector;
    private MailPasswordCodec codec;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OaMailMessageIndex.class);
        TableInfoHelper.initTableInfo(assistant, OaMailFolderState.class);
    }

    @BeforeEach
    void setUp() {
        accountMapper = mock(OaMailAccountMapper.class);
        indexMapper = mock(OaMailMessageIndexMapper.class);
        folderStateMapper = mock(OaMailFolderStateMapper.class);
        connector = new ImapSmtpConnector();
        codec = new AesMailPasswordCodec(TEST_KEY);
        operator = new MailMessageOperator();
        service = new OaMailServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", accountMapper);
        ReflectionTestUtils.setField(service, "indexMapper", indexMapper);
        ReflectionTestUtils.setField(service, "folderStateMapper", folderStateMapper);
        ReflectionTestUtils.setField(service, "connector", connector);
        ReflectionTestUtils.setField(service, "passwordCodec", codec);
        ReflectionTestUtils.setField(service, "operator", operator);
        ReflectionTestUtils.setField(service, "ownershipGuard",
                mock(org.jeecg.modules.mail.service.impl.OaMailOwnershipGuard.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void detail_fetchesBodyAndMarksRead() throws Exception {
        GreenMailUser user = greenMail.setUser("me@local.test", "secret");
        deliver("详情验证", "正文内容XYZ");

        // 文件夹映射：Inbox → INBOX
        OaMailFolderState inboxState = state("acc-1", "INBOX", "Inbox");
        when(folderStateMapper.selectOne(any(Wrapper.class), eq(false))).thenReturn(inboxState);

        OaMailAccount account = account("acc-1");
        when(accountMapper.selectById("acc-1")).thenReturn(account);
        OaMailMessageIndex row = row("msg-1", "acc-1", 1L, false);
        when(indexMapper.selectById("msg-1")).thenReturn(row);

        OaMailMessageDetailDTO detail = service.getMessageDetail("msg-1");

        assertEquals("msg-1", detail.getId());
        assertNotNull(detail.getText());
        assertTrue(detail.getText().contains("正文内容XYZ"));
        assertTrue(detail.getFetchedFromImap());
        assertNotNull(detail.getFetchLatencyMs());
        // 打开即已读：索引回写
        ArgumentCaptor<OaMailMessageIndex> captor = ArgumentCaptor.forClass(OaMailMessageIndex.class);
        verify(indexMapper).updateById(captor.capture());
        assertTrue(captor.getValue().getReadFlag());
        // IMAP 侧也已置 \Seen
        Store verifyStore = openStore();
        try {
            Folder inbox = verifyStore.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            Message fetched = inbox.getMessage(1);
            assertTrue(fetched.isSet(Flags.Flag.SEEN));
            inbox.close(false);
        } finally {
            verifyStore.close();
        }
        assertNotNull(user);
    }

    @Test
    @SuppressWarnings("unchecked")
    void delete_withoutTrashMapping_expungesPermanently() throws Exception {
        greenMail.setUser("me@local.test", "secret");
        deliver("永久删除验证", "bye");
        // 调用序：resolveRemoteFolder(Inbox) → Inbox 映射；canonicalOf(INBOX) → Inbox 映射；
        // resolveRemoteFolder(TRASH) → null（永久删除）
        OaMailFolderState inboxState = state("acc-1", "INBOX", "Inbox");
        when(folderStateMapper.selectOne(any(Wrapper.class), eq(false)))
                .thenReturn(inboxState, inboxState, null);

        OaMailAccount account = account("acc-1");
        when(accountMapper.selectById("acc-1")).thenReturn(account);
        when(indexMapper.selectById("msg-2")).thenReturn(row("msg-2", "acc-1", 1L, true));

        Store store = openStore();
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);
        assertEquals(1, inbox.getMessageCount());
        inbox.close(false);

        service.deleteMessage("msg-2");

        inbox.open(Folder.READ_ONLY);
        assertEquals(0, inbox.getMessageCount(), "无 Trash 映射时应永久删除");
        inbox.close(false);
        store.close();
        verify(indexMapper).deleteById("msg-2");
    }

    @Test
    @SuppressWarnings("unchecked")
    void delete_withTrashMapping_movesToTrash() throws Exception {
        greenMail.setUser("me@local.test", "secret");
        deliver("移入已删除验证", "move me");

        // 调用序：resolveRemoteFolder(Inbox)、canonicalOf(INBOX) → Inbox 映射；resolveRemoteFolder(TRASH) → Trash 映射
        OaMailFolderState inboxState = state("acc-1", "INBOX", "Inbox");
        OaMailFolderState trashState = state("acc-1", "Trash", "Trash");
        greenMail.getManagers().getImapHostManager().createMailbox(
                greenMail.setUser("me@local.test", "secret"), "Trash");
        when(folderStateMapper.selectOne(any(Wrapper.class), eq(false)))
                .thenReturn(inboxState, inboxState, trashState);

        OaMailAccount account = account("acc-1");
        when(accountMapper.selectById("acc-1")).thenReturn(account);
        when(indexMapper.selectById("msg-3")).thenReturn(row("msg-3", "acc-1", 1L, true));

        service.deleteMessage("msg-3");

        Store store = openStore();
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);
        assertEquals(0, inbox.getMessageCount());
        inbox.close(false);
        Folder trash = store.getFolder("Trash");
        trash.open(Folder.READ_ONLY);
        assertEquals(1, trash.getMessageCount(), "有 Trash 映射时应移入而非永久删除");
        trash.close(false);
        store.close();
        verify(indexMapper).deleteById("msg-3");
    }

    @Test
    void sendMessage_validatesRequiredFields() {
        SendMailMessageReqDTO req = new SendMailMessageReqDTO();
        req.setAccountId("acc-1");
        req.setHtml("<p>hi</p>");
        // 缺 to
        assertThrows(JeecgBootException.class, () -> service.sendMessage(req));

        req.setTo("a@b.c");
        req.setHtml(null);
        assertThrows(JeecgBootException.class, () -> service.sendMessage(req));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendMessage_deliversViaBoundAccountSmtp() throws Exception {
        greenMail.setUser("me@local.test", "secret");
        // Sent 文件夹未映射 → 归档走告警旁路，发送本体不受影响
        when(folderStateMapper.selectOne(any(Wrapper.class), anyBoolean())).thenReturn(null);
        OaMailAccount account = account("acc-1");
        when(accountMapper.selectById("acc-1")).thenReturn(account);

        SendMailMessageReqDTO req = new SendMailMessageReqDTO();
        req.setAccountId("acc-1");
        req.setMode("new");
        req.setTo("boss@local.test");
        req.setSubject("写信验证");
        req.setHtml("<p>这封信来自 B-6。</p>");

        String result = service.sendMessage(req);
        assertEquals("发送成功", result);
        assertTrue(greenMail.waitForIncomingEmail(5000, 1), "邮件未投递到 GreenMail SMTP");
        MimeMessage[] received = greenMail.getReceivedMessages();
        assertEquals("写信验证", received[0].getSubject());
        assertEquals("me@local.test", ((jakarta.mail.internet.InternetAddress) received[0].getFrom()[0]).getAddress());
        String content = (String) received[0].getContent();
        assertTrue(content.contains("B-6"));
    }

    // ---------- helpers ----------

    private void deliver(String subject, String body) throws Exception {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(ServerSetupTest.SMTP.getPort());
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setTo("me@local.test");
        helper.setFrom("boss@local.test");
        helper.setSubject(subject);
        helper.setText(body, false);
        sender.send(message);
        assertTrue(greenMail.waitForIncomingEmail(5000, 1), "投递超时: " + subject);
    }

    private Store openStore() throws Exception {
        org.jeecg.modules.mail.dto.MailServerConfig cfg = new org.jeecg.modules.mail.dto.MailServerConfig();
        cfg.setHost("127.0.0.1");
        cfg.setPort(ServerSetupTest.IMAP.getPort());
        cfg.setSsl(false);
        cfg.setUsername("me@local.test");
        cfg.setPassword("secret");
        return connector.openImapStore(cfg);
    }

    private OaMailAccount account(String id) {
        OaMailAccount account = new OaMailAccount();
        account.setId(id);
        account.setEmail("me@local.test");
        account.setImapHost("127.0.0.1");
        account.setImapPort(ServerSetupTest.IMAP.getPort());
        account.setImapSsl(false);
        account.setImapUsername("me@local.test");
        account.setImapPassword(codec.encode("secret"));
        account.setSmtpHost("127.0.0.1");
        account.setSmtpPort(ServerSetupTest.SMTP.getPort());
        account.setSmtpSsl(false);
        account.setSmtpUsername("me@local.test");
        account.setSmtpPassword(codec.encode("secret"));
        return account;
    }

    private OaMailFolderState state(String accountId, String remote, String canonical) {
        OaMailFolderState s = new OaMailFolderState();
        s.setAccountId(accountId);
        s.setRemoteName(remote);
        s.setCanonical(canonical);
        return s;
    }

    private OaMailMessageIndex row(String id, String accountId, long uid, boolean read) {
        OaMailMessageIndex r = new OaMailMessageIndex();
        r.setId(id);
        r.setAccountId(accountId);
        r.setCanonicalFolder("Inbox");
        r.setImapUid(uid);
        r.setSubject("s");
        r.setReadFlag(read);
        return r;
    }
}
