package io.crest.dataset.sync;

import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.dataset.dao.auto.entity.CoreDatasetSyncTask;
import io.crest.engine.constant.ExtFieldConstant;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatasetSyncQueryManageTest {

    @Test
    void routeIfSyncedKeepsSourceSqlWhenNoRoutableNormalFieldExists() {
        DatasetSyncQueryManage manage = new DatasetSyncQueryManage();
        DatasetSyncTaskManage taskManage = mock(DatasetSyncTaskManage.class);
        ReflectionTestUtils.setField(manage, "taskManage", taskManage);

        DatasetGroupInfoDTO dataset = new DatasetGroupInfoDTO();
        dataset.setId(982004L);
        dataset.setMode(1);
        dataset.setIsCross(false);

        DatasourceSchemaDTO datasource = new DatasourceSchemaDTO();
        datasource.setType("obOracle");
        Map<Long, DatasourceSchemaDTO> dsMap = new LinkedHashMap<>();
        dsMap.put(1L, datasource);

        DatasetTableFieldDTO field = new DatasetTableFieldDTO();
        field.setChecked(true);
        field.setExtField(ExtFieldConstant.EXT_GROUP);
        field.setEngineFieldName("group_field");
        Map<String, Object> sqlMap = new LinkedHashMap<>();
        sqlMap.put("sql", "SELECT 1 FROM dual");
        sqlMap.put("dsMap", dsMap);
        sqlMap.put("field", List.of(field));

        Map<String, Object> routed = manage.routeIfSynced(dataset, sqlMap);

        assertSame(sqlMap, routed);
        verifyNoInteractions(taskManage);
    }

    @Test
    void routeIfSyncedMarksCacheUnavailableWhenCacheTableCannotBeAccessed() {
        DatasetSyncQueryManage manage = new DatasetSyncQueryManage();
        DatasetSyncTaskManage taskManage = mock(DatasetSyncTaskManage.class);
        ReflectionTestUtils.setField(manage, "taskManage", taskManage);

        DatasetGroupInfoDTO dataset = new DatasetGroupInfoDTO();
        dataset.setId(982004L);
        dataset.setMode(1);
        dataset.setIsCross(false);

        DatasourceSchemaDTO datasource = new DatasourceSchemaDTO();
        datasource.setType("obOracle");
        Map<Long, DatasourceSchemaDTO> dsMap = new LinkedHashMap<>();
        dsMap.put(1L, datasource);

        DatasetTableFieldDTO field = new DatasetTableFieldDTO();
        field.setChecked(true);
        field.setExtField(ExtFieldConstant.EXT_NORMAL);
        field.setEngineFieldName("f_1");
        Map<String, Object> sqlMap = new LinkedHashMap<>();
        sqlMap.put("sql", "SELECT 1 AS f_1 FROM dual");
        sqlMap.put("dsMap", dsMap);
        sqlMap.put("field", List.of(field));

        CoreDatasetSyncTask task = new CoreDatasetSyncTask();
        task.setDatasetGroupId(982004L);
        task.setCacheReady(1);
        task.setSchemaHash(DatasetSyncUtils.schemaHash(List.of(field)));
        when(taskManage.selectByDatasetGroupId(982004L)).thenReturn(task);
        when(taskManage.cacheTableExists(982004L)).thenReturn(false);

        Map<String, Object> routed = manage.routeIfSynced(dataset, sqlMap);

        assertSame(sqlMap, routed);
        verify(taskManage).markCacheUnavailable(982004L, "缓存表不可访问，已回退实时查询");
    }
}
