package io.crest.datasource.provider;

import io.crest.utils.CommonBeanFactory;
import io.micrometer.common.util.StringUtils;

// 按抽取引擎类型查找对应 SQL provider
public class ProviderUtil {


    // 未配置引擎类型时按 Crest Core 默认的 OB Oracle 抽取引擎处理
    public static EngineProvider getEngineProvider(String datasourceType) {
        if (StringUtils.isNotEmpty(datasourceType)) {
            return (EngineProvider) CommonBeanFactory.getBean(datasourceType + "Engine");
        } else {
            return CommonBeanFactory.getBean(ObOracleEngineProvider.class);
        }
    }

}
