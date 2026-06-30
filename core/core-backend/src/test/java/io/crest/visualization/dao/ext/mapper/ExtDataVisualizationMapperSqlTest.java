package io.crest.visualization.dao.ext.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExtDataVisualizationMapperSqlTest {

    @Test
    void recentResourceQueryHasObOracleTypeAndReservedColumnGuards() throws Exception {
        String mapperXml = readMapperXml();

        assertThat(mapperXml)
                .contains("<when test=\"_databaseId == 'ob-oracle'\">")
                .contains("CAST(core_dataset.id AS NUMBER(19,0)) AS id")
                .contains("CAST(TO_NUMBER(core_visualization.id) AS NUMBER(19,0)) AS resource_id")
                .contains("CASE WHEN EXISTS (")
                .contains("wrr.\"UID\" = #{uid}")
                .contains("TRIM(dvResource.name) IS NOT NULL")
                .contains("order by wrr.\"TIME\" desc");
    }

    @Test
    void mybatisBuildsObOracleRecentResourceSql() {
        Configuration configuration = parseMapper("ob-oracle");
        BoundSql boundSql = configuration
                .getMappedStatement(ExtDataVisualizationMapper.class.getName() + ".findRecent")
                .getBoundSql(Map.of(
                        "uid", 1L,
                        "keyword", "DevOps",
                        "ew", Map.of("type", "dataset", "isAsc", false)
                ));

        assertThat(boundSql.getSql())
                .contains("CAST(core_dataset.id AS NUMBER(19,0)) AS id")
                .contains("CASE WHEN EXISTS")
                .contains("wrr.\"UID\" = ?")
                .contains("TRIM(dvResource.name) IS NOT NULL")
                .contains("LOWER('%' || ? || '%')")
                .contains("order by wrr.\"TIME\" desc");
    }

    @Test
    void mybatisBuildsDefaultRecentResourceSql() {
        Configuration configuration = parseMapper("mysql");
        BoundSql boundSql = configuration
                .getMappedStatement(ExtDataVisualizationMapper.class.getName() + ".findRecent")
                .getBoundSql(Map.of(
                        "uid", 1L,
                        "keyword", "DevOps",
                        "ew", Map.of("type", "dataset", "isAsc", false)
                ));

        assertThat(boundSql.getSql())
                .contains("core_visualization.`mobile_layout` as ext_flag")
                .contains("core_workspace_recent_resource.uid = ?")
                .contains("LOWER(CONCAT('%', ?, '%'))")
                .contains("order by core_workspace_recent_resource.time desc")
                .doesNotContain("wrr.\"UID\"");
    }

    private String readMapperXml() throws IOException, URISyntaxException {
        return Files.readString(Path.of(
                getClass().getResource("/mybatis/ExtDataVisualizationMapper.xml").toURI()
        ));
    }

    private Configuration parseMapper(String databaseId) {
        Configuration configuration = new Configuration();
        configuration.setDatabaseId(databaseId);
        try (InputStream inputStream = getClass().getResourceAsStream("/mybatis/ExtDataVisualizationMapper.xml")) {
            new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    "mybatis/ExtDataVisualizationMapper.xml",
                    configuration.getSqlFragments()
            ).parse();
            return configuration;
        } catch (IOException e) {
            throw new AssertionError("Cannot read ExtDataVisualizationMapper.xml", e);
        }
    }
}
