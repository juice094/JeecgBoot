package org.jeecg.modules.mail.service.impl;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.jeecg.modules.mail.delivery.AccountMailSenderFactory;
import org.jeecg.modules.mail.dto.ConnectionTestResult;
import org.jeecg.modules.mail.entity.SystemMailAccount;
import org.jeecg.modules.mail.entity.SystemMailTemplate;
import org.jeecg.modules.mail.mapper.SystemMailAccountMapper;
import org.jeecg.modules.mail.service.ISystemMailAccountService;
import org.jeecg.modules.mail.service.ISystemMailTemplateService;
import org.jeecg.modules.mail.support.MailPasswordCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @Description: OA 邮件-系统发信账号 ServiceImpl（Phase 1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 口径（需求文档 A-1）：多发送账号配置；被模板引用的账号删除请求被拒绝；
 * 保存前可测连（接口 §3.1 POST {id}/test）。密码加密落库（D-76），响应侧剥离密码。
 */
@Service
public class SystemMailAccountServiceImpl extends ServiceImpl<SystemMailAccountMapper, SystemMailAccount>
        implements ISystemMailAccountService {

    /** 模板状态：启用（与 SystemMailTemplate.status 口径一致） */
    private static final int STATUS_ENABLED = 1;

    @Autowired
    private MailPasswordCodec passwordCodec;

    @Autowired
    private AccountMailSenderFactory senderFactory;

    @Autowired
    private ISystemMailTemplateService templateService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createAccount(SystemMailAccount account) {
        validateRequired(account);
        if (StringUtils.isBlank(account.getPassword())) {
            throw new JeecgBootException("SMTP 密码不能为空");
        }
        account.setId(null);
        account.setPassword(passwordCodec.encode(account.getPassword()));
        if (account.getStatus() == null) {
            account.setStatus(STATUS_ENABLED);
        }
        account.setCreateTime(new Date());
        save(account);
        return account.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAccount(String id, SystemMailAccount account) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("账号 id 不能为空");
        }
        SystemMailAccount existing = getById(id);
        if (existing == null) {
            throw new JeecgBootException("发送账号不存在: " + id);
        }
        validateRequired(account);
        existing.setHost(account.getHost());
        existing.setPort(account.getPort());
        existing.setUsername(account.getUsername());
        // 密码为空表示不修改；非空则加密后覆盖
        if (StringUtils.isNotBlank(account.getPassword())) {
            existing.setPassword(passwordCodec.encode(account.getPassword()));
        }
        existing.setSslEnable(account.getSslEnable());
        existing.setStarttlsEnable(account.getStarttlsEnable());
        existing.setFromAddress(account.getFromAddress());
        existing.setFromName(account.getFromName());
        existing.setStatus(account.getStatus());
        existing.setUpdateTime(new Date());
        updateById(existing);
        // SMTP 连接参数变化，清缓存
        senderFactory.evict(id);
    }

    @Override
    public IPage<SystemMailAccount> pageAccounts(long pageNo, long pageSize, Integer status, String keyword) {
        Page<SystemMailAccount> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<SystemMailAccount> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(SystemMailAccount::getStatus, status);
        }
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(SystemMailAccount::getHost, keyword)
                    .or().like(SystemMailAccount::getUsername, keyword));
        }
        wrapper.orderByDesc(SystemMailAccount::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(String id) {
        if (StringUtils.isBlank(id)) {
            throw new JeecgBootException("账号 id 不能为空");
        }
        SystemMailAccount existing = getById(id);
        if (existing == null) {
            throw new JeecgBootException("发送账号不存在: " + id);
        }
        long referenced = templateService.count(new LambdaQueryWrapper<SystemMailTemplate>()
                .eq(SystemMailTemplate::getAccountId, id));
        if (referenced > 0) {
            throw new JeecgBootException("发送账号被 " + referenced + " 个模板引用，拒绝删除: " + id);
        }
        removeById(id);
        senderFactory.evict(id);
    }

    @Override
    public ConnectionTestResult testConnection(String id) {
        SystemMailAccount existing = StringUtils.isBlank(id) ? null : getById(id);
        if (existing == null) {
            throw new JeecgBootException("发送账号不存在: " + id);
        }
        return senderFactory.testSmtp(existing, passwordCodec.decode(existing.getPassword()));
    }

    private void validateRequired(SystemMailAccount account) {
        if (account == null) {
            throw new JeecgBootException("账号信息不能为空");
        }
        if (StringUtils.isBlank(account.getHost())) {
            throw new JeecgBootException("SMTP 主机不能为空");
        }
        if (account.getPort() == null) {
            throw new JeecgBootException("SMTP 端口不能为空");
        }
        if (StringUtils.isBlank(account.getUsername())) {
            throw new JeecgBootException("SMTP 用户名不能为空");
        }
        if (StringUtils.isBlank(account.getFromAddress())) {
            throw new JeecgBootException("发件人地址不能为空");
        }
    }
}
