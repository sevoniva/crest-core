package io.crest.utils;

import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.asn1.x9.ECNamedCurveTable;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.engines.SM2Engine;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;

/**
 * 国密算法工具类，集中处理 SM2、SM3 和 SM4 的密钥、加解密与完整性校验
 */
public class SmCryptoUtils {

    public static final String SM_SUITE_PREFIX = "sm-suite:v1:";
    public static final String SM2_PREFIX = SM_SUITE_PREFIX + "sm2:";
    public static final String SM4_PREFIX = SM_SUITE_PREFIX + "sm4:";
    public static final String SM2_PUBLIC_KEY_PREFIX = SM_SUITE_PREFIX + "sm2-public:";

    private static final String SM4_KEY_PROPERTY = "crest.crypto.sm4-key";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final org.bouncycastle.asn1.x9.X9ECParameters SM2_PARAMETERS = ECNamedCurveTable.getByName("sm2p256v1");
    private static final ECDomainParameters SM2_DOMAIN = new ECDomainParameters(
            SM2_PARAMETERS.getCurve(),
            SM2_PARAMETERS.getG(),
            SM2_PARAMETERS.getN(),
            SM2_PARAMETERS.getH()
    );

    static {
        ensureProvider();
    }

    private SmCryptoUtils() {
    }

