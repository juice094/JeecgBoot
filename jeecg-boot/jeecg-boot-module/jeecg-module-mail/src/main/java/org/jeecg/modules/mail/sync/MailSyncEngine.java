package org.jeecg.modules.mail.sync;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.FolderNotFoundException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMultipart;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.mail.delivery.ImapSmtpConnector;
import org.jeecg.modules.mail.dto.MailServerConfig;
import org.jeecg.modules.mail.entity.OaMailAccount;
import org.jeecg.modules.mail.entity.OaMailFolderState;
import org.jeecg.modules.mail.entity.OaMailMessageIndex;
import org.jeecg.modules.mail.enums.MailCanonicalFolder;
import org.jeecg.modules.mail.mapper.OaMailAccountMapper;
import org.jeecg.modules.mail.mapper.OaMailFolderStateMapper;
import org.jeecg.modules.mail.mapper.OaMailMessageIndexMapper;
import org.jeecg.modules.mail.support.MailDigests;
import org.jeecg.modules.mail.support.MailPasswordCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.mail.UIDFolder;

/**
 * @Description: 员工邮箱增量同步引擎（B-2 上半）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 口径（需求文档 B-3 / 接口文档 §4.2）：
 * - 基于 last_uid 游标增量同步，只写信封索引，不存正文；
 * - 远端文件夹经 MailFolderMapping 归一；未识别进入待映射列表，同步不静默跳过；
 * - 唯一键 (account_id, canonical_folder, imap_uid) 保证重复同步不产生重复记录；
 * - 同步 error 不回退 last_uid，下次续跑。
 *
 * 事务边界：同步含长 I/O，刻意不加 @Transactional；索引逐条 upsert，失败重跑由游标幂等兜底。
 * snippet 为 pilot 占位（取主题，不读正文）；正文按需 FETCH 属 B-3。
 */
@Component
public class MailSyncEngine {

    private static final Logger log = LoggerFactory.getLogger(MailSyncEngine.class);

    /** 收件人摘要最多展示的地址数 */
    private static final int TO_SUMMARY_MAX = 3;

    /** 收件人摘要长度上限 */
    private static final int TO_SUMMARY_MAX_LENGTH = 200;

    @Autowired
    private OaMailAccountMapper accountMapper;

    @Autowired
    private OaMailFolderStateMapper folderStateMapper;

    @Autowired
    private OaMailMessageIndexMapper indexMapper;

    @Autowired
    private ImapSmtpConnector connector;

    @Autowired
    private MailPasswordCodec passwordCodec;

