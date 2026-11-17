package org.jeecg.modules.mail.service;

import org.jeecg.modules.mail.dto.ConnectionTestResult;
import org.jeecg.modules.mail.entity.SystemMailAccount;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @Description: OA 邮件-系统发信账号 Service（Phase 1）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 对齐接口文档 §3.1 账号管理；密码经 MailPasswordCodec 密文落库（D-76）。
 */
public interface ISystemMailAccountService extends IService<SystemMailAccount> {

    /**
     * 新建发送账号；明文密码落库前加密，返回账号 id。
     */
    String createAccount(SystemMailAccount account);

    /**
     * 修改账号；请求体密码为空表示不修改密码（保留原密文）。
     */
    void updateAccount(String id, SystemMailAccount account);

    /**
     * 分页查询（status 精确、keyword 模糊匹配 host/username）。
     */
    IPage<SystemMailAccount> pageAccounts(long pageNo, long pageSize, Integer status, String keyword);

    /**
     * 删除账号；被模板引用则拒绝（接口文档 §3.1 DELETE 说明）。
     */
    void deleteAccount(String id);

    /**
     * 测试 SMTP 连通（真实握手，不发信），返回连通性与错误分类。
     */
    ConnectionTestResult testConnection(String id);
}
