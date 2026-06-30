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

@Data
@EqualsAndHashCode(callSuper = false)
@SuppressWarnings("deprecation")
// 定义数据源连接参数和元数据匹配规则
public class Mysql extends DatasourceConfiguration {
    private String driver = "com.mysql.cj.jdbc.Driver";
    private String extraParams = "characterEncoding=UTF-8&connectTimeout=5000&useSSL=false&allowPublicKeyRetrieval=true&zeroDateTimeBehavior=convertToNull";
    @JsonIgnore
    private List<String> illegalParameters = Arrays.asList("maxAllowedPacket", "autoDeserialize", "queryInterceptors", "statementInterceptors", "detectCustomCollations", "allowloadlocalinfile", "allowUrlInLocalInfile", "allowLoadLocalInfileInPath", "allowMultiQueries");
    private List<String> showTableSqls = Arrays.asList("show tables");

    // 构建当前数据源的连接地址
    @Override
    public String getJdbc() {
        if (StringUtils.isNoneEmpty(getUrlType()) && !getUrlType().equalsIgnoreCase("hostName")) {
            for (String illegalParameter : illegalParameters) {
                if (URLDecoder.decode(getJdbcUrl()).toLowerCase().contains(illegalParameter.toLowerCase()) || URLDecoder.decode(getExtraParams()).contains(illegalParameter.toLowerCase())) {
                    CrestException.throwException("Illegal parameter: " + illegalParameter);
                }
            }
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
            if (URLDecoder.decode(jdbcUrl).toLowerCase().contains(illegalParameter.toLowerCase()) || URLDecoder.decode(jdbcUrl).contains(illegalParameter.toLowerCase())) {
                CrestException.throwException("Illegal parameter: " + illegalParameter);
            }
        }
        return jdbcUrl;
    }
}