    /**
     * 同步一个绑定账号（增量，游标续跑）。
     */
    public void syncAccount(String accountId) {
        OaMailAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new JeecgBootException("邮箱绑定不存在: " + accountId);
        }
        Store store = null;
        try {
            store = connector.openImapStore(toImapConfig(account));
            doSync(account, store);
            account.setLastSyncTime(new Date());
            accountMapper.updateById(account);
        } catch (MessagingException e) {
            throw new JeecgBootException("邮箱同步失败 accountId=" + accountId + ": " + e.getMessage(), e);
        } finally {
            if (store != null) {
                try {
                    store.close();
                } catch (MessagingException e) {
                    log.warn("[mail] 同步关闭 IMAP 连接失败 accountId={}", accountId);
                }
            }
        }
    }

    /**
     * 对已打开的 Store 执行同步（包级可见，供离线单测以 mock Store 直测）。
     */
    void doSync(OaMailAccount account, Store store) throws MessagingException {
        Map<String, String> customMappings = loadCustomMappings(account.getId());
        Folder root = store.getDefaultFolder();
        for (Folder folder : root.list("*")) {
            String remoteName = folder.getName();
            String canonicalCode = MailFolderMapping.resolve(remoteName, customMappings);
            OaMailFolderState state = upsertFolderState(account.getId(), remoteName, canonicalCode);
            if (canonicalCode == null) {
                // 不静默跳过：落状态（canonical 为空）并告警，由映射接口人工维护
                log.warn("[mail] 文件夹未识别，进入待映射 accountId={} remoteName={}", account.getId(), remoteName);
                continue;
            }
            if (!(folder instanceof UIDFolder)) {
                log.warn("[mail] 文件夹不支持 UID 语义，跳过 accountId={} remoteName={}", account.getId(), remoteName);
                continue;
            }
            try {
                syncFolder(account.getId(), MailCanonicalFolder.fromCode(canonicalCode), folder, state);
            } catch (MessagingException e) {
                // 单文件夹失败不拖垮整轮同步（如 QQ「其他文件夹」命名空间父目录无法 SELECT）；
                // 错误不静默：记 warn 且该文件夹游标不回退，修复后下轮续跑
                log.warn("[mail] 文件夹同步失败（跳过本轮） accountId={} remoteName={} error={}",
                        account.getId(), remoteName, e.getMessage());
            }
        }
    }

    private void syncFolder(String accountId, MailCanonicalFolder canonical, Folder folder,
            OaMailFolderState state) throws MessagingException {
        UIDFolder uidFolder = (UIDFolder) folder;
        folder.open(Folder.READ_ONLY);
        try {
            long lastUid = state.getLastUid() == null ? 0L : state.getLastUid();
            Message[] messages = uidFolder.getMessagesByUID(lastUid + 1, UIDFolder.LASTUID);
            if (messages == null || messages.length == 0) {
                return;
            }
            jakarta.mail.FetchProfile profile = new jakarta.mail.FetchProfile();
            profile.add(jakarta.mail.FetchProfile.Item.ENVELOPE);
            profile.add(jakarta.mail.FetchProfile.Item.FLAGS);
            folder.fetch(messages, profile);
            long maxUid = lastUid;
            for (Message message : messages) {
                long uid = uidFolder.getUID(message);
                if (uid < 0) {
                    continue;
                }
                upsertIndex(buildIndexRow(accountId, canonical, message, uid));
                maxUid = Math.max(maxUid, uid);
            }
            state.setLastUid(maxUid);
            folderStateMapper.updateById(state);
            log.info("[mail] 同步完成 accountId={} folder={} 新增/刷新 {} 封", accountId,
                    canonical.getCode(), messages.length);
        } catch (FolderNotFoundException e) {
            log.warn("[mail] 远端文件夹不存在，跳过 accountId={} remote={}", accountId, folder.getName());
        } finally {
            folder.close(false);
        }
    }

    /**
     * 单封邮件 → 索引行（包级可见，供离线单测直测信封提取）。
     */
    static OaMailMessageIndex buildIndexRow(String accountId, MailCanonicalFolder canonical, Message message,
            long uid) throws MessagingException {
        OaMailMessageIndex row = new OaMailMessageIndex();
        row.setAccountId(accountId);
        row.setCanonicalFolder(canonical.getCode());
        row.setImapUid(uid);
        row.setSubject(message.getSubject());
        row.setMessageId(firstHeader(message, "Message-ID"));
        jakarta.mail.Address[] from = message.getFrom();
        if (from != null && from.length > 0 && from[0] instanceof InternetAddress) {
            InternetAddress address = (InternetAddress) from[0];
            row.setFromName(address.getPersonal());
            row.setFromAddressHash(MailDigests.toHash8(address.getAddress()));
        }
        row.setToSummary(toSummary(message));
        Date received = message.getReceivedDate() != null ? message.getReceivedDate() : message.getSentDate();
        row.setReceivedTime(received);
        row.setReadFlag(message.isSet(Flags.Flag.SEEN));
        row.setHasAttachment(hasAttachment(message));
        // pilot 占位：不读正文，snippet 取主题；正文按需 FETCH 属 B-3
        row.setSnippet(row.getSubject());
        return row;
    }

    private void upsertIndex(OaMailMessageIndex row) {
        OaMailMessageIndex existing = indexMapper.selectOne(new LambdaQueryWrapper<OaMailMessageIndex>()
                .eq(OaMailMessageIndex::getAccountId, row.getAccountId())
                .eq(OaMailMessageIndex::getCanonicalFolder, row.getCanonicalFolder())
                .eq(OaMailMessageIndex::getImapUid, row.getImapUid())
                .last("limit 1"), false);
        if (existing == null) {
            indexMapper.insert(row);
        } else {
            // 已索引：只刷新已读标记（主题等快照字段首刷为准）
            existing.setReadFlag(row.getReadFlag());
            indexMapper.updateById(existing);
        }
    }

    private OaMailFolderState upsertFolderState(String accountId, String remoteName, String canonicalCode) {
        OaMailFolderState state = folderStateMapper.selectOne(new LambdaQueryWrapper<OaMailFolderState>()
                .eq(OaMailFolderState::getAccountId, accountId)
                .eq(OaMailFolderState::getRemoteName, remoteName)
                .last("limit 1"), false);
        if (state == null) {
            state = new OaMailFolderState();
            state.setAccountId(accountId);
            state.setRemoteName(remoteName);
            state.setCanonical(canonicalCode);
            state.setCreateTime(new Date());
            folderStateMapper.insert(state);
        } else if (!StringUtils.equals(state.getCanonical(), canonicalCode)) {
            // 映射可能被人工维护或内置表升级，同步最新归一结果；不回退 last_uid
            state.setCanonical(canonicalCode);
            folderStateMapper.updateById(state);
        }
        return state;
    }

    private Map<String, String> loadCustomMappings(String accountId) {
        Map<String, String> mappings = new HashMap<>();
        for (OaMailFolderState state : folderStateMapper.selectList(
                new LambdaQueryWrapper<OaMailFolderState>()
                        .eq(OaMailFolderState::getAccountId, accountId)
                        .isNotNull(OaMailFolderState::getCanonical))) {
            mappings.put(state.getRemoteName().trim().toLowerCase(Locale.ROOT), state.getCanonical());
        }
        return mappings;
    }

    private MailServerConfig toImapConfig(OaMailAccount account) {
        MailServerConfig cfg = new MailServerConfig();
        cfg.setHost(account.getImapHost());
        cfg.setPort(account.getImapPort());
        cfg.setSsl(Boolean.TRUE.equals(account.getImapSsl()));
        cfg.setUsername(account.getImapUsername());
        cfg.setPassword(passwordCodec.decode(account.getImapPassword()));
        return cfg;
    }

    private static String firstHeader(Message message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        return values == null || values.length == 0 ? null : values[0];
    }

    private static String toSummary(Message message) throws MessagingException {
        jakarta.mail.Address[] recipients = message.getAllRecipients();
        if (recipients == null || recipients.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (jakarta.mail.Address recipient : recipients) {
            if (shown >= TO_SUMMARY_MAX) {
                sb.append("…");
                break;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(recipient.toString());
            shown++;
        }
        return sb.length() > TO_SUMMARY_MAX_LENGTH
                ? sb.substring(0, TO_SUMMARY_MAX_LENGTH)
                : sb.toString();
    }

    /**
     * 信封级附件探测：multipart 内存在 ATTACHMENT 处置的 part 即视为有附件。
     */
    private static boolean hasAttachment(Message message) throws MessagingException {
        try {
            if (!message.isMimeType("multipart/*")) {
                return false;
            }
            Object content = message.getContent();
            if (!(content instanceof MimeMultipart)) {
                return false;
            }
            MimeMultipart multipart = (MimeMultipart) content;
            for (int i = 0; i < multipart.getCount(); i++) {
                Part part = multipart.getBodyPart(i);
                if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new MessagingException("附件探测失败", e);
        }
    }
}
