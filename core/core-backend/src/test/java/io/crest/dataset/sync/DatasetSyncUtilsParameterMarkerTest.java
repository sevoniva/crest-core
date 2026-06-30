package io.crest.dataset.sync;

import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetSyncUtilsParameterMarkerTest {

    @Test
    void detectsUnboundParameterMarker() {
        assertTrue(DatasetSyncUtils.hasUnboundParameterMarker("SELECT * FROM orders WHERE status = ?"));
        assertTrue(DatasetSyncUtils.hasUnboundParameterMarker("""
                SELECT *
                FROM orders
                WHERE status = 'DONE'
                  AND owner_id = ?
                """));
    }

    @Test
    void ignoresQuestionMarkInsideLiteralsAndQuotedIdentifiers() {
        assertFalse(DatasetSyncUtils.hasUnboundParameterMarker("SELECT '?' AS q FROM dual"));
        assertFalse(DatasetSyncUtils.hasUnboundParameterMarker("SELECT 'it''s ?' AS q FROM dual"));
        assertFalse(DatasetSyncUtils.hasUnboundParameterMarker("SELECT \"?\" FROM \"orders\""));
        assertFalse(DatasetSyncUtils.hasUnboundParameterMarker("SELECT `?` FROM `orders`"));
        assertFalse(DatasetSyncUtils.hasUnboundParameterMarker("SELECT [?] FROM [orders]"));
    }

    @Test
    void ignoresQuestionMarkInsideComments() {
        assertFalse(DatasetSyncUtils.hasUnboundParameterMarker("""
                SELECT 1
                -- ? is only a comment
                FROM dual
                """));
        assertFalse(DatasetSyncUtils.hasUnboundParameterMarker("""
                SELECT 1
                /* ? is only a comment */
                FROM dual
                """));
    }

    @Test
    void detectsMarkerAfterCommentsAndLiterals() {
        assertTrue(DatasetSyncUtils.hasUnboundParameterMarker("""
                SELECT '?'
                FROM dual
                /* ignored ? */
                WHERE id = ?
                """));
    }

    @Test
    void treatsUnboundMarkerAsSqlParameterInSqlMap() {
        assertTrue(DatasetSyncUtils.hasSqlParameters(null, Map.of("sql", "SELECT * FROM orders WHERE status = ?")));
        assertFalse(DatasetSyncUtils.hasSqlParameters(null, Map.of("sql", "SELECT '?' AS q FROM dual")));
    }

    @Test
    void doesNotRouteToCacheWhenGeneratedSqlIsBlank() {
        DatasetGroupInfoDTO dataset = new DatasetGroupInfoDTO();
        dataset.setMode(1);
        dataset.setIsCross(false);

        DatasourceSchemaDTO datasource = new DatasourceSchemaDTO();
        datasource.setType("obOracle");
        Map<Long, DatasourceSchemaDTO> dsMap = new LinkedHashMap<>();
        dsMap.put(1L, datasource);

        assertFalse(DatasetSyncUtils.shouldRouteToCache(dataset, Map.of("sql", " ", "dsMap", dsMap)));
    }
}
