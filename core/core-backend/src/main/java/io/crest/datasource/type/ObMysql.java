package io.crest.datasource.type;

import io.crest.datasource.security.JdbcUrlSecurityPolicy;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.Arrays;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@SuppressWarnings("deprecation")
// 定义 OceanBase MySQL 模式数据源连接参数和元数据匹配规则
public class ObMysql extends DatasourceConfiguration {
    private String driver = "com.oceanbase.jdbc.Driver";
    private String extraParams = "characterEncoding=UTF-8&connectTimeout=5000&useSSL=false&allowPublicKeyRetrieval=true&zeroDateTimeBehavior=convertToNull";
    private List<String> showTableSqls = Arrays.asList("show tables");

    // 构建当前数据源的连接地址
    @Override
    public String getJdbc() {
        if (StringUtils.isNoneEmpty(getUrlType()) && !Strings.CI.equals(getUrlType(), "hostName")) {
            validateJdbcUrl(getJdbcUrl());
            return getJdbcUrl();
        }
        String jdbcUrl = "jdbc:oceanbase://HOSTNAME:PORT/DATABASE"
                .replace("HOSTNAME", getLHost().trim())
                .replace("PORT", getLPort().toString().trim())
                .replace("DATABASE", getDataBase().trim());
        if (StringUtils.isNotBlank(getExtraParams())) {
            jdbcUrl = jdbcUrl + "?" + getExtraParams().trim();
        }
        validateJdbcUrl(jdbcUrl);
        return jdbcUrl;
    }

    // 校验 JDBC URL 和危险参数
    private void validateJdbcUrl(String jdbcUrl) {
        JdbcUrlSecurityPolicy.validate(DatasourceType.obMysql.getType(), jdbcUrl, getExtraParams());
    }
}