    /**
     * 确保 Bouncy Castle 安全提供方已注册
     */
    public static void ensureProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * 生成一组 SM2 公私钥，私钥为定长十六进制，公钥为未压缩点格式
     */
    public static Sm2KeyPair generateSm2KeyPair() {
        ensureProvider();
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(SM2_DOMAIN, SECURE_RANDOM));
        AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();
        ECPrivateKeyParameters privateKey = (ECPrivateKeyParameters) keyPair.getPrivate();
        ECPublicKeyParameters publicKey = (ECPublicKeyParameters) keyPair.getPublic();
        return new Sm2KeyPair(toFixedLengthHex(privateKey.getD(), 64), Hex.toHexString(publicKey.getQ().getEncoded(false)));
    }

    /**
     * 将 SM2 公钥包装为带版本前缀的传输载荷
     */
    public static String sm2PublicKeyPayload(String publicKey) {
        return SM2_PUBLIC_KEY_PREFIX + normalizePublicKey(publicKey);
    }

    /**
     * 从带版本前缀的载荷中读取 SM2 公钥
     */
    public static String readSm2PublicKeyPayload(String payload) {
        if (!startsWith(payload, SM2_PUBLIC_KEY_PREFIX)) {
            throw new IllegalArgumentException("SM2 public key payload is invalid");
        }
        return payload.substring(SM2_PUBLIC_KEY_PREFIX.length());
    }

    /**
     * 使用 SM2 公钥加密 UTF-8 明文，并返回带算法前缀的密文
     */
    public static String sm2Encrypt(String plainText, String publicKey) {
        if (plainText == null) {
            throw new IllegalArgumentException("plainText must not be null");
        }
        try {
            ensureProvider();
            ECPublicKeyParameters publicKeyParameters = new ECPublicKeyParameters(
                    SM2_PARAMETERS.getCurve().decodePoint(Hex.decode(normalizePublicKey(publicKey))),
                    SM2_DOMAIN
            );
            SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            engine.init(true, new ParametersWithRandom(publicKeyParameters, SECURE_RANDOM));
            byte[] input = plainText.getBytes(StandardCharsets.UTF_8);
            return SM2_PREFIX + Hex.toHexString(engine.processBlock(input, 0, input.length));
        } catch (InvalidCipherTextException e) {
            throw new IllegalStateException("SM2 encrypt error", e);
        }
    }

    /**
     * 使用 SM2 私钥解密带算法前缀的密文
     */
    public static String sm2Decrypt(String ciphertext, String privateKey) {
        if (!startsWith(ciphertext, SM2_PREFIX)) {
            throw new IllegalArgumentException("SM2 ciphertext is invalid");
        }
        try {
            ensureProvider();
            ECPrivateKeyParameters privateKeyParameters = new ECPrivateKeyParameters(
                    new BigInteger(1, Hex.decode(normalizePrivateKey(privateKey))),
                    SM2_DOMAIN
            );
            SM2Engine engine = new SM2Engine(SM2Engine.Mode.C1C3C2);
            engine.init(false, privateKeyParameters);
            byte[] data = normalizeSm2CipherBytes(ciphertext.substring(SM2_PREFIX.length()));
            byte[] plainText = engine.processBlock(data, 0, data.length);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (InvalidCipherTextException e) {
            throw new IllegalArgumentException("SM2 ciphertext is invalid", e);
        }
    }

    /**
     * 使用 16 字符明文密钥执行 SM4 加密
     */
    public static String sm4Encrypt(String plainText, String secretKey) {
        return sm4Encrypt(plainText, keyBytes(secretKey));
    }

    /**
     * 使用 Base64 编码的 128 位密钥执行 SM4 加密
     */
    public static String sm4EncryptWithBase64Key(String plainText, String base64SecretKey) {
        return sm4Encrypt(plainText, Base64.getDecoder().decode(base64SecretKey));
    }

    /**
     * 使用系统配置的 SM4 密钥执行加密
     */
    public static String sm4EncryptWithConfiguredKey(String plainText) {
        return sm4Encrypt(plainText, configuredSm4KeyBytes());
    }

    /**
     * 使用 SM4 CBC 模式加密明文，并追加 HMAC-SM3 完整性校验值
     */
    public static String sm4Encrypt(String plainText, byte[] secretKey) {
        if (plainText == null) {
            return null;
        }
        try {
            ensureProvider();
            byte[] key = validateSm4Key(secretKey);
            byte[] iv = randomBytes(16);
            Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS7Padding", BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "SM4"), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            String ivHex = Hex.toHexString(iv);
            String ciphertextHex = Hex.toHexString(encrypted);
            return SM4_PREFIX + ivHex + ":" + ciphertextHex + ":" + sm4MacHex(key, ivHex, ciphertextHex);
        } catch (Exception e) {
            throw new IllegalStateException("SM4 encrypt error", e);
        }
    }

    /**
     * 使用 16 字符明文密钥解密 SM4 密文
     */
    public static String sm4Decrypt(String ciphertext, String secretKey) {
        return sm4Decrypt(ciphertext, keyBytes(secretKey));
    }

    /**
     * 使用 Base64 编码的 128 位密钥解密 SM4 密文
     */
    public static String sm4DecryptWithBase64Key(String ciphertext, String base64SecretKey) {
        return sm4Decrypt(ciphertext, Base64.getDecoder().decode(base64SecretKey));
    }

    /**
     * 使用系统配置的 SM4 密钥解密密文
     */
    public static String sm4DecryptWithConfiguredKey(String ciphertext) {
        return sm4Decrypt(ciphertext, configuredSm4KeyBytes());
    }

    /**
     * 解密带版本前缀的 SM4 密文，并在存在校验值时校验完整性
     */
    public static String sm4Decrypt(String ciphertext, byte[] secretKey) {
        if (!startsWith(ciphertext, SM4_PREFIX)) {
            throw new IllegalArgumentException("SM4 ciphertext is invalid");
        }
        try {
            ensureProvider();
            String[] parts = ciphertext.substring(SM4_PREFIX.length()).split(":", -1);
            if (parts.length != 2 && parts.length != 3) {
                throw new IllegalArgumentException("SM4 ciphertext is invalid");
            }
            byte[] key = validateSm4Key(secretKey);
            if (parts.length == 3) {
                verifySm4Mac(key, parts[0], parts[1], parts[2]);
            }
            Cipher cipher = Cipher.getInstance("SM4/CBC/PKCS7Padding", BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "SM4"),
                    new IvParameterSpec(Hex.decode(parts[0]))
            );
            return new String(cipher.doFinal(Hex.decode(parts[1])), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("SM4 ciphertext is invalid", e);
        }
    }

    /**
     * 生成 Base64 编码的 128 位 SM4 密钥
     */
    public static String generateBase64Sm4Key() {
        try {
            ensureProvider();
            KeyGenerator keyGenerator = KeyGenerator.getInstance("SM4", BouncyCastleProvider.PROVIDER_NAME);
            keyGenerator.init(128, SECURE_RANDOM);
            return Base64.getEncoder().encodeToString(keyGenerator.generateKey().getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("SM4 key generation error", e);
        }
    }

    /**
     * 使用多轮 HMAC-SM3 派生密码哈希
     */
    public static byte[] sm3HmacHash(char[] password, byte[] salt, int iterations) {
        if (password == null || salt == null || iterations < 1) {
            throw new IllegalArgumentException("SM3 hash parameters are invalid");
        }
        byte[] passwordBytes = new String(password).getBytes(StandardCharsets.UTF_8);
        byte[] current = doHmacSm3(passwordBytes, salt);
        for (int i = 1; i < iterations; i++) {
            current = doHmacSm3(passwordBytes, concat(current, salt));
        }
        return current;
    }

    /**
     * 使用 SM3 HMAC 计算消息认证码
     */
    public static byte[] hmacSm3(byte[] key, byte[] data) {
        if (key == null || data == null) {
            throw new IllegalArgumentException("SM3 HMAC parameters are invalid");
        }
        return doHmacSm3(key, data);
    }

    /**
     * 判断字符串是否为国密套件生成的密文或载荷
     */
    public static boolean isSmSuiteCiphertext(String value) {
        return startsWith(value, SM_SUITE_PREFIX);
    }

    /**
     * 判断字符串是否为 SM2 密文
     */
    public static boolean isSm2Ciphertext(String value) {
        return startsWith(value, SM2_PREFIX);
    }

    /**
     * 判断字符串是否为 SM4 密文
     */
    public static boolean isSm4Ciphertext(String value) {
        return startsWith(value, SM4_PREFIX);
    }

    /**
     * 在国密模式启用时校验必要的 SM4 配置密钥
     */
    public static void validateConfiguration() {
        if (CryptoMode.isSmSuite()) {
            validateSm4Key(configuredSm4KeyBytes());
        }
    }

    /**
     * 使用配置密钥加密 SM2 私钥后用于持久化存储
     */
    public static String encryptSm2PrivateKeyForStorage(String privateKey) {
        return sm4EncryptWithConfiguredKey(normalizePrivateKey(privateKey));
    }

    /**
     * 从存储值读取 SM2 私钥，兼容明文历史值和 SM4 加密值
     */
    public static String readSm2PrivateKeyFromStorage(String storedPrivateKey) {
        if (isSm4Ciphertext(storedPrivateKey)) {
            return normalizePrivateKey(sm4DecryptWithConfiguredKey(storedPrivateKey));
        }
        return normalizePrivateKey(storedPrivateKey);
    }

    /**
     * 读取并校验系统配置的 SM4 密钥
     */
    private static byte[] configuredSm4KeyBytes() {
        String value = ConfigUtils.getConfig(SM4_KEY_PROPERTY, null);
        if (StringUtils.isBlank(value)) {
            throw new IllegalStateException(SM4_KEY_PROPERTY + " must be configured when crest.crypto.mode is sm-suite");
        }
        if (StringUtils.length(value) == 16) {
            return validateSm4Key(value.getBytes(StandardCharsets.UTF_8));
        }
        try {
            return validateSm4Key(Base64.getDecoder().decode(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(SM4_KEY_PROPERTY + " must be 16 characters or a Base64 encoded 128-bit key", e);
        }
    }

    /**
     * 将明文字符串密钥转换为 UTF-8 字节
     */
    private static byte[] keyBytes(String secretKey) {
        if (StringUtils.isBlank(secretKey)) {
            throw new IllegalArgumentException("SM4 secretKey is empty");
        }
        return secretKey.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 校验 SM4 密钥长度必须为 128 位
     */
    private static byte[] validateSm4Key(byte[] key) {
        if (key == null || key.length != 16) {
            throw new IllegalArgumentException("SM4 secretKey must be 128 bits");
        }
        return key;
    }

    /**
     * 执行底层 HMAC-SM3 计算
     */
    private static byte[] doHmacSm3(byte[] key, byte[] data) {
        HMac hmac = new HMac(new SM3Digest());
        hmac.init(new KeyParameter(key));
        hmac.update(data, 0, data.length);
        byte[] output = new byte[hmac.getMacSize()];
        hmac.doFinal(output, 0);
        return output;
    }

    /**
     * 根据 IV 和密文生成 SM4 完整性校验值
     */
    private static String sm4MacHex(byte[] key, String ivHex, String ciphertextHex) {
        return Hex.toHexString(hmacSm3(key, (ivHex + ":" + ciphertextHex).getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 校验 SM4 密文附带的 HMAC-SM3 完整性标记
     */
    private static void verifySm4Mac(byte[] key, String ivHex, String ciphertextHex, String macHex) {
        if (StringUtils.isBlank(macHex)) {
            throw new IllegalArgumentException("SM4 ciphertext is invalid");
        }
        byte[] expected = Hex.decode(sm4MacHex(key, ivHex, ciphertextHex));
        byte[] actual;
        try {
            actual = Hex.decode(macHex);
        } catch (Exception e) {
            throw new IllegalArgumentException("SM4 ciphertext is invalid", e);
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("SM4 ciphertext integrity check failed");
        }
    }

    /**
     * 拼接两段字节数组
     */
    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    /**
     * 兼容缺少未压缩点前缀的 SM2 密文字节
     */
    private static byte[] normalizeSm2CipherBytes(String cipherHex) {
        byte[] data = Hex.decode(cipherHex);
        if (data.length > 0 && data[0] == 0x04) {
            return data;
        }
        byte[] normalized = new byte[data.length + 1];
        normalized[0] = 0x04;
        System.arraycopy(data, 0, normalized, 1, data.length);
        return normalized;
    }

    /**
     * 规范化 SM2 公钥为未压缩点十六进制格式
     */
    private static String normalizePublicKey(String publicKey) {
        String normalized = StringUtils.deleteWhitespace(publicKey);
        if (!startsWithIgnoreCase(normalized, "04")) {
            normalized = "04" + normalized;
        }
        if (normalized.length() != 130) {
            throw new IllegalArgumentException("SM2 public key must be an uncompressed 65-byte point");
        }
        return normalized.toLowerCase();
    }

    /**
     * 规范化 SM2 私钥为 64 位十六进制格式
     */
    private static String normalizePrivateKey(String privateKey) {
        String normalized = StringUtils.deleteWhitespace(privateKey);
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("SM2 private key is invalid");
        }
        return StringUtils.leftPad(normalized, 64, '0').toLowerCase();
    }

    /**
     * 将大整数转换为指定长度的十六进制字符串
     */
    private static String toFixedLengthHex(BigInteger value, int length) {
        return StringUtils.leftPad(value.toString(16), length, '0');
    }

    /**
     * 空安全的前缀匹配
     */
    private static boolean startsWith(String value, String prefix) {
        return value != null && value.startsWith(prefix);
    }

    /**
     * 空安全的忽略大小写前缀匹配
     */
    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value != null && value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /**
     * 生成指定长度的安全随机字节
     */
    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * SM2 密钥对值对象
     */
    public static class Sm2KeyPair {
        private final String privateKey;
        private final String publicKey;

        /**
         * 创建 SM2 密钥对
         */
        public Sm2KeyPair(String privateKey, String publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }

        /**
         * 获取十六进制私钥
         */
        public String privateKey() {
            return privateKey;
        }

        /**
         * 获取十六进制公钥
         */
        public String publicKey() {
            return publicKey;
        }
    }
}
