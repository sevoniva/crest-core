package io.crest.utils;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignedTokenUtilsTest {

    private static final String SECRET = "enterprise-token-secret";

    @Test
    void verifyReturnsTrustedClaims() {
        String token = signedToken();

        DecodedJWT jwt = SignedTokenUtils.verify(token, SECRET);

        assertThat(jwt.getClaim("uid").asLong()).isEqualTo(10001L);
        assertThat(jwt.getClaim("oid").asLong()).isEqualTo(20002L);
        assertThat(jwt.getClaim("resourceId").asLong()).isEqualTo(30003L);
    }

    @Test
    void verifyRejectsWrongSecret() {
        String token = signedToken();

        assertThatThrownBy(() -> SignedTokenUtils.verify(token, "wrong-secret"))
                .isInstanceOf(JWTVerificationException.class);
    }

    @Test
    void unverifiedDecodeOnlySupportsSecretLookup() {
        String token = signedToken();

        DecodedJWT unverifiedJwt = SignedTokenUtils.decodeUnverifiedForSecretLookup(token);

        assertThat(unverifiedJwt.getClaim("uid").asLong()).isEqualTo(10001L);
        assertThatThrownBy(() -> SignedTokenUtils.verify(token, "wrong-secret"))
                .isInstanceOf(JWTVerificationException.class);
    }

    private String signedToken() {
        return SignedTokenUtils.sign(Map.of(
                        "uid", 10001L,
                        "oid", 20002L,
                        "resourceId", 30003L),
                SECRET,
                Date.from(Instant.now().plusSeconds(300)));
    }
}
