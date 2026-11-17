package org.jeecg.modules.mail.support;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.exception.JeecgBootException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * @Description: 邮件密码 AES 编解码（决策 D-20261117-76 试点实现）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 方案（已拍板）：应用层 AES/GCM/NoPadding（256 位密钥），密钥走配置文件
 * （jeecg.mail.crypto.key）或环境变量（MAIL_CRYPTO_KEY），值为 Base64 编码的 32 字节密钥。
 * 每次加密使用随机 12 字节 IV，密文 = Base64(iv ++ cipher)。
 *
 * 可插拔：任何模块提供 MailPasswordCodec Bean 即替换本实现（国密/KMS 升级路径）。
 * 密钥未配置时构造即失败（fail-fast），禁止静默明文落库。
 */
@Component
@ConditionalOnMissingBean(MailPasswordCodec.class)
public class AesMailPasswordCodec implements MailPasswordCodec {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AesMailPasswordCodec(@Value("${jeecg.mail.crypto.key:${MAIL_CRYPTO_KEY:}}") String base64Key) {
        if (StringUtils.isBlank(base64Key)) {
            throw new IllegalStateException("邮件密码密钥未配置：请设置 jeecg.mail.crypto.key 或环境变量 MAIL_CRYPTO_KEY"
                    + "（Base64 编码的 256 位密钥，可用 keytool/openssl 生成）");
        }
        byte[] rawKey;
        try {
            rawKey = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("邮件密码密钥不是合法 Base64", e);
        }
        if (rawKey.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("邮件密码密钥长度必须为 " + (KEY_LENGTH_BYTES * 8) + " 位（" + KEY_LENGTH_BYTES
                    + " 字节，Base64 编码）");
        }
        this.key = new SecretKeySpec(rawKey, KEY_ALGORITHM);
    }

    @Override
    public String encode(String plainPassword) {
        if (plainPassword == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("邮件密码加密失败", e);
        }
    }

    @Override
    public String decode(String cipherPassword) {
        if (cipherPassword == null) {
            return null;
        }
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(cipherPassword.trim());
        } catch (IllegalArgumentException e) {
            throw new JeecgBootException("邮件密码密文不是合法 Base64", e);
        }
        if (combined.length <= IV_LENGTH_BYTES) {
            throw new JeecgBootException("邮件密码密文长度异常（" + combined.length + " 字节），数据可能损坏");
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(combined, iv.length, combined.length - iv.length);
            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new JeecgBootException("邮件密码解密失败：密钥不匹配或密文被篡改", e);
        } catch (GeneralSecurityException e) {
            throw new JeecgBootException("邮件密码解密失败：密文损坏或算法不可用", e);
        }
    }
}
