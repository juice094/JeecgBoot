package org.jeecg.modules.mail.service.impl;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.common.log.EventLoggers;
import org.jeecg.modules.mail.constant.MailRedisConstants;
import org.jeecg.modules.mail.delivery.ImapSmtpConnector;
import org.jeecg.modules.mail.delivery.MailConnectFailedException;
import org.jeecg.modules.mail.dto.ConnectionTestResult;
import org.jeecg.modules.mail.dto.MailAccountBindReqDTO;
import org.jeecg.modules.mail.dto.MailFolderMappingReqDTO;
import org.jeecg.modules.mail.dto.MailServerConfig;
import org.jeecg.modules.mail.dto.MailSyncStatusDTO;
import org.jeecg.modules.mail.dto.OaMailAccountRespDTO;
import org.jeecg.modules.mail.dto.OaMailMessageDetailDTO;
import org.jeecg.modules.mail.dto.SendMailMessageReqDTO;
import org.jeecg.modules.mail.entity.OaMailAccount;
import org.jeecg.modules.mail.entity.OaMailFolderState;
import org.jeecg.modules.mail.entity.OaMailMessageIndex;
import org.jeecg.modules.mail.enums.MailCanonicalFolder;
import org.jeecg.modules.mail.mapper.OaMailAccountMapper;
import org.jeecg.modules.mail.mapper.OaMailFolderStateMapper;
import org.jeecg.modules.mail.mapper.OaMailMessageIndexMapper;
import org.jeecg.modules.mail.service.IOaMailService;
import org.jeecg.modules.mail.support.MailBodyExtractor;
import org.jeecg.modules.mail.support.MailDigests;
import org.jeecg.modules.mail.support.MailPasswordCodec;
import org.jeecg.modules.mail.sync.MailMessageOperator;
import org.jeecg.modules.mail.sync.MailSyncTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: OA 邮件-员工邮箱 ServiceImpl（B-1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 已实现（B-1 全量 + B-2 上半）：绑定双测连落库、修改触发重测、手动双测连、解绑、本人账号列表、
 * 文件夹映射维护、同步触发/状态；密码复用 MailPasswordCodec 密文落库（D-76）；
 * 归属校验走 OaMailOwnershipGuard（F-09）；同步引擎在 sync 包（MailSyncEngine）。
 * 未实现（B-2 下半/B-3+，不假装可用）：定时调度接入、列表/详情 FETCH 页面联动、发送（B-5）。
 */
@Service
public class OaMailServiceImpl extends ServiceImpl<OaMailAccountMapper, OaMailAccount> implements IOaMailService {

    /** 测连状态：通过 */
    private static final int TEST_STATUS_OK = 1;

    /** 事件名（规划稿 §1 埋点清单） */
    private static final String EVENT_MAIL_BIND = "mail.bind";
    private static final String EVENT_MAIL_UNBIND = "mail.unbind";
    private static final String EVENT_MAIL_SENT = "mail.sent";
    private static final String EVENT_MAIL_DELETED = "mail.deleted";

    /** 事件结果/拒绝原因口径 */
    private static final String RESULT_OK = "ok";
    private static final String RESULT_FAILED = "failed";
    private static final String REASON_DUPLICATE = "duplicate";
    private static final String REASON_CONNECT_TEST_FAILED = "connect_test_failed";

    private static final Logger log = LoggerFactory.getLogger(OaMailServiceImpl.class);

    @Autowired
    private OaMailMessageIndexMapper indexMapper;

    @Autowired
    private OaMailFolderStateMapper folderStateMapper;

    @Autowired
    private MailSyncTracker syncTracker;

    @Autowired
    private ImapSmtpConnector connector;

    @Autowired
    private MailPasswordCodec passwordCodec;

