package org.jeecg.modules.mail.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;

import org.jeecg.modules.mail.delivery.ImapSmtpConnector;
import org.jeecg.modules.mail.entity.OaMailAccount;
import org.jeecg.modules.mail.entity.OaMailFolderState;
import org.jeecg.modules.mail.entity.OaMailMessageIndex;
import org.jeecg.modules.mail.enums.MailCanonicalFolder;
import org.jeecg.modules.mail.mapper.OaMailAccountMapper;
import org.jeecg.modules.mail.mapper.OaMailFolderStateMapper;
import org.jeecg.modules.mail.mapper.OaMailMessageIndexMapper;
import org.jeecg.modules.mail.support.AesMailPasswordCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.baomidou.mybatisplus.core.conditions.Wrapper;

/**
 * @Description: MailSyncEngine 离线单测（mock Store/IMAPFolder/Message，零真实 I/O）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 锁定口径（需求文档 B-3）：last_uid 游标增量、只写信封索引、唯一键幂等、未识别不静默跳过。
 */
class MailSyncEngineTest {

    private static final String TEST_KEY = java.util.Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private OaMailAccountMapper accountMapper;
    private OaMailFolderStateMapper folderStateMapper;
    private OaMailMessageIndexMapper indexMapper;
    private MailSyncEngine engine;

