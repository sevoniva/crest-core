package io.crest.listener;

import io.crest.datasource.manage.EngineManage;
import io.crest.datasource.manage.DataSourceManage;
import io.crest.datasource.manage.DatasourceSyncManage;
import io.crest.datasource.provider.CalciteProvider;
import io.crest.datasource.server.DatasourceServer;
import io.crest.datasource.server.DatasourceTaskServer;
import io.crest.dataset.sync.DatasetSyncTaskManage;
import io.crest.system.manage.SysParameterManage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataSourceInitStartListenerTest {

    @Test
    void shouldSkipDatasourcePoolPreloadInProductionApiRole() {
        DataSourceInitStartListener listener = listener("api", true, null);
        CalciteProvider calciteProvider = (CalciteProvider) ReflectionTestUtils.getField(listener, "calciteProvider");

        listener.onApplicationEvent(null);

        verify(calciteProvider, never()).initConnectionPool();
    }

    @Test
    void shouldKeepDatasourcePoolPreloadForLegacyAllInOne() {
        DataSourceInitStartListener listener = listener("all", false, null);
        CalciteProvider calciteProvider = (CalciteProvider) ReflectionTestUtils.getField(listener, "calciteProvider");

        listener.onApplicationEvent(null);

        // 旧 all-in-one 非生产启动路径保持预热行为，避免改变现有使用习惯。
        verify(calciteProvider).initConnectionPool();
    }

    @Test
    void shouldAllowExplicitDatasourcePoolPreloadInProduction() {
        DataSourceInitStartListener listener = listener("api", true, "true");
        CalciteProvider calciteProvider = (CalciteProvider) ReflectionTestUtils.getField(listener, "calciteProvider");

        listener.onApplicationEvent(null);

        verify(calciteProvider).initConnectionPool();
    }

    private DataSourceInitStartListener listener(String role, boolean productionMode, String preloadEnabled) {
        DataSourceInitStartListener listener = new DataSourceInitStartListener();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("crest.runtime.role", role)
                .withProperty("crest.production-mode", Boolean.toString(productionMode));
        if (preloadEnabled != null) {
            environment.withProperty("crest.datasource.pool.preload.enabled", preloadEnabled);
        }
        ReflectionTestUtils.setField(listener, "environment", environment);
        ReflectionTestUtils.setField(listener, "engineManage", mock(EngineManage.class));
        ReflectionTestUtils.setField(listener, "calciteProvider", mock(CalciteProvider.class));
        ReflectionTestUtils.setField(listener, "datasourceSyncManage", mock(DatasourceSyncManage.class));
        ReflectionTestUtils.setField(listener, "datasourceServer", mock(DatasourceServer.class));
        ReflectionTestUtils.setField(listener, "dataSourceManage", mock(DataSourceManage.class));
        DatasourceTaskServer datasourceTaskServer = mock(DatasourceTaskServer.class);
        when(datasourceTaskServer.listAll()).thenReturn(List.of());
        ReflectionTestUtils.setField(listener, "datasourceTaskServer", datasourceTaskServer);
        DatasetSyncTaskManage datasetSyncTaskManage = mock(DatasetSyncTaskManage.class);
        when(datasetSyncTaskManage.listAll()).thenReturn(List.of());
        ReflectionTestUtils.setField(listener, "datasetSyncTaskManage", datasetSyncTaskManage);
        SysParameterManage sysParameterManage = mock(SysParameterManage.class);
        when(sysParameterManage.groupList("basic.")).thenReturn(List.of());
        ReflectionTestUtils.setField(listener, "sysParameterManage", sysParameterManage);
        return listener;
    }
}
