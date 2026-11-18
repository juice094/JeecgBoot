package org.jeecg.modules.mail.service.impl;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.mail.delivery.ImapSmtpConnector;
import org.jeecg.modules.mail.delivery.MailConnectFailedException;
import org.jeecg.modules.mail.dto.ConnectionTestResult;
import org.jeecg.modules.mail.dto.MailAccountBindReqDTO;
import org.jeecg.modules.mail.dto.MailFolderMappingReqDTO;
import org.jeecg.modules.mail.dto.MailServerConfig;
import org.jeecg.modules.mail.dto.MailSyncStatusDTO;
import org.jeecg.modules.mail.dto.OaMailAccountRespDTO;
import org.jeecg.modules.mail.entity.OaMailAccount;
import org.jeecg.modules.mail.entity.OaMailFolderState;
import org.jeecg.modules.mail.entity.OaMailMessageIndex;
import org.jeecg.modules.mail.enums.MailCanonicalFolder;
import org.jeecg.modules.mail.mapper.OaMailAccountMapper;
import org.jeecg.modules.mail.mapper.OaMailFolderStateMapper;
import org.jeecg.modules.mail.mapper.OaMailMessageIndexMapper;
import org.jeecg.modules.mail.service.IOaMailService;
import org.jeecg.modules.mail.support.MailPasswordCodec;
import org.jeecg.modules.mail.sync.MailSyncTracker;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String bind(MailAccountBindReqDTO req) {
        validateBindRequest(req, true);
        String owner = ownershipGuard.currentUsernameOrNull();
        Long duplicates = count(new LambdaQueryWrapper<OaMailAccount>()
                .eq(OaMailAccount::getEmail, req.getEmail().trim())
                .eq(OaMailAccount::getCreateBy, owner));
        if (duplicates > 0) {
            throw new JeecgBootException("该邮箱已绑定: " + req.getEmail());
        }

        // 双测连通过才落库（B-1）；失败抛 MailConnectFailedException，由 REST 层组装结构化响应
        ConnectionTestResult testResult = connector.testBoth(req.getImap(), req.getSmtp());
        if (!testResult.allOk()) {
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

    // ---------- 内部 ----------

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