    @BeforeEach
    void setUp() {
        accountMapper = mock(OaMailAccountMapper.class);
        folderStateMapper = mock(OaMailFolderStateMapper.class);
        indexMapper = mock(OaMailMessageIndexMapper.class);
        engine = new MailSyncEngine();
        ReflectionTestUtils.setField(engine, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(engine, "folderStateMapper", folderStateMapper);
        ReflectionTestUtils.setField(engine, "indexMapper", indexMapper);
        ReflectionTestUtils.setField(engine, "connector", mock(ImapSmtpConnector.class));
        ReflectionTestUtils.setField(engine, "passwordCodec", new AesMailPasswordCodec(TEST_KEY));
    }

    private Message envelopeMessage(String subject, boolean seen, Date received) throws Exception {
        Message message = mock(Message.class);
        when(message.getSubject()).thenReturn(subject);
        when(message.getHeader("Message-ID")).thenReturn(new String[] { "<" + subject + "@x>" });
        when(message.getFrom()).thenReturn(new jakarta.mail.Address[] {
                new InternetAddress("zhangsan@example.com", "张三") });
        when(message.getAllRecipients()).thenReturn(new jakarta.mail.Address[] {
                new InternetAddress("me@example.com") });
        when(message.getReceivedDate()).thenReturn(received);
        when(message.isSet(Flags.Flag.SEEN)).thenReturn(seen);
        when(message.isMimeType("multipart/*")).thenReturn(false);
        return message;
    }

    @Test
    @SuppressWarnings("unchecked")
    void doSync_newMessages_indexedAndCursorAdvanced() throws Exception {
        StoreFixture fx = new StoreFixture();
        fx.addImapFolder("INBOX", 0L,
                new long[] { 101L, 102L },
                new Message[] {
                        envelopeMessage("周报确认", true, new Date(1000L)),
                        envelopeMessage("Q4 计划", false, new Date(2000L)) });

        OaMailAccount account = new OaMailAccount();
        account.setId("acc-1");
        engine.doSync(account, fx.store);

        // 两条索引落库，游标推进到 102
        ArgumentCaptor<OaMailMessageIndex> indexCaptor = ArgumentCaptor.forClass(OaMailMessageIndex.class);
        verify(indexMapper, org.mockito.Mockito.times(2)).insert(indexCaptor.capture());
        List<OaMailMessageIndex> rows = indexCaptor.getAllValues();
        assertEquals("Inbox", rows.get(0).getCanonicalFolder());
        assertEquals(Long.valueOf(101L), rows.get(0).getImapUid());
        assertEquals("周报确认", rows.get(0).getSubject());
        assertEquals("张三", rows.get(0).getFromName());
        assertEquals(8, rows.get(0).getFromAddressHash().length());
        assertTrue(rows.get(0).getReadFlag());
        assertFalse(rows.get(1).getReadFlag());

        ArgumentCaptor<OaMailFolderState> stateCaptor = ArgumentCaptor.forClass(OaMailFolderState.class);
        verify(folderStateMapper).updateById(stateCaptor.capture());
        assertEquals(Long.valueOf(102L), stateCaptor.getValue().getLastUid());
    }

    @Test
    @SuppressWarnings("unchecked")
    void doSync_cursorResume_noNewMessagesNoInserts() throws Exception {
        // 已有游标 102：从 103 起拉取为空，不产生任何索引写入
        StoreFixture fx = new StoreFixture();
        fx.addImapFolder("INBOX", 102L, new long[0], new Message[0]);

        OaMailAccount account = new OaMailAccount();
        account.setId("acc-1");
        engine.doSync(account, fx.store);

        verify(indexMapper, never()).insert(any(OaMailMessageIndex.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void doSync_unrecognizedFolder_recordedButNotSynced() throws Exception {
        StoreFixture fx = new StoreFixture();
        Folder weird = fx.addFolder("自定义归档");

        OaMailAccount account = new OaMailAccount();
        account.setId("acc-1");
        engine.doSync(account, fx.store);

        // 未识别：状态落库（canonical 空），但不发起任何文件夹级 I/O
        ArgumentCaptor<OaMailFolderState> stateCaptor = ArgumentCaptor.forClass(OaMailFolderState.class);
        verify(folderStateMapper).insert(stateCaptor.capture());
        assertEquals("自定义归档", stateCaptor.getValue().getRemoteName());
        assertNull(stateCaptor.getValue().getCanonical());
        verify(weird, never()).open(anyInt());
        verify(indexMapper, never()).insert(any(OaMailMessageIndex.class));
    }

    @Test
    void buildIndexRow_extractsEnvelope() throws Exception {
        Message message = envelopeMessage("主题X", false, new Date(5000L));
        OaMailMessageIndex row = MailSyncEngine.buildIndexRow("acc-9", MailCanonicalFolder.INBOX, message, 7L);
        assertEquals("acc-9", row.getAccountId());
        assertEquals("Inbox", row.getCanonicalFolder());
        assertEquals(Long.valueOf(7L), row.getImapUid());
        assertEquals("<主题X@x>", row.getMessageId());
        assertEquals("me@example.com", row.getToSummary());
        assertEquals(new Date(5000L), row.getReceivedTime());
        assertFalse(row.getReadFlag());
        assertFalse(row.getHasAttachment());
        assertEquals("主题X", row.getSnippet());
    }

    /**
     * Store/Folder 装配夹具：默认空自定义映射、空状态表，按需挂 IMAP 文件夹。
     */
    @SuppressWarnings("unchecked")
    private class StoreFixture {
        final jakarta.mail.Store store = mock(jakarta.mail.Store.class);
        final Folder root = mock(Folder.class);
        final java.util.List<Folder> folders = new java.util.ArrayList<>();

        StoreFixture() throws Exception {
            when(store.getDefaultFolder()).thenReturn(root);
            when(root.list("*")).thenAnswer(invocation -> folders.toArray(new Folder[0]));
            when(folderStateMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
            when(folderStateMapper.selectOne(any(Wrapper.class), eq(false))).thenReturn(null);
            when(folderStateMapper.insert(any(OaMailFolderState.class))).thenReturn(1);
            when(folderStateMapper.updateById(any(OaMailFolderState.class))).thenReturn(1);
            when(indexMapper.selectOne(any(Wrapper.class), eq(false))).thenReturn(null);
            when(indexMapper.insert(any(OaMailMessageIndex.class))).thenReturn(1);
            when(indexMapper.updateById(any(OaMailMessageIndex.class))).thenReturn(1);
        }

        /** 挂载文件夹；uids/messages 为空数组表示游标后无新邮件 */
        Folder addImapFolder(String name, long cursorUid, long[] uids, Message[] messages)
                throws Exception {
            Folder folder = mock(Folder.class, org.mockito.Mockito.withSettings()
                    .extraInterfaces(UIDFolder.class));
            UIDFolder uidFolder = (UIDFolder) folder;
            when(folder.getName()).thenReturn(name);
            when(uidFolder.getMessagesByUID(anyLong(), anyLong())).thenReturn(messages);
            for (int i = 0; i < uids.length && i < messages.length; i++) {
                when(uidFolder.getUID(messages[i])).thenReturn(uids[i]);
            }
            // 游标后无邮件：任意 message 的 UID 返回 -1
            if (uids.length == 0) {
                when(uidFolder.getUID(any(Message.class))).thenReturn(-1L);
            }
            folders.add(folder);
            // 状态查询：按游标返回
            if (cursorUid > 0) {
                OaMailFolderState state = new OaMailFolderState();
                state.setAccountId("acc-1");
                state.setRemoteName(name);
                state.setCanonical(MailFolderMapping.resolve(name, null));
                state.setLastUid(cursorUid);
                when(folderStateMapper.selectOne(any(Wrapper.class), eq(false)))
                        .thenReturn(state);
            }
            return folder;
        }

        Folder addFolder(String name) throws Exception {
            Folder folder = mock(Folder.class);
            when(folder.getName()).thenReturn(name);
            folders.add(folder);
            return folder;
        }
    }
}
