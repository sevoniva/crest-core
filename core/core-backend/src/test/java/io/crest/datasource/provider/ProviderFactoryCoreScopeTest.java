package io.crest.datasource.provider;

import io.crest.exception.CrestException;
import io.crest.extensions.datasource.factory.ProviderFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderFactoryCoreScopeTest {

    @Test
    void defaultCoreScopeRejectsNonObOracleBuiltinProviders() {
        assertThatThrownBy(() -> ProviderFactory.getProvider("mysql"))
                .isInstanceOf(CrestException.class)
                .hasMessageContaining("Datasource type not supported.");
    }
}
