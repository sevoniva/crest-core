package io.crest.datasource.type;

import io.crest.datasource.security.JdbcUrlSecurityPolicy;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Data
@EqualsAndHashCode(callSuper = false)
@Component("obOracle")
@SuppressWarnings("deprecation")
// 定义数据源连接参数和元数据匹配规则
public class ObOracle extends DatasourceConfiguration {
    private String driver = "com.oceanbase.jdbc.Driver";
    private String extraParams = "";
    private Boolean readOnly = true;

    // 构建当前数据源的连接地址
    @Override
    public String getJdbc() {
        if (StringUtils.isNoneEmpty(getUrlType()) && !Strings.CI.equals(getUrlType(), "hostName")) {
            validateIllegalParameters(getJdbcUrl());
            return getJdbcUrl();
        }

        String jdbcUrl = "jdbc:oceanbase://HOSTNAME:PORT"
                .replace("HOSTNAME", getLHost().trim())
                .replace("PORT", getLPort().toString().trim());
        if (StringUtils.isNotBlank(getExtraParams())) {
            jdbcUrl = jdbcUrl + "?" + getExtraParams().trim();
        }
        validateIllegalParameters(jdbcUrl);
        return jdbcUrl;
    }

    @Override
    // 解析当前数据源的默认模式名
    public String getSchema() {
        String schema = super.getSchema();
        if (StringUtils.isNotBlank(schema)) {
            return schema.trim();
        }
        String username = StringUtils.defaultString(getUsername()).trim();
        if (StringUtils.isBlank(username)) {
            return schema;
        }
        int atIndex = username.indexOf("@");
        int clusterIndex = username.indexOf("#");
        int endIndex = username.length();
        if (atIndex >= 0) {
            endIndex = Math.min(endIndex, atIndex);
        }
        if (clusterIndex >= 0) {
            endIndex = Math.min(endIndex, clusterIndex);
        }
        return username.substring(0, endIndex).toUpperCase(Locale.ROOT);
    }

    // 格式化日期时间并返回统一展示值
    private void validateIllegalParameters(String value) {
        JdbcUrlSecurityPolicy.validate(DatasourceType.obOracle.getType(), value, getExtraParams());
    }
}
