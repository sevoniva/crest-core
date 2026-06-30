package io.crest.commons.utils;

import io.crest.utils.BeanUtils;
import io.crest.utils.ConfigUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 提供平台配置化的 AES、SM4 和 MD5 加解密工具入口
 */
public class EncryptUtils extends CodingUtil {

    private static final String AES_KEY_PROPERTY = "crest.crypto.aes-key";
    private static final String AES_IV_PROPERTY = "crest.crypto.aes-iv";

    /**
     * 读取并校验 AES 密钥配置
     */
    private static String configuredSecretKey() {
        String value = ConfigUtils.getConfig(AES_KEY_PROPERTY, null);
        if (!isValidAesValue(value)) {
            throw new IllegalStateException(AES_KEY_PROPERTY + " must be 16, 24, or 32 characters");
        }
        return value;
    }

    /**
     * 读取并校验 AES 初始化向量配置
     */
    private static String iv() {
        String value = ConfigUtils.getConfig(AES_IV_PROPERTY, null);
        if (StringUtils.length(value) != 16) {
            throw new IllegalStateException(AES_IV_PROPERTY + " must be 16 characters");
        }
        return value;
    }

    /**
     * 判断配置值长度是否满足 AES 密钥要求
     */
    private static boolean isValidAesValue(String value) {
        int length = StringUtils.length(value);
        return length == 16 || length == 24 || length == 32;
    }


    /**
     * 按当前密码套件对单个值进行加密
     */
    public static Object aesEncrypt(Object o) {
        if (o == null) {
            return null;
        }
        if (io.crest.utils.CryptoMode.isSmSuite()) {
            return io.crest.utils.SmCryptoUtils.sm4EncryptWithConfiguredKey(o.toString());
        }
        return aesEncrypt(o.toString(), configuredSecretKey(), iv());
    }

    /**
     * 按密文格式对单个值进行解密
     */
    public static Object aesDecrypt(Object o) {
        if (o == null) {
            return null;
        }
        String value = o.toString();
        if (io.crest.utils.SmCryptoUtils.isSm4Ciphertext(value)) {
            return io.crest.utils.SmCryptoUtils.sm4DecryptWithConfiguredKey(value);
        }
        return aesDecrypt(value, configuredSecretKey(), iv());
    }

    /**
     * 批量解密集合中指定属性的值
     */
    public static <T> Object aesDecrypt(List<T> o, String attrName) {
        if (o == null) {
            return null;
        }
        return o.stream()
                .filter(element -> BeanUtils.getFieldValueByName(attrName, element) != null)
                .peek(element -> BeanUtils.setFieldValueByName(element, attrName, aesDecrypt(BeanUtils.getFieldValueByName(attrName, element)), String.class))
                .collect(Collectors.toList());
    }

    /**
     * 对指定值生成 MD5 摘要
     */
    public static Object md5Encrypt(Object o) {
        if (o == null) {
            return null;
        }
        return md5(o.toString());
    }
}
