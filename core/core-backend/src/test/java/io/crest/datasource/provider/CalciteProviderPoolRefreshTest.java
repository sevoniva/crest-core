package io.crest.datasource.provider;

import io.crest.extensions.datasource.dto.DatasourceDTO;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import io.crest.datasource.type.H2;
import io.crest.utils.CommonBeanFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalciteProviderPoolRefreshTest {

    @Test
    void shouldCreateCalciteConnectionWhenRefreshingDatasourcePoolWithoutPreload() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBeansOfType(DatasourceConfiguration.class)).thenReturn(Map.of("h2", new H2()));
        new CommonBeanFactory().setApplicationContext(applicationContext);
        CalciteProvider provider = new CalciteProvider();
        DatasourceDTO datasource = new DatasourceDTO();
        datasource.setId(1L);
        datasource.setName("h2-smoke");
        datasource.setType("h2");
        datasource.setConfiguration("""
                {
                  "jdbc": "jdbc:h2:mem:crest_pool_refresh;MODE=MySQL;DB_CLOSE_DELAY=-1",
                  "driver": "org.h2.Driver",
                  "dataBase": "PUBLIC",
                  "initialPoolSize": 0,
                  "minPoolSize": 0,
                  "maxPoolSize": 2,
                  "queryTimeout": 5,
                  "customDriver": "default",
                  "useSSH": false
                }
                """);

        provider.updateDsPoolAfterCheckStatus(datasource);

        Connection connection = (Connection) ReflectionTestUtils.getField(provider, "connection");
        assertThat(connection).isNotNull();
    }
}
