package io.crest.dataset.sync;

import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.dataset.manage.DatasetGroupManage;
import io.crest.dataset.manage.DatasetSQLManage;
import io.crest.exception.CrestException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatasetSyncSupportValidatorTest {

    @Test
    void rejectsBlankGeneratedSql() throws Exception {
        DatasetSyncSupportValidator validator = new DatasetSyncSupportValidator();
        DatasetGroupManage datasetGroupManage = mock(DatasetGroupManage.class);
        DatasetSQLManage datasetSQLManage = mock(DatasetSQLManage.class);
        ReflectionTestUtils.setField(validator, "datasetGroupManage", datasetGroupManage);
        ReflectionTestUtils.setField(validator, "datasetSQLManage", datasetSQLManage);

        DatasetGroupInfoDTO dataset = new DatasetGroupInfoDTO();
        dataset.setId(982004L);
        dataset.setNodeType("dataset");
        dataset.setMode(1);
        dataset.setIsCross(false);
        when(datasetGroupManage.getForCount(982004L)).thenReturn(dataset);
        when(datasetSQLManage.getUnionSQLForEdit(eq(dataset), isNull())).thenReturn(Map.of("sql", " "));

        CrestException exception = assertThrows(CrestException.class, () -> validator.assertSupported(982004L));

        assertEquals("数据集 SQL 为空，暂不支持缓存", exception.getMessage());
    }
}
