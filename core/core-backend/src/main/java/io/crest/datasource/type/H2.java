package io.crest.datasource.type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@EqualsAndHashCode(callSuper = true)
@Data
// 定义数据源连接参数和元数据匹配规则
public class H2 extends DatasourceConfiguration {
    private String driver = "org.h2.Driver";

    // 构建当前数据源的连接地址
    @Override
    public String getJdbc() {
        for (String illegalParameter : getH2IllegalParameters()) {
            if (jdbc.toUpperCase(Locale.ENGLISH).replace("\\", "").contains(illegalParameter)) {
                CrestException.throwException("Has illegal parameter: " + jdbc);
            }
        }
        if (StringUtils.isNotEmpty(jdbc) && !jdbc.startsWith("jdbc:h2")) {
            CrestException.throwException("Illegal jdbcUrl: " + jdbc);
        }
        return jdbc;
    }

    @JsonIgnore
    private List<String> getH2IllegalParameters() {
        return Arrays.asList("INIT", "RUNSCRIPT");
    }

}
