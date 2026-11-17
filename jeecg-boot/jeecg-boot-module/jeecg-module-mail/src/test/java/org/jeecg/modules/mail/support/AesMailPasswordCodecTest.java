package org.jeecg.modules.mail.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.jeecg.common.exception.JeecgBootException;
import org.junit.jupiter.api.Test;

/**
 * @Description: AesMailPasswordCodec 纯单元测试（决策 D-20261117-76 试点实现）
 * @Author: mail-dev
 * @Date: 2026-11-17
 *
 * 锁定口径：AES/GCM，密文 = Base64(iv ++ cipher)；密钥不匹配或密文被篡改必须失败，不得静默出明文。
 */
class AesMailPasswordCodecTest {

    private static final String KEY_A = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String KEY_B = Base64.getEncoder()
            .encodeToString("abcdef0123456789abcdef0123456789".getBytes());

    @Test
    void encodeDecode_roundTrip() {
        AesMailPasswordCodec codec = new AesMailPasswordCodec(KEY_A);
        String cipher = codec.encode("smtp-secret-授权码");
        assertNotEquals("smtp-secret-授权码", cipher);
        assertEquals("smtp-secret-授权码", codec.decode(cipher));
    }

    @Test
    void encode_samePlainTwice_producesDifferentCipher() {
        // 随机 IV：同一明文两次加密不得产生同一密文（防密文比对泄露）
        AesMailPasswordCodec codec = new AesMailPasswordCodec(KEY_A);
        assertNotEquals(codec.encode("same-password"), codec.encode("same-password"));
    }

    @Test
    void encodeNullDecodeNull_returnNull() {
        AesMailPasswordCodec codec = new AesMailPasswordCodec(KEY_A);
        assertNull(codec.encode(null));
        assertNull(codec.decode(null));
    }

    @Test
    void decode_withDifferentKey_throwsWithContext() {
        AesMailPasswordCodec encryptor = new AesMailPasswordCodec(KEY_A);
        AesMailPasswordCodec wrongKeyCodec = new AesMailPasswordCodec(KEY_B);
        String cipher = encryptor.encode("secret");
        JeecgBootException ex = assertThrows(JeecgBootException.class, () -> wrongKeyCodec.decode(cipher));
        assertTrue(ex.getMessage().contains("密钥不匹配"));
    }

    @Test
    void decode_tamperedCipher_throws() {
        AesMailPasswordCodec codec = new AesMailPasswordCodec(KEY_A);
        String cipher = codec.encode("secret");
        byte[] raw = Base64.getDecoder().decode(cipher);
        raw[raw.length - 1] ^= (byte) 0xFF;
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThrows(JeecgBootException.class, () -> codec.decode(tampered));
    }

    @Test
    void decode_notBase64_throws() {
        AesMailPasswordCodec codec = new AesMailPasswordCodec(KEY_A);
        assertThrows(JeecgBootException.class, () -> codec.decode("!!not-base64!!"));
    }

    @Test
    void constructor_blankKey_failsFast() {
        assertThrows(IllegalStateException.class, () -> new AesMailPasswordCodec(""));
        assertThrows(IllegalStateException.class, () -> new AesMailPasswordCodec("   "));
    }

    @Test
    void constructor_wrongLengthKey_failsFast() {
        String shortKey = Base64.getEncoder().encodeToString("tooshort".getBytes());
        assertThrows(IllegalStateException.class, () -> new AesMailPasswordCodec(shortKey));
    }

    @Test
    void constructor_notBase64Key_failsFast() {
        assertThrows(IllegalStateException.class, () -> new AesMailPasswordCodec("###"));
    }
}
