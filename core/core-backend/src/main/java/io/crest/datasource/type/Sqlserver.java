package io.crest.datasource.type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

import java.net.URLDecoder;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Data
@EqualsAndHashCode(callSuper = false)
@SuppressWarnings("deprecation")
// 定义数据源连接参数和元数据匹配规则
public class Sqlserver extends DatasourceConfiguration {
    private String driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private String extraParams = "";
    @JsonIgnore
    private List<String> illegalParameters = Arrays.asList("autoDeserialize", "queryInterceptors", "statementInterceptors", "detectCustomCollations", "jndi:", "rmi:", "ldap:", "ldaps:", "java.naming.factory.initial");
    private List<String> showTableSqls = Arrays.asList("show tables");

    // 构建当前数据源的连接地址
    @Override
    public String getJdbc() {
        if (StringUtils.isNoneEmpty(getUrlType()) && !getUrlType().equalsIgnoreCase("hostName")) {
            if (!getJdbcUrl().startsWith("jdbc:sqlserver")) {
                CrestException.throwException("Illegal jdbcUrl: " + getJdbcUrl());
            }
            return getJdbcUrl();
        }
        String jdbcUrl = "";
        if (StringUtils.isEmpty(extraParams.trim())) {
            jdbcUrl = "jdbc:sqlserver://HOSTNAME:PORT;DatabaseName=DATABASE"
                    .replace("HOSTNAME", getLHost().trim())
                    .replace("PORT", getLPort().toString().trim())
                    .replace("DATABASE", getDataBase().trim());
        } else {
            jdbcUrl = "jdbc:sqlserver://HOSTNAME:PORT;DatabaseName=DATABASE;EXTRA_PARAMS"
                    .replace("HOSTNAME", getLHost().trim())
                    .replace("PORT", getLPort().toString().trim())
                    .replace("DATABASE", getDataBase().trim())
                    .replace("EXTRA_PARAMS", getExtraParams().trim());
        }
        for (String illegalParameter : illegalParameters) {
            if (URLDecoder.decode(jdbcUrl).toLowerCase().contains(illegalParameter.toLowerCase()) || URLDecoder.decode(jdbcUrl).contains(illegalParameter.toLowerCase())) {
                CrestException.throwException("Illegal parameter: " + illegalParameter);
            }
        }
        return jdbcUrl;
    }

    private static final Pattern DB_NAME_PATTERN = Pattern.compile(";databaseName=([^;]+)");

    @Override
    // 返回数据库名称解析使用的匹配规则
    protected Pattern databasePattern() {
        return DB_NAME_PATTERN;
    }

}
