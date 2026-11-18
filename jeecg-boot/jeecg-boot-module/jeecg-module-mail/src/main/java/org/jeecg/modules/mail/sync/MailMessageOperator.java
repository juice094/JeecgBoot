package org.jeecg.modules.mail.sync;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.MimeMessage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.mail.entity.OaMailFolderState;
import org.jeecg.modules.mail.enums.MailCanonicalFolder;
import org.jeecg.modules.mail.mapper.OaMailFolderStateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * @Description: 邮件消息级 IMAP 操作器（B-3 详情/已读、B-5 删除、B-6 发送归档）
 * @Author: mail-dev（B-3/B-5/B-6 续作）
 * @Date: 2026-11-18
 *
 * 口径：全部按 (remoteFolder, imapUid) 定位；文件夹打开用 READ_WRITE（回写 \Seen / expunge 需要）；
 * remoteName 由 folder_state 的 canonical 映射解析，未映射抛业务异常不静默跳过。
 * 本类只做协议层操作，归属校验与索引维护在 ServiceImpl。
 */
@Component
public class MailMessageOperator {

    private static final Logger log = LoggerFactory.getLogger(MailMessageOperator.class);

    /**
     * 按 canonical 解析 remoteName（folder_state 中该账号第一条映射）。
     *
     * @return remoteName；无映射返回 null
     */
    public String resolveRemoteFolder(OaMailFolderStateMapper folderStateMapper, String accountId,
            MailCanonicalFolder canonical) {
        OaMailFolderState state = folderStateMapper.selectOne(new LambdaQueryWrapper<OaMailFolderState>()
                .eq(OaMailFolderState::getAccountId, accountId)
                .eq(OaMailFolderState::getCanonical, canonical.getCode())
                .last("limit 1"), false);
        return state == null ? null : state.getRemoteName();
    }

    /**
     * 按 UID 抓取单封邮件（含正文内容预取）。调用方负责 close folder。
     *
     * @return 命中的消息；UID 不存在返回 null
     */
    public Message fetchByUid(Store store, String remoteFolder, long uid) throws MessagingException {
        Folder folder = openFolder(store, remoteFolder);
        try {
            UIDFolder uidFolder = (UIDFolder) folder;
            Message[] found = uidFolder.getMessagesByUID(uid, uid);
            if (found == null || found.length == 0 || found[0] == null) {
                return null;
            }
            // 内容按需加载：jakarta.mail 对 getContent 懒抓取，无需显式 FetchProfile
            return found[0];
        } catch (ClassCastException e) {
            folder.close(false);
            throw new MessagingException("文件夹不支持 UID 语义: " + remoteFolder, e);
        } catch (MessagingException | RuntimeException e) {
            folder.close(false);
            throw e;
        }
    }

    /**
     * 置位 \Seen（已读回写）。
     */
    public void setSeen(Store store, String remoteFolder, long uid, boolean seen) throws MessagingException {
        Folder folder = openFolder(store, remoteFolder);
        try {
            Message message = firstByUid(folder, uid);
            if (message != null) {
                message.setFlag(Flags.Flag.SEEN, seen);
            }
        } finally {
            folder.close(false);
        }
    }

    /**
     * 删除：目标 Trash 有映射且源不是 Trash → copy 到 Trash 后源 expunge；
     * 否则源内直接 expunge（永久删除）。
     */
    public void delete(Store store, String accountId, OaMailFolderStateMapper folderStateMapper,
            String sourceRemote, long uid) throws MessagingException {
        MailCanonicalFolder sourceCanonical = canonicalOf(folderStateMapper, accountId, sourceRemote);
        String trashRemote = resolveRemoteFolder(folderStateMapper, accountId, MailCanonicalFolder.TRASH);
        if (trashRemote != null && !trashRemote.equals(sourceRemote)
                && sourceCanonical != MailCanonicalFolder.TRASH) {
            moveToTrash(store, sourceRemote, trashRemote, uid);
            return;
        }
        Folder folder = openFolder(store, sourceRemote);
        try {
            Message message = firstByUid(folder, uid);
            if (message != null) {
                message.setFlag(Flags.Flag.DELETED, true);
            }
            folder.expunge();
        } finally {
            // expunge 后 folder 仍按 open 状态使用，close(false)：\Deleted 已物理清除
            folder.close(false);
        }
    }

    /**
     * 追加一封已发送邮件到指定文件夹（best-effort，调用方已捕获告警）。
     */
    public void appendToFolder(Store store, String remoteFolder, MimeMessage message, boolean seen)
            throws MessagingException {
        Folder folder = openFolder(store, remoteFolder);
        try {
            Flags flags = new Flags();
            if (seen) {
                flags.add(Flags.Flag.SEEN);
            }
            folder.appendMessages(new Message[]{message});
            if (seen) {
                // appendMessages 不保证携带 flags，部分服务端忽略；补一次按最近 UID 置位
                Message[] appended = folder.getMessages();
                if (appended.length > 0) {
                    appended[appended.length - 1].setFlags(flags, true);
                }
            }
        } finally {
            folder.close(false);
        }
    }

    private void moveToTrash(Store store, String sourceRemote, String trashRemote, long uid)
            throws MessagingException {
        Folder source = openFolder(store, sourceRemote);
        Folder trash = null;
        try {
            trash = openFolder(store, trashRemote);
            Message message = firstByUid(source, uid);
            if (message == null) {
                return;
            }
            source.copyMessages(new Message[]{message}, trash);
            message.setFlag(Flags.Flag.DELETED, true);
            source.expunge();
        } catch (MessagingException | RuntimeException e) {
            log.warn("[mail] 移动到已删除文件夹失败，回退为源内永久删除 remote={} uid={}", sourceRemote, uid);
            Message message = firstByUid(source, uid);
            if (message != null) {
                message.setFlag(Flags.Flag.DELETED, true);
            }
            source.expunge();
        } finally {
            try {
                source.close(false);
            } catch (MessagingException ignored) {
                // 连接级清理失败仅告警
            }
            if (trash != null) {
                try {
                    trash.close(false);
                } catch (MessagingException ignored) {
                    // 连接级清理失败仅告警
                }
            }
        }
    }

    private Folder openFolder(Store store, String remoteFolder) throws MessagingException {
        Folder folder = store.getFolder(remoteFolder);
        if (!folder.exists()) {
            throw new JeecgBootException("远端文件夹不存在: " + remoteFolder);
        }
        folder.open(Folder.READ_WRITE);
        return folder;
    }

    private Message firstByUid(Folder folder, long uid) throws MessagingException {
        UIDFolder uidFolder = (UIDFolder) folder;
        Message[] found = uidFolder.getMessagesByUID(uid, uid);
        return found == null || found.length == 0 ? null : found[0];
    }

    private MailCanonicalFolder canonicalOf(OaMailFolderStateMapper folderStateMapper, String accountId,
            String remoteName) {
        OaMailFolderState state = folderStateMapper.selectOne(new LambdaQueryWrapper<OaMailFolderState>()
                .eq(OaMailFolderState::getAccountId, accountId)
                .eq(OaMailFolderState::getRemoteName, remoteName)
                .last("limit 1"), false);
        if (state == null || state.getCanonical() == null) {
            return null;
        }
        return MailCanonicalFolder.fromCode(state.getCanonical());
    }
}
