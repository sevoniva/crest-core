package io.crest.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhitelistUtilsCoreScopeTest {

    @Test
    void swaggerAndLegacyScanLoginPathsAreNotAnonymous() {
        assertThat(WhitelistUtils.match("/doc.html")).isFalse();
        assertThat(WhitelistUtils.match("/swagger-ui.html")).isFalse();
        assertThat(WhitelistUtils.match("/webjars/swagger-ui/index.html")).isFalse();
        assertThat(WhitelistUtils.match("/lark/token")).isFalse();
        assertThat(WhitelistUtils.match("/dingtalk/token")).isFalse();
        assertThat(WhitelistUtils.match("/wecom/token")).isFalse();
    }
}