    @Autowired
    private OaMailOwnershipGuard ownershipGuard;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MailMessageOperator operator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String bind(MailAccountBindReqDTO req) {
        validateBindRequest(req, true);
        String owner = ownershipGuard.currentUsernameOrNull();
        Long duplicates = count(new LambdaQueryWrapper<OaMailAccount>()
                .eq(OaMailAccount::getEmail, req.getEmail().trim())
                .eq(OaMailAccount::getCreateBy, owner));
        if (duplicates > 0) {
            EventLoggers.info(EventLoggers.MAIL_EVENT, EVENT_MAIL_BIND,
                    "emailHash", MailDigests.toHash8(req.getEmail().trim()),
                    "result", RESULT_FAILED, "reason", REASON_DUPLICATE);
            throw new JeecgBootException("该邮箱已绑定: " + req.getEmail());
        }

        // 双测连通过才落库（B-1）；失败抛 MailConnectFailedException，由 REST 层组装结构化响应
        ConnectionTestResult testResult = connector.testBoth(req.getImap(), req.getSmtp());
        if (!testResult.allOk()) {
            EventLoggers.info(EventLoggers.MAIL_EVENT, EVENT_MAIL_BIND,
                    "emailHash", MailDigests.toHash8(req.getEmail().trim()),
                    "result", RESULT_FAILED, "reason", REASON_CONNECT_TEST_FAILED);
            throw new MailConnectFailedException(testResult);
        }

        OaMailAccount account = new OaMailAccount();
        account.setEmail(req.getEmail().trim());
        applyImapConfig(account, req.getImap(), true);
        applySmtpConfig(account, req.getSmtp(), true);
        account.setTestStatus(TEST_STATUS_OK);
        account.setCreateBy(owner);
        account.setCreateTime(new Date());
        save(account);
        EventLoggers.info(EventLoggers.MAIL_EVENT, EVENT_MAIL_BIND,
                "accountId", account.getId(),
                "emailHash", MailDigests.toHash8(account.getEmail()),
                "result", RESULT_OK);
        return account.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAccount(String id, MailAccountBindReqDTO req) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("绑定 id 不能为空");
        }
        validateBindRequest(req, false);
        OaMailAccount existing = getAndCheckOwnership(id);

        // 合并配置后重测：密码留空的一侧沿用库中密文解密后的明文
        MailServerConfig imapConfig = mergedImap(existing, req.getImap());
        MailServerConfig smtpConfig = mergedSmtp(existing, req.getSmtp());
        ConnectionTestResult testResult = connector.testBoth(imapConfig, smtpConfig);
        if (!testResult.allOk()) {
            throw new MailConnectFailedException(testResult);
        }

