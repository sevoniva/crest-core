package io.crest.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

// 提供当前模块复用的工具能力
public enum CryptoMode {
    STANDARD,
    SM_SUITE;

    private static final String CRYPTO_MODE_PROPERTY = "crest.crypto.mode";

    public static CryptoMode current() {
        String mode = ConfigUtils.getConfig(CRYPTO_MODE_PROPERTY, "standard");
        String normalized = StringUtils.trimToEmpty(mode).toLowerCase(Locale.ROOT);
        if ("standard".equals(normalized)) {
            return STANDARD;
        }
        if ("sm-suite".equals(normalized)) {
            return SM_SUITE;
        }
        throw new IllegalStateException(CRYPTO_MODE_PROPERTY + " must be standard or sm-suite");
    }

    // 判断当前类型是否满足业务分类
    public static boolean isSmSuite() {
        return current() == SM_SUITE;
    }
}
