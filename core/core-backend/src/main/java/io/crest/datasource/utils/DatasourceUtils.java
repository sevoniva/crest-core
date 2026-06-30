package io.crest.datasource.utils;

import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import io.crest.i18n.Translator;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

// 数据源运行状态校验工具
public class DatasourceUtils {

    // 任一参与查询的数据源被删除或处于错误状态时，提前阻断查询
    public static void checkDsStatus(Map<Long, DatasourceSchemaDTO> dsMap) {
        if (ObjectUtils.isEmpty(dsMap)) {
            CrestException.throwException(Translator.get("i18n_datasource_delete"));
        }
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            DatasourceSchemaDTO ds = next.getValue();
            if (StringUtils.isNotEmpty(ds.getStatus()) && "Error".equalsIgnoreCase(ds.getStatus())) {
                CrestException.throwException(Translator.get("i18n_invalid_ds"));
            }
        }
    }
}
