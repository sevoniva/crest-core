package io.crest.datasource.server;

import io.crest.api.ds.vo.BusiDsRequest;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.DatasourceDTO;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasourceServerAllowedTypesTest {

    @Test
    void emptyAllowedDatasourceTypesUsesCoreDefaultScope() {
        DatasourceServer server = serverWithAllowedTypes("");

        assertThat(server.datasourceTypes())
                .extracting(DatasourceConfiguration.DatasourceType::getType)
                .containsExactly("folder", "API", "Excel", "ExcelRemote", "obOracle");
    }

    @Test
    void configuredCoreAllowedDatasourceTypesKeepFoldersExcelApiAndObOracle() {
        DatasourceServer server = serverWithAllowedTypes("obOracle,Excel,ExcelRemote,API");

        assertThat(server.datasourceTypes())
                .extracting(DatasourceConfiguration.DatasourceType::getType)
                .containsExactly("folder", "API", "Excel", "ExcelRemote", "obOracle");
    }

    @Test
    void preCheckRejectsTypesOutsideConfiguredCoreScope() {
        DatasourceServer server = serverWithAllowedTypes("obOracle,Excel,ExcelRemote,API");
        DatasourceDTO datasource = new DatasourceDTO();
        datasource.setType("mysql");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(server, "preCheckDs", datasource))
                .isInstanceOf(CrestException.class)
                .hasMessageContaining("Datasource type not supported.");
    }

    @Test
    void validateRejectsTypesOutsideConfiguredCoreScope() {
        DatasourceServer server = serverWithAllowedTypes("obOracle,Excel,ExcelRemote,API");
        BusiDsRequest request = new BusiDsRequest();
        request.setType("mysql");

        assertThatThrownBy(() -> server.validate(request))
                .isInstanceOf(CrestException.class)
                .hasMessageContaining("Datasource type not supported.");
    }

    @Test
    void getSchemaRejectsTypesOutsideConfiguredCoreScope() {
        DatasourceServer server = serverWithAllowedTypes("obOracle,Excel,ExcelRemote,API");
        BusiDsRequest request = new BusiDsRequest();
        request.setType("mysql");

        assertThatThrownBy(() -> server.getSchema(request))
                .isInstanceOf(CrestException.class)
                .hasMessageContaining("Datasource type not supported.");
    }

    private static DatasourceServer serverWithAllowedTypes(String allowedTypes) {
        DatasourceServer server = new DatasourceServer();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("crest.datasource.allowed-types", allowedTypes);
        ReflectionTestUtils.setField(server, "environment", environment);
        return server;
    }
}
