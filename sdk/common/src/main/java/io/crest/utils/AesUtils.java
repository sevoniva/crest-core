package io.crest.utils;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.security.SecureRandom;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * 提供 AES 和国密模式下的加解密工具方法
 */
public class AesUtils {
    private static final String AES_KEY_PROPERTY = "crest.crypto.aes-key";
    private static final String AES_IV_PROPERTY = "crest.crypto.aes-iv";

    /**
     * 读取并校验 AES 密钥配置
     */
    private static String configuredSecretKey() {
        String value = ConfigUtils.getConfig(AES_KEY_PROPERTY, null);
        int length = StringUtils.length(value);
        if (length != 16 && length != 24 && length != 32) {
            throw new IllegalStateException(AES_KEY_PROPERTY + " must be 16, 24, or 32 characters");
        }
        return value;
    }

    /**
     * 读取并校验 AES 初始化向量配置
     */
    private static String configuredIv() {
        String value = ConfigUtils.getConfig(AES_IV_PROPERTY, null);
        if (StringUtils.length(value) != 16) {
            throw new IllegalStateException(AES_IV_PROPERTY + " must be 16 characters");
        }
        return value;
    }

    @SuppressWarnings("java/weak-cryptographic-algorithm")
    /**
     * 使用指定密钥和初始化向量解密文本
     */
    public static String aesDecrypt(String src, String secretKey, String iv) {
        if (StringUtils.isBlank(secretKey)) {
            throw new RuntimeException("secretKey is empty");
        }
        try {
            byte[] raw = secretKey.getBytes(UTF_8);
            SecretKeySpec secretKeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding"); // nosemgrep: java.lang.security.audit.cbc-padding-oracle.cbc-padding-oracle
            IvParameterSpec iv1 = new IvParameterSpec(iv.getBytes());
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, iv1);
            byte[] encrypted1 = Base64.decodeBase64(src);
            byte[] original = cipher.doFinal(encrypted1);
            return new String(original, UTF_8);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            // 解密的原字符串为非加密字符串，则直接返回原字符串
            return src;
        } catch (Exception e) {
            throw new RuntimeException("decrypt error，please check parameters", e);
        }
    }

    @SuppressWarnings("java/weak-cryptographic-algorithm")
    /**
     * 使用指定密钥和初始化向量加密文本
     */
    public static String aesEncrypt(String src, String secretKey, String iv) {
        if (StringUtils.isBlank(secretKey)) {
            throw new RuntimeException("secretKey is empty");
        }

        try {
            byte[] raw = secretKey.getBytes(UTF_8);
            SecretKeySpec secretKeySpec = new SecretKeySpec(raw, "AES");
            // "算法/模式/补码方式" ECB
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding"); // nosemgrep: java.lang.security.audit.cbc-padding-oracle.cbc-padding-oracle
            IvParameterSpec iv1 = new IvParameterSpec(iv.getBytes());
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, iv1);
            byte[] encrypted = cipher.doFinal(src.getBytes(UTF_8));
            return Base64.encodeBase64String(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt error:", e);
        }

    }

    @SuppressWarnings("java/weak-cryptographic-algorithm")
    /**
     * 使用当前加密模式加密对象文本值
     */
    public static Object aesEncrypt(Object o) {

        if (o == null) {
            return null;
        }
        if (CryptoMode.isSmSuite()) {
            return SmCryptoUtils.sm4EncryptWithConfiguredKey(o.toString());
        }
        return aesEncrypt(o.toString(), configuredSecretKey(), configuredIv());
    }

    /**
     * 使用当前加密模式解密对象文本值
     */
    public static Object aesDecrypt(Object o) {
        if (o == null) {
            return null;
        }
        String value = o.toString();
        if (SmCryptoUtils.isSm4Ciphertext(value)) {
            return SmCryptoUtils.sm4DecryptWithConfiguredKey(value);
        }
        return aesDecrypt(value, configuredSecretKey(), configuredIv());
    }

    @SuppressWarnings("java/weak-cryptographic-algorithm")
    /**
     * 使用随机初始化向量加密文本并拼接向量
     */
    public static String aesEncryptWithIv(String src, String secretKey) {
        if (StringUtils.isBlank(secretKey)) {
            throw new RuntimeException("secretKey is empty");
        }
        try {
            // 生成随机IV
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            byte[] raw = secretKey.getBytes(UTF_8);
            SecretKeySpec secretKeySpec = new SecretKeySpec(raw, "AES");
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding"); // nosemgrep: java.lang.security.audit.cbc-padding-oracle.cbc-padding-oracle
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(src.getBytes(UTF_8));

            // 将IV拼接到密文前面
            byte[] ivAndCipher = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, ivAndCipher, 0, iv.length);
            System.arraycopy(encrypted, 0, ivAndCipher, iv.length, encrypted.length);

            return Base64.encodeBase64String(ivAndCipher);
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt error:", e);
        }
    }
}
