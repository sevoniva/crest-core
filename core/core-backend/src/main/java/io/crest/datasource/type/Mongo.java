package io.crest.datasource.type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
// 定义数据源连接参数和元数据匹配规则
public class Mongo extends DatasourceConfiguration {
    private String driver = "com.mysql.cj.jdbc.Driver";
    private String extraParams = "characterEncoding=UTF-8&connectTimeout=5000&useSSL=false&allowPublicKeyRetrieval=true&zeroDateTimeBehavior=convertToNull";
    @JsonIgnore
    private List<String> illegalParameters = Arrays.asList("autoDeserialize", "queryInterceptors", "statementInterceptors", "detectCustomCollations");
    private List<String> showTableSqls = Arrays.asList("show tables");

    // 构建当前数据源的连接地址
    @Override
    public String getJdbc() {
        if (StringUtils.isNoneEmpty(getUrlType()) && !getUrlType().equalsIgnoreCase("hostName")) {
            if (!getJdbcUrl().startsWith("jdbc:mysql")) {
                CrestException.throwException("Illegal jdbcUrl: " + getJdbcUrl());
            }
            return getJdbcUrl();
        }
        String jdbcUrl = "";
        if (StringUtils.isEmpty(extraParams.trim())) {
            jdbcUrl = "jdbc:mysql://HOSTNAME:PORT/DATABASE"
                    .replace("HOSTNAME", getLHost().trim())
                    .replace("PORT", getLPort().toString().trim())
                    .replace("DATABASE", getDataBase().trim());
        } else {
            jdbcUrl = "jdbc:mysql://HOSTNAME:PORT/DATABASE?EXTRA_PARAMS"
                    .replace("HOSTNAME", getLHost().trim())
                    .replace("PORT", getLPort().toString().trim())
                    .replace("DATABASE", getDataBase().trim())
                    .replace("EXTRA_PARAMS", getExtraParams().trim());
        }
        for (String illegalParameter : illegalParameters) {
            if (jdbcUrl.contains(illegalParameter)) {
                throw new RuntimeException("Illegal parameter: " + illegalParameter);
            }
        }
        return jdbcUrl;
    }
}
