package org.jeecg.modules.mail.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * @Description: 邮件摘要工具（哈希、指纹）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 口径（接口文档 §3.4 / §8）：收件人指纹 = SHA-256 的前 8 位十六进制；
 * 应用日志与日志查询只允许出现指纹，不出现完整收件人（需求文档 A-7、D-4）。
 */
public final class MailDigests {

    private static final String DIGEST_ALGORITHM = "SHA-256";
    /** 指纹长度（十六进制字符数，接口文档 §8：SHA-256 前 8 位） */
    private static final int HASH_PREFIX_CHARS = 8;
    private static final int HASH_PREFIX_BYTES = HASH_PREFIX_CHARS / 2;

    private MailDigests() {
    }

    /**
     * 计算收件人指纹（SHA-256 前 8 位十六进制）；入参为空返回 null。
     */
    public static String toHash8(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed, 0, HASH_PREFIX_BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(DIGEST_ALGORITHM + " 算法不可用", e);
        }
    }
}
