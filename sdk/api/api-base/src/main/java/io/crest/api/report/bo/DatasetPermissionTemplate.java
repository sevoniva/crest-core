package io.crest.api.report.bo;

import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Set;

@Data
// 定义模块接口契约和数据传输结构
public class DatasetPermissionTemplate {

    private Long datasetId;

    private Set<Long> dsIdSet;

    private List<TableSysVariable> tableSysVariables;

    public boolean hasSysVariable() {
        return CollectionUtils.isNotEmpty(tableSysVariables);
    }
}
