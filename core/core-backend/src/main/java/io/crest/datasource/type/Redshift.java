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
import java.util.Map;
import java.util.regex.Pattern;

import static java.awt.SystemColor.info;

@Data
@EqualsAndHashCode(callSuper = false)
@SuppressWarnings("deprecation")
// 定义数据源连接参数和元数据匹配规则
public class Redshift extends DatasourceConfiguration {
    private String driver = "com.amazon.redshift.jdbc42.Driver";
    private String extraParams = "";
    @JsonIgnore
    private List<String> illegalParameters = Arrays.asList("socketFactory", "socketFactoryArg", "sslfactory", "sslhostnameverifier", "sslpasswordcallback", "authenticationPluginClassName", "IniFile");

    // 构建当前数据源的连接地址
    @Override
    public String getJdbc() {
        if (StringUtils.isNoneEmpty(getUrlType()) && !getUrlType().equalsIgnoreCase("hostName")) {
            for (String illegalParameter : illegalParameters) {
                if (URLDecoder.decode(getJdbcUrl()).contains(illegalParameter)) {
                    CrestException.throwException("Illegal parameter: " + illegalParameter);
                }
            }
            if (!getJdbcUrl().startsWith("jdbc:redshift")) {
                CrestException.throwException("Illegal jdbcUrl: " + getJdbcUrl());
            }
            return getJdbcUrl();
        }
        String jdbcUrl = "jdbc:redshift://HOSTNAME:PORT/DATABASE"
                .replace("HOSTNAME", getLHost().trim())
                .replace("PORT", getLPort().toString().trim())
                .replace("DATABASE", getDataBase().trim());
        for (String illegalParameter : illegalParameters) {
            if (URLDecoder.decode(jdbcUrl).contains(illegalParameter)) {
                CrestException.throwException("Illegal parameter: " + illegalParameter);
            }
        }
        return jdbcUrl;
    }

    private static final Pattern DB_NAME_PATTERN = Pattern.compile("//[^/]+/([^?]+)");

    @Override
    // 返回数据库名称解析使用的匹配规则
    protected Pattern databasePattern() {
        return DB_NAME_PATTERN;
    }

    @Override
    // 转换输入数据并返回目标格式
    protected void convertParameters() {
        Map<String, String> parameters = getParameters();
        if (parameters.containsKey("UID")) {
            setUsername(parameters.get("UID"));
        }
        if (parameters.containsKey("PWD")) {
            setPassword(parameters.get("PWD"));
        }
    }
}
