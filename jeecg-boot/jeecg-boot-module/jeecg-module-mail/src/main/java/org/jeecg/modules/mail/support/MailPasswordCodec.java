package org.jeecg.modules.mail.support;

/**
 * @Description: 邮件密码编解码接口（可插拔）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 口径（需求文档 §4.4，决策 D-20261117-76）：SMTP/IMAP 需原文认证，禁止不可逆哈希；
 * 密码字段密文存储，密钥走配置中心。试点实现为 AES（见 AesMailPasswordCodec），
 * 接口按可插拔预留国密/KMS 升级——提供本接口的 Bean 即可整体替换，
 * AesMailPasswordCodec 上的 @ConditionalOnMissingBean 保证不冲突。
 */
public interface MailPasswordCodec {

    /**
     * 明文加密为密文（落库前调用）。
     */
    String encode(String plainPassword);

    /**
     * 密文解密为明文（SMTP 认证/测连前调用）。
     * 密文损坏或密钥不匹配时抛带上下文的运行时异常。
     */
    String decode(String cipherPassword);
}