        existing.setEmail(req.getEmail().trim());
        applyImapConfig(existing, req.getImap(), false);
        applySmtpConfig(existing, req.getSmtp(), false);
        existing.setTestStatus(TEST_STATUS_OK);
        existing.setUpdateBy(ownershipGuard.currentUsernameOrNull());
        existing.setUpdateTime(new Date());
        updateById(existing);
    }

    @Override
    public ConnectionTestResult testAccount(String id) {
        OaMailAccount existing = getAndCheckOwnership(id);
        return connector.testBoth(toImapConfig(existing), toSmtpConfig(existing));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(String accountId) {
        getAndCheckOwnership(accountId);
        removeById(accountId);
        EventLoggers.info(EventLoggers.MAIL_EVENT, EVENT_MAIL_UNBIND,
                "accountId", accountId, "result", RESULT_OK);
    }

    @Override
    public List<OaMailAccountRespDTO> listAccounts() {
        LambdaQueryWrapper<OaMailAccount> wrapper = new LambdaQueryWrapper<>();
        if (!ownershipGuard.isAdmin()) {
            String owner = ownershipGuard.currentUsernameOrNull();
            wrapper.eq(OaMailAccount::getCreateBy, owner);
        }
        wrapper.orderByDesc(OaMailAccount::getCreateTime);
        return list(wrapper).stream().map(OaMailAccountRespDTO::from).collect(Collectors.toList());
    }

    @Override
    public List<OaMailFolderState> listFolderStates(String accountId) {
        getAndCheckOwnership(accountId);
        return folderStateMapper.selectList(new LambdaQueryWrapper<OaMailFolderState>()
                .eq(OaMailFolderState::getAccountId, accountId)
                .orderByAsc(OaMailFolderState::getRemoteName));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveFolderMapping(String accountId, MailFolderMappingReqDTO req) {
        getAndCheckOwnership(accountId);
        if (req == null || StringUtils.isBlank(req.getRemoteName())) {
            throw new JeecgBootException("远端文件夹名不能为空");
        }
        MailCanonicalFolder canonical = MailCanonicalFolder.fromCode(req.getCanonical());
        if (canonical == null) {
            throw new JeecgBootException("非法规范文件夹: " + req.getCanonical()
                    + "（可选: Inbox/Sent/Drafts/Trash/Spam/Archive）");
        }
        String remoteName = req.getRemoteName().trim();
        OaMailFolderState state = folderStateMapper.selectOne(new LambdaQueryWrapper<OaMailFolderState>()
                .eq(OaMailFolderState::getAccountId, accountId)
                .eq(OaMailFolderState::getRemoteName, remoteName)
                .last("limit 1"), false);
        if (state == null) {
            state = new OaMailFolderState();
            state.setAccountId(accountId);
            state.setRemoteName(remoteName);
            state.setCanonical(canonical.getCode());
            state.setCreateTime(new Date());
            folderStateMapper.insert(state);
        } else {
            state.setCanonical(canonical.getCode());
            folderStateMapper.updateById(state);
        }
    }

    @Override
    public String submitSync(String accountId) {
        getAndCheckOwnership(accountId);
        return syncTracker.submit(accountId);
    }

    @Override
    public MailSyncStatusDTO syncStatus(String accountId) {
        OaMailAccount account = getAndCheckOwnership(accountId);
        MailSyncStatusDTO status = new MailSyncStatusDTO();
        status.setAccountId(accountId);
        status.setRunning(syncTracker.isRunning(accountId));
        status.setLastSyncTime(account.getLastSyncTime());
        status.setFolders(listFolderStates(accountId));
        return status;
    }

    @Override
    public ConnectionTestResult testConnection(MailServerConfig cfg) {
        // 契约层单配置测连：按 SMTP 侧语义（接口文档 §2 未指明侧别，立项版重对点）
        return connector.testSmtp(cfg);
    }

    @Override
    public ConnectionTestResult testConnection(MailServerConfig imap, MailServerConfig smtp) {
        return connector.testBoth(imap, smtp);
    }

    @Override
    public IPage<OaMailMessageIndex> pageMessages(String accountId, String canonicalFolder, String keyword,
            Boolean unreadOnly, Integer pageNo, Integer pageSize) {
        if (StringUtils.isBlank(accountId)) {
            throw new JeecgBootException("accountId 不能为空");
        }
        getAndCheckOwnership(accountId);
        LambdaQueryWrapper<OaMailMessageIndex> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OaMailMessageIndex::getAccountId, accountId);
        if (StringUtils.isNotBlank(canonicalFolder)) {
            MailCanonicalFolder folder = MailCanonicalFolder.fromCode(canonicalFolder);
            if (folder == null) {
                throw new JeecgBootException("非法文件夹: " + canonicalFolder);
            }
            wrapper.eq(OaMailMessageIndex::getCanonicalFolder, folder.getCode());
        }
        // 关键字：主题 LIKE；试点量级不建全文索引，走 idx_mail_index_list 后过滤
        if (StringUtils.isNotBlank(keyword)) {
            String trimmed = keyword.trim();
            if (trimmed.length() > 100) {
                throw new JeecgBootException("关键字过长（上限 100 字符）");
            }
            wrapper.like(OaMailMessageIndex::getSubject, trimmed);
        }
        // 未读筛选：仅当显式传 true 时生效，false/null 均不筛选
        if (Boolean.TRUE.equals(unreadOnly)) {
            wrapper.eq(OaMailMessageIndex::getReadFlag, false);
        }
        wrapper.orderByDesc(OaMailMessageIndex::getReceivedTime);
        return indexMapper.selectPage(new Page<>(pageNo == null ? 1 : pageNo, pageSize == null ? 10 : pageSize),
                wrapper);
    }

    @Override
    public OaMailMessageDetailDTO getMessageDetail(String id) {
        OaMailMessageIndex row = requireIndexRow(id);
        OaMailAccount account = getAndCheckOwnership(row.getAccountId());
        Store store = null;
        long start = System.currentTimeMillis();
        try {
            store = connector.openImapStore(toImapConfig(account));
            String remote = operator.resolveRemoteFolder(folderStateMapper, account.getId(),
                    MailCanonicalFolder.fromCode(row.getCanonicalFolder()));
            requireMappedFolder(remote, row.getCanonicalFolder());
            jakarta.mail.Message message = operator.fetchByUid(store, remote, row.getImapUid());
            if (message == null) {
                throw new JeecgBootException("远端邮件不存在（可能已删除或 UID 失效），请同步后重试");
            }
            MailBodyExtractor.ExtractedBody body = MailBodyExtractor.extract(message);
            // 打开详情即已读（Outlook 口径）：\Seen 回写 + 索引列同步
            if (!Boolean.TRUE.equals(row.getReadFlag())) {
                message.setFlag(jakarta.mail.Flags.Flag.SEEN, true);
                row.setReadFlag(true);
                indexMapper.updateById(row);
            }
            return buildDetailDTO(row, body, System.currentTimeMillis() - start);
        } catch (MailConnectFailedException e) {
            throw e;
        } catch (jakarta.mail.MessagingException | java.io.IOException e) {
            throw new JeecgBootException("读取邮件正文失败: " + e.getMessage(), e);
        } finally {
            closeStoreQuietly(store, account.getId());
        }
    }

    @Override
    public void markMessageRead(String id, boolean read) {
        OaMailMessageIndex row = requireIndexRow(id);
        OaMailAccount account = getAndCheckOwnership(row.getAccountId());
        if (Boolean.valueOf(read).equals(row.getReadFlag())) {
            return;
        }
        Store store = null;
        try {
            store = connector.openImapStore(toImapConfig(account));
            String remote = operator.resolveRemoteFolder(folderStateMapper, account.getId(),
                    MailCanonicalFolder.fromCode(row.getCanonicalFolder()));
            requireMappedFolder(remote, row.getCanonicalFolder());
            operator.setSeen(store, remote, row.getImapUid(), read);
            row.setReadFlag(read);
            indexMapper.updateById(row);
        } catch (jakarta.mail.MessagingException e) {
            throw new JeecgBootException("已读状态回写失败: " + e.getMessage(), e);
        } finally {
            closeStoreQuietly(store, account.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessage(String id) {
        OaMailMessageIndex row = requireIndexRow(id);
        OaMailAccount account = getAndCheckOwnership(row.getAccountId());
        Store store = null;
        String deleteMode = null;
        try {
            store = connector.openImapStore(toImapConfig(account));
            String remote = operator.resolveRemoteFolder(folderStateMapper, account.getId(),
                    MailCanonicalFolder.fromCode(row.getCanonicalFolder()));
            requireMappedFolder(remote, row.getCanonicalFolder());
            deleteMode = operator.delete(store, account.getId(), folderStateMapper, remote, row.getImapUid());
        } catch (jakarta.mail.MessagingException e) {
            throw new JeecgBootException("删除邮件失败: " + e.getMessage(), e);
        } finally {
            closeStoreQuietly(store, account.getId());
        }
        indexMapper.deleteById(row.getId());
        EventLoggers.info(EventLoggers.MAIL_EVENT, EVENT_MAIL_DELETED,
                "accountId", account.getId(),
                "mode", deleteMode,
                "folder", row.getCanonicalFolder(),
                "result", RESULT_OK);
    }

    @Override
    public String sendMessage(SendMailMessageReqDTO req) {
        if (req == null || StringUtils.isBlank(req.getAccountId())) {
            throw new JeecgBootException("accountId 不能为空");
        }
        if (StringUtils.isBlank(req.getTo())) {
            throw new JeecgBootException("收件人不能为空");
        }
        if (StringUtils.isBlank(req.getHtml())) {
            throw new JeecgBootException("正文不能为空");
        }
        OaMailAccount account = getAndCheckOwnership(req.getAccountId());

        // 幂等占位（D-20261119-87 ①）：幂等键为空跳过检查直接发送（不强制）；Redis 挂 fail-fast 拒绝发送
        acquireSendIdempotencySlot(req, account.getId());

        long sendStart = System.currentTimeMillis();
        jakarta.mail.Session session = connector.buildSmtpSession(toSmtpConfig(account));
        MimeMessage message;
        String subject = StringUtils.defaultIfBlank(req.getSubject(), "(无主题)");
        try {
            message = new MimeMessage(session);
            message.setFrom(new InternetAddress(account.getEmail()));
            message.setRecipients(jakarta.mail.Message.RecipientType.TO, parseAddresses(req.getTo()));
            if (req.getCc() != null && !req.getCc().isEmpty()) {
                message.setRecipients(jakarta.mail.Message.RecipientType.CC,
                        parseAddresses(String.join(",", req.getCc())));
            }
            if (req.getBcc() != null && !req.getBcc().isEmpty()) {
                message.setRecipients(jakarta.mail.Message.RecipientType.BCC,
                        parseAddresses(String.join(",", req.getBcc())));
            }
            message.setSubject(subject, "UTF-8");
            message.setContent(req.getHtml(), "text/html; charset=UTF-8");
            message.saveChanges();
            connector.send(toSmtpConfig(account), message);
        } catch (jakarta.mail.MessagingException e) {
            EventLoggers.info(EventLoggers.MAIL_EVENT, EVENT_MAIL_SENT,
                    "accountId", account.getId(),
                    "toHash", MailDigests.toHash8(req.getTo()),
                    "result", RESULT_FAILED, "reason", e.getMessage());
            throw new JeecgBootException("发送失败: " + e.getMessage(), e);
        }

        // 发后归档已发送（best-effort，失败仅告警不阻断发送结果）
        boolean archivedToSent = archiveToSentQuietly(account, message);
        EventLoggers.info(EventLoggers.MAIL_EVENT, EVENT_MAIL_SENT,
                "accountId", account.getId(),
                "toHash", MailDigests.toHash8(req.getTo()),
                "subjectHash", MailDigests.toHash8(subject),
                "smtpLatencyMs", System.currentTimeMillis() - sendStart,
                "archivedToSent", archivedToSent,
                "result", RESULT_OK);
        return "发送成功";
    }

    // ---------- 内部 ----------

    /**
     * 写信幂等占位（D-20261119-87 ①）：Redis incr + TTL 24h。
     *
     * key 口径：mail:idem:send:{accountId}:{幂等键 SHA-256 指纹}——幂等键为前端按
     * 「收件人+主题+正文」生成的内容摘要（契约字段），服务端只哈希不落原文；
     * incr 首次返回 1 并设 TTL，同 key 24h 内重复提交返回 &gt;1 即拒绝发送，保证只发一次。
     * 幂等键为空：跳过检查直接发送（决策默认值，不强制）。
     * Redis 不可用：fail-fast 抛业务异常拒绝发送，绝不降级为"不幂等继续发"。
     */
    private void acquireSendIdempotencySlot(SendMailMessageReqDTO req, String accountId) {
        if (StringUtils.isBlank(req.getIdempotencyKey())) {
            return;
        }
        String key = MailRedisConstants.SEND_IDEMPOTENT_KEY_PREFIX + accountId + ":"
                + MailDigests.toHash8(req.getIdempotencyKey());
        Long seq;
        try {
            seq = stringRedisTemplate.opsForValue().increment(key);
            if (seq != null && seq.longValue() == 1L) {
                stringRedisTemplate.expire(key, Duration.ofSeconds(MailRedisConstants.SEND_IDEMPOTENT_TTL_SECONDS));
            }
        } catch (RedisConnectionFailureException e) {
            // Redis 连接异常单独捕获转业务异常：fail-fast，避免同一邮件被重复投递
            throw new JeecgBootException("邮件发送失败：幂等检查不可用（Redis 连接失败），请稍后重试", e);
        }
        if (seq != null && seq.longValue() > 1L) {
            log.info("[mail] 写信幂等命中，拒绝重复提交 accountId={} key={}", accountId, key);
            throw new JeecgBootException("重复提交：相同邮件已在 24 小时内提交过，请勿重发");
        }
    }

    private OaMailMessageIndex requireIndexRow(String id) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("邮件 id 不能为空");
        }
        OaMailMessageIndex row = indexMapper.selectById(id);
        if (row == null) {
            throw new JeecgBootException("邮件不存在: " + id);
        }
        return row;
    }

    private void requireMappedFolder(String remote, String canonicalFolder) {
        if (remote == null) {
            throw new JeecgBootException("文件夹「" + canonicalFolder + "」未映射到远端，请在同步状态里维护映射后再试");
        }
    }

    private OaMailMessageDetailDTO buildDetailDTO(OaMailMessageIndex row, MailBodyExtractor.ExtractedBody body,
            long latencyMs) {
        OaMailMessageDetailDTO dto = new OaMailMessageDetailDTO();
        dto.setId(row.getId());
        dto.setAccountId(row.getAccountId());
        dto.setCanonicalFolder(row.getCanonicalFolder());
        dto.setImapUid(row.getImapUid());
        dto.setSubject(row.getSubject());
        dto.setFromName(row.getFromName());
        dto.setFromAddressHash(row.getFromAddressHash());
        dto.setToSummary(row.getToSummary());
        dto.setReceivedTime(row.getReceivedTime());
        dto.setReadFlag(row.getReadFlag());
        dto.setHasAttachment(row.getHasAttachment());
        dto.setText(body.getText());
        dto.setHtml(body.getHtml());
        dto.setTruncated(body.isTruncated());
        dto.setAttachments(body.getAttachments());
        dto.setFetchedFromImap(true);
        dto.setFetchLatencyMs(latencyMs);
        return dto;
    }

    private boolean archiveToSentQuietly(OaMailAccount account, MimeMessage message) {
        Store store = null;
        try {
            store = connector.openImapStore(toImapConfig(account));
            String sentRemote = operator.resolveRemoteFolder(folderStateMapper, account.getId(),
                    MailCanonicalFolder.SENT);
            if (sentRemote == null) {
                log.warn("[mail] 账号 {} 未映射「已发送」文件夹，发信不入归档（下次同步不影响）", account.getId());
                return false;
            }
            operator.appendToFolder(store, sentRemote, message, true);
            return true;
        } catch (Exception e) {
            // QQ 等服务商 SMTP 自动落 Sent，APPEND 会返回 "already saved to Sent"——属预期行为，降级为 INFO
            if (e.getMessage() != null && e.getMessage().contains("already saved to Sent")) {
                log.info("[mail] 发信已由服务商自动归档已发送 accountId={}", account.getId());
            } else {
                log.warn("[mail] 发信归档已发送失败 accountId={}: {}", account.getId(), e.getMessage());
            }
            return false;
        } finally {
            closeStoreQuietly(store, account.getId());
        }
    }

    private void closeStoreQuietly(Store store, String accountId) {
        if (store != null) {
            try {
                store.close();
            } catch (jakarta.mail.MessagingException e) {
                log.warn("[mail] 关闭 IMAP 连接失败 accountId={}", accountId);
            }
        }
    }

    private InternetAddress[] parseAddresses(String raw) throws jakarta.mail.internet.AddressException {
        return InternetAddress.parse(raw.replace('；', ';'));
    }


    private void validateBindRequest(MailAccountBindReqDTO req, boolean passwordRequired) {
        if (req == null || StringUtils.isBlank(req.getEmail())) {
            throw new JeecgBootException("邮箱地址不能为空");
        }
        validateServerConfig("IMAP", req.getImap(), passwordRequired);
        validateServerConfig("SMTP", req.getSmtp(), passwordRequired);
    }

    private void validateServerConfig(String side, MailServerConfig cfg, boolean passwordRequired) {
        if (cfg == null) {
            throw new JeecgBootException(side + " 配置不能为空");
        }
        if (StringUtils.isBlank(cfg.getHost()) || cfg.getPort() == null
                || StringUtils.isBlank(cfg.getUsername())) {
            throw new JeecgBootException(side + " 配置不完整（host/port/username 必填）");
        }
        if (passwordRequired && StringUtils.isBlank(cfg.getPassword())) {
            throw new JeecgBootException(side + " 密码不能为空");
        }
    }

    private OaMailAccount getAndCheckOwnership(String id) {
        OaMailAccount existing = getById(id);
        if (existing == null) {
            throw new JeecgBootException("邮箱绑定不存在: " + id);
        }
        ownershipGuard.requireOwnerOrAdmin(existing.getCreateBy(), "邮箱绑定");
        return existing;
    }

    private void applyImapConfig(OaMailAccount account, MailServerConfig cfg, boolean encryptPassword) {
        account.setImapHost(cfg.getHost());
        account.setImapPort(cfg.getPort());
        account.setImapSsl(cfg.isSsl());
        account.setImapUsername(cfg.getUsername());
        if (StringUtils.isNotBlank(cfg.getPassword())) {
            account.setImapPassword(passwordCodec.encode(cfg.getPassword()));
        } else if (encryptPassword) {
            // 绑定路径已在校验层保证密码必填，此处防御性兜底
            throw new JeecgBootException("IMAP 密码不能为空");
        }
    }

    private void applySmtpConfig(OaMailAccount account, MailServerConfig cfg, boolean encryptPassword) {
        account.setSmtpHost(cfg.getHost());
        account.setSmtpPort(cfg.getPort());
        account.setSmtpSsl(cfg.isSsl());
        account.setSmtpUsername(cfg.getUsername());
        if (StringUtils.isNotBlank(cfg.getPassword())) {
            account.setSmtpPassword(passwordCodec.encode(cfg.getPassword()));
        } else if (encryptPassword) {
            throw new JeecgBootException("SMTP 密码不能为空");
        }
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

    private MailServerConfig toSmtpConfig(OaMailAccount account) {
        MailServerConfig cfg = new MailServerConfig();
        cfg.setHost(account.getSmtpHost());
        cfg.setPort(account.getSmtpPort());
        cfg.setSsl(Boolean.TRUE.equals(account.getSmtpSsl()));
        cfg.setUsername(account.getSmtpUsername());
        cfg.setPassword(passwordCodec.decode(account.getSmtpPassword()));
        return cfg;
    }

    /**
     * 修改合并：请求密码非空用请求值；为空沿用库中密文解密后的明文参与重测。
     */
    private MailServerConfig mergedImap(OaMailAccount existing, MailServerConfig req) {
        MailServerConfig merged = new MailServerConfig();
        merged.setHost(req.getHost());
        merged.setPort(req.getPort());
        merged.setSsl(req.isSsl());
        merged.setUsername(req.getUsername());
        merged.setPassword(StringUtils.isNotBlank(req.getPassword())
                ? req.getPassword()
                : passwordCodec.decode(existing.getImapPassword()));
        return merged;
    }

    private MailServerConfig mergedSmtp(OaMailAccount existing, MailServerConfig req) {
        MailServerConfig merged = new MailServerConfig();
        merged.setHost(req.getHost());
        merged.setPort(req.getPort());
        merged.setSsl(req.isSsl());
        merged.setUsername(req.getUsername());
        merged.setPassword(StringUtils.isNotBlank(req.getPassword())
                ? req.getPassword()
                : passwordCodec.decode(existing.getSmtpPassword()));
        return merged;
    }
}
