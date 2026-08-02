package com.livetix.common.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES 对称加密工具类（Spring Bean，密钥从配置文件注入）
 *
 * 用途：对敏感信息（如身份证号码）进行加密存储，查询时自动解密。
 *
 * 加密算法：AES-256-CBC + PKCS5Padding
 *   - 密钥长度：256 bit（32字节）
 *   - 分组模式：CBC（需要初始化向量 IV）
 *   - 填充模式：PKCS5Padding
 *   - 编码方式：Base64
 *
 * 安全说明：
 *   密钥通过 @Value 从 application.yml 注入，生产环境通过环境变量覆盖：
 *     export AES_SECRET_KEY="your-32-byte-key"
 *     export AES_IV="your-16-byte-iv"
 *   默认值仅用于开发环境，生产部署时必须更换密钥。
 */
@Component
public class AesUtil {

    private String secretKey;
    private String iv;

    /** 加密算法 */
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    /** 单例实例 — 兼容静态调用方 */
    private static AesUtil INSTANCE;

    public AesUtil(@Value("${aes.secret-key:LiveTixDevKey2026!@#$%^&*()123456}") String secretKey,
                   @Value("${aes.iv:LiveTixIV2026!@#$}") String iv) {
        this.secretKey = secretKey;
        this.iv = iv;
        INSTANCE = this;
    }

    /**
     * 获取 secretKey 对应 32 字节密钥（截断或右补零）
     */
    private byte[] getKeyBytes() {
        byte[] raw = secretKey.getBytes(StandardCharsets.UTF_8);
        if (raw.length == 32) return raw;
        // 长度不足 32 → 右补零
        byte[] key = new byte[32];
        System.arraycopy(raw, 0, key, 0, Math.min(raw.length, 32));
        return key;
    }

    /**
     * 获取 IV 对应 16 字节向量（截断或右补零）
     */
    private byte[] getIvBytes() {
        byte[] raw = iv.getBytes(StandardCharsets.UTF_8);
        if (raw.length == 16) return raw;
        // 长度不足 16 → 右补零；超过 16 → 截取前 16
        byte[] fixed = new byte[16];
        System.arraycopy(raw, 0, fixed, 0, Math.min(raw.length, 16));
        return fixed;
    }

    /**
     * 加密明文
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            AesUtil instance = INSTANCE != null ? INSTANCE : new AesUtil("LiveTixDevKey2026!@#$%^&*()123456", "LiveTixIV2026!@#$");
            SecretKeySpec keySpec = new SecretKeySpec(instance.getKeyBytes(), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(instance.getIvBytes());

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed", e);
        }
    }

    /**
     * 解密密文
     */
    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        try {
            AesUtil instance = INSTANCE != null ? INSTANCE : new AesUtil("LiveTixDevKey2026!@#$%^&*()123456", "LiveTixIV2026!@#$");
            SecretKeySpec keySpec = new SecretKeySpec(instance.getKeyBytes(), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(instance.getIvBytes());

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
