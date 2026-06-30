package io.crest.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 提供签名令牌生成和验签能力
 */
public class SignedTokenUtils {

    private static final String SM3_HMAC_ALGORITHM = "SM3-HMAC";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private SignedTokenUtils() {
    }

    /**
     * 根据当前加密模式生成签名令牌
     */
    public static String sign(Map<String, Object> claims, String secret, Date expiresAt) {
        if (CryptoMode.isSmSuite()) {
            return signWithSm3Hmac(claims, secret, expiresAt);
        }
        return signWithHmacSha256(claims, secret, expiresAt);
    }

    /**
     * 校验签名令牌并返回解码结果
     */
    public static DecodedJWT verify(String token, String secret) {
        DecodedJWT decodedJWT = decodeUnverifiedForSecretLookup(token);
        if (SM3_HMAC_ALGORITHM.equals(decodedJWT.getAlgorithm())) {
            verifySm3Hmac(token, secret, decodedJWT);
            return decodedJWT;
        }
        Algorithm algorithm = Algorithm.HMAC256(secret);
        JWTVerifier verifier = JWT.require(algorithm).build();
        return verifier.verify(token);
    }

    /**
     * 只用于读取未验签令牌中的 uid/resourceId 以查找签名密钥，调用方必须随后执行 verify。
     */
    public static DecodedJWT decodeUnverifiedForSecretLookup(String token) {
        return JWT.decode(token);
    }

    /**
     * 使用 HMAC-SHA256 生成标准 JWT
     */
    private static String signWithHmacSha256(Map<String, Object> claims, String secret, Date expiresAt) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        JWTCreator.Builder builder = JWT.create();
        addClaims(builder, claims);
        if (expiresAt != null) {
            builder.withExpiresAt(expiresAt);
        }
        return builder.sign(algorithm);
    }

    /**
     * 使用 SM3-HMAC 生成签名令牌
     */
    private static String signWithSm3Hmac(Map<String, Object> claims, String secret, Date expiresAt) {
        if (StringUtils.isBlank(secret)) {
            throw new IllegalArgumentException("token secret is empty");
        }
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", SM3_HMAC_ALGORITHM);
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>(claims);
        if (expiresAt != null) {
            payload.put("exp", expiresAt.getTime() / 1000);
        }

        String signingInput = base64Url(JsonUtil.toJSONString(header).toString())
                + "."
                + base64Url(JsonUtil.toJSONString(payload).toString());
        byte[] signature = SmCryptoUtils.hmacSm3(
                secret.getBytes(StandardCharsets.UTF_8),
                signingInput.getBytes(StandardCharsets.UTF_8)
        );
        return signingInput + "." + BASE64_URL_ENCODER.encodeToString(signature);
    }

    /**
     * 校验 SM3-HMAC 签名和过期时间
     */
    private static void verifySm3Hmac(String token, String secret, DecodedJWT decodedJWT) {
        if (StringUtils.isBlank(secret)) {
            throw new JWTVerificationException("token secret is empty");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || StringUtils.isBlank(parts[0]) || StringUtils.isBlank(parts[1]) || StringUtils.isBlank(parts[2])) {
            throw new JWTVerificationException("token format is invalid");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expected = SmCryptoUtils.hmacSm3(
                secret.getBytes(StandardCharsets.UTF_8),
                signingInput.getBytes(StandardCharsets.UTF_8)
        );
        byte[] actual;
        try {
            actual = BASE64_URL_DECODER.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new JWTVerificationException("token signature is invalid", e);
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new JWTVerificationException("token signature is invalid");
        }
        Date expiresAt = decodedJWT.getExpiresAt();
        if (expiresAt != null && expiresAt.before(new Date())) {
            throw new JWTVerificationException("token has expired");
        }
    }

    /**
     * 对字符串执行 Base64 URL 安全编码
     */
    private static String base64Url(String value) {
        return BASE64_URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将声明集合写入 JWT 构建器
     */
    private static void addClaims(JWTCreator.Builder builder, Map<String, Object> claims) {
        claims.forEach((key, value) -> {
            if (value instanceof Long longValue) {
                builder.withClaim(key, longValue);
            } else if (value instanceof Integer integerValue) {
                builder.withClaim(key, integerValue);
            } else if (value instanceof Boolean booleanValue) {
                builder.withClaim(key, booleanValue);
            } else if (value instanceof String stringValue) {
                builder.withClaim(key, stringValue);
            } else if (value != null) {
                builder.withClaim(key, value.toString());
            }
        });
    }
}
