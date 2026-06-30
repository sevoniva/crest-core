package io.crest.extensions.view.util;

import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Utils {

    // 判断当前类型是否满足业务分类
    public static boolean isNeedOrder(List<String> dsList) {
        String[] list = {"sqlServer", "db2", "impala"};
        List<String> strings = Arrays.asList(list);
        List<String> collect = strings.stream().filter(dsList::contains).collect(Collectors.toList());
        return ObjectUtils.isNotEmpty(collect);
    }

    // 判断当前类型是否满足业务分类
    public static boolean isCrossDs(Map<Long, DatasourceSchemaDTO> dsMap) {
        return dsMap.size() != 1;
    }

}
