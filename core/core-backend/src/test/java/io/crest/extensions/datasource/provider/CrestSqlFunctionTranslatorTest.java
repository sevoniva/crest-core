package io.crest.extensions.datasource.provider;

import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrestSqlFunctionTranslatorTest {

    @Test
    @DisplayName("MySQL 兼容数据源翻译 Crest 内置日期函数")
    void translateShouldRewriteCrestDateFunctionsForMysql() {
        DatasourceSchemaDTO datasource = new DatasourceSchemaDTO();
        datasource.setType("mysql");

        String sql = "select CREST_DATE_FORMAT(CREST_STR_TO_DATE(`orders`.`day`, 'yyyy-MM-dd'), 'yyyy-MM') "
                + "from `orders` "
                + "where CREST_UNIX_TIMESTAMP(CREST_FROM_UNIXTIME(`orders`.`ts`, 'yyyy-MM-dd HH:mm:ss')) > 0";

        String translated = CrestSqlFunctionTranslator.translate(sql, datasource);

        assertThat(translated).contains("DATE_FORMAT(STR_TO_DATE(`orders`.`day`, '%Y-%m-%d'), '%Y-%m')");
        assertThat(translated).contains("UNIX_TIMESTAMP(FROM_UNIXTIME(`orders`.`ts`, '%Y-%m-%d %H:%i:%s'))");
        assertThat(translated).doesNotContain("CREST_");
        assertThat(translated).doesNotContain("DE_");
    }

    @Test
    @DisplayName("非 MySQL 兼容数据源保持 SQL 不变")
    void translateShouldSkipNonMysqlFamilyDatasource() {
        DatasourceSchemaDTO datasource = new DatasourceSchemaDTO();
        datasource.setType("oracle");
        String sql = "select CREST_DATE_FORMAT(created_at, 'yyyy-MM-dd') from orders";

        assertThat(CrestSqlFunctionTranslator.translate(sql, datasource)).isEqualTo(sql);
    }
}
