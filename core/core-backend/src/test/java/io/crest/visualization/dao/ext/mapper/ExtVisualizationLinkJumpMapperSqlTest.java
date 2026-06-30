package io.crest.visualization.dao.ext.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExtVisualizationLinkJumpMapperSqlTest {

    @Test
    void mybatisBuildsSnapshotViewTableSql() {
        Configuration configuration = parseMapper("mysql");
        BoundSql boundSql = configuration
                .getMappedStatement(ExtVisualizationLinkJumpMapper.class.getName() + ".getViewTableDetailsSnapshot")
                .getBoundSql(Map.of("dvId", 1L));

        assertThat(boundSql.getSql())
                .contains("core_chart_view_snapshot")
                .contains("core_visualization_snapshot")
                .contains("core_dataset_field")
                .doesNotContain("LOCATE")
                .doesNotContain("INSTR");
    }

    @Test
    void obOracleViewTableSqlDoesNotUseMysqlIdentifierQuoting() {
        Configuration configuration = parseMapper("ob-oracle");
        BoundSql snapshotSql = configuration
                .getMappedStatement(ExtVisualizationLinkJumpMapper.class.getName() + ".getViewTableDetailsSnapshot")
                .getBoundSql(Map.of("dvId", 1L));
        BoundSql editSql = configuration
                .getMappedStatement(ExtVisualizationLinkJumpMapper.class.getName() + ".getViewTableDetails")
                .getBoundSql(Map.of("dvId", 1L));

        assertThat(snapshotSql.getSql()).doesNotContain("`");
        assertThat(editSql.getSql()).doesNotContain("`");
    }

    @Test
    void mybatisBuildsSnapshotOutParamsSql() {
        Configuration configuration = parseMapper("mysql");
        BoundSql boundSql = configuration
                .getMappedStatement(ExtVisualizationLinkJumpMapper.class.getName() + ".queryOutParamsTargetWithDvIdSnapshot")
                .getBoundSql(Map.of("dvId", 1L));

        assertThat(boundSql.getSql())
                .contains("core_visualization_parameter_item_snapshot")
                .contains("core_visualization_parameter_snapshot")
                .contains("vop.visualization_id = ?");
    }

    private Configuration parseMapper(String databaseId) {
        Configuration configuration = new Configuration();
        configuration.setDatabaseId(databaseId);
        try (InputStream inputStream = getClass().getResourceAsStream("/mybatis/ExtVisualizationLinkJumpMapper.xml")) {
            new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    "mybatis/ExtVisualizationLinkJumpMapper.xml",
                    configuration.getSqlFragments()
            ).parse();
            return configuration;
        } catch (IOException e) {
            throw new AssertionError("Cannot read ExtVisualizationLinkJumpMapper.xml", e);
        }
    }
}
