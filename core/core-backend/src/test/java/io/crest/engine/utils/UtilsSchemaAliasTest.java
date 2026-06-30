package io.crest.engine.utils;

import io.crest.dataset.sync.DatasetSyncUtils;
import io.crest.engine.constant.ExtFieldConstant;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtilsSchemaAliasTest {

    @Test
    void singleDatasourceQueryShouldReplaceObOracleSchemaAliasWithRealSchema() {
        DatasourceSchemaDTO datasource = datasource("obOracle", "s_a_1", "{\"schema\":\"CREST_OFFLINE\"}");

        String sql = "SELECT \"f_1\" FROM \"s_a_1\".\"dataset_sync_126\" WHERE s_a_1.\"f_2\" IS NOT NULL";
        String actual = Utils.replaceSchemaAlias(sql, dsMap(datasource));

        assertEquals("SELECT \"f_1\" FROM \"CREST_OFFLINE\".\"dataset_sync_126\" WHERE \"CREST_OFFLINE\".\"f_2\" IS NOT NULL", actual);
    }

    @Test
    void cacheSelectSqlShouldNotLeakInternalSchemaAliasToObOracle() {
        DatasourceSchemaDTO datasource = datasource("obOracle", "s_a_1", "{\"schema\":\"CREST_OFFLINE\"}");
        String cacheSql = DatasetSyncUtils.buildCacheSelectSql(
                1267813530418155520L,
                List.of(field("f_1"), field("f_2")),
                "\"",
                "\"",
                datasource.getSchemaAlias()
        );

        String actual = Utils.replaceSchemaAlias(cacheSql, dsMap(datasource));

        assertEquals("SELECT \"f_1\" AS \"f_1\",\"f_2\" AS \"f_2\" FROM \"CREST_OFFLINE\".\"dataset_sync_1267813530418155520\"", actual);
    }

    @Test
    void singleDatasourceQueryShouldRemoveMysqlSchemaAliasWithoutRealSchema() {
        DatasourceSchemaDTO datasource = datasource("mysql", "s_a_1", "{\"dataBase\":\"crest\"}");

        String sql = "SELECT `s_a_1`.`f_1` FROM `s_a_1`.`dataset_sync_126` WHERE s_a_1.`f_2` = 1";
        String actual = Utils.replaceSchemaAlias(sql, dsMap(datasource));

        assertEquals("SELECT `f_1` FROM `dataset_sync_126` WHERE `f_2` = 1", actual);
    }

    @Test
    void singleDatasourceQueryShouldRemoveAliasWhenConfigurationIsEmpty() {
        DatasourceSchemaDTO datasource = datasource("obMysql", "s_a_1", "");

        String sql = "SELECT * FROM `s_a_1`.`dataset_sync_126`";
        String actual = Utils.replaceSchemaAlias(sql, dsMap(datasource));

        assertEquals("SELECT * FROM `dataset_sync_126`", actual);
    }

    @Test
    void singleDatasourceQueryShouldRemoveAliasWhenSchemaIsBlank() {
        DatasourceSchemaDTO datasource = datasource("obOracle", "s_a_1", "{\"schema\":\" \"}");

        String sql = "SELECT * FROM \"s_a_1\".\"dataset_sync_126\"";
        String actual = Utils.replaceSchemaAlias(sql, dsMap(datasource));

        assertEquals("SELECT * FROM \"dataset_sync_126\"", actual);
    }

    private DatasourceSchemaDTO datasource(String type, String schemaAlias, String configuration) {
        DatasourceSchemaDTO datasource = new DatasourceSchemaDTO();
        datasource.setId(1L);
        datasource.setType(type);
        datasource.setSchemaAlias(schemaAlias);
        datasource.setConfiguration(configuration);
        return datasource;
    }

    private DatasetTableFieldDTO field(String name) {
        DatasetTableFieldDTO field = new DatasetTableFieldDTO();
        field.setChecked(true);
        field.setExtField(ExtFieldConstant.EXT_NORMAL);
        field.setEngineFieldName(name);
        return field;
    }

    private Map<Long, DatasourceSchemaDTO> dsMap(DatasourceSchemaDTO datasource) {
        Map<Long, DatasourceSchemaDTO> map = new LinkedHashMap<>();
        map.put(datasource.getId(), datasource);
        return map;
    }
}
