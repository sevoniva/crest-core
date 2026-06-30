package io.crest.dataset.sync;

import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.constant.SQLConstants;
import io.crest.dataset.dao.auto.entity.CoreDatasetSyncTask;
import io.crest.datasource.dao.auto.entity.CoreDatasource;
import io.crest.datasource.manage.EngineManage;
import io.crest.engine.constant.ExtFieldConstant;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import io.crest.utils.BeanUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
// 在数据集缓存可用时，将查询路由到本地抽取引擎缓存表
public class DatasetSyncQueryManage {

    @Resource
    private EngineManage engineManage;
    @Resource
    private DatasetSyncTaskManage taskManage;

    @SuppressWarnings("unchecked")
    // 缓存未开启、结构不匹配或缓存表不存在时，保持原始实时查询路径
    public Map<String, Object> routeIfSynced(DatasetGroupInfoDTO dataset, Map<String, Object> sqlMap) {
        if (!DatasetSyncUtils.shouldRouteToCache(dataset, sqlMap)) {
            return sqlMap;
        }
        List<DatasetTableFieldDTO> fields = (List<DatasetTableFieldDTO>) sqlMap.get("field");
        if (ObjectUtils.isEmpty(fields)) {
            return sqlMap;
        }
        boolean hasRoutableField = fields.stream()
                .filter(field -> Objects.equals(field.getChecked(), true))
                .filter(field -> Objects.equals(field.getExtField(), ExtFieldConstant.EXT_NORMAL))
                .map(field -> StringUtils.defaultIfBlank(field.getEngineFieldName(), field.getFieldShortName()))
                .anyMatch(StringUtils::isNotBlank);
        if (!hasRoutableField) {
            return sqlMap;
        }
        CoreDatasetSyncTask task = taskManage.selectByDatasetGroupId(dataset.getId());
        if (!DatasetSyncUtils.isCacheReady(task, DatasetSyncUtils.schemaHash(fields))) {
            return sqlMap;
        }
        if (!taskManage.cacheTableExists(dataset.getId())) {
            taskManage.markCacheUnavailable(dataset.getId(), "缓存表不可访问，已回退实时查询");
            return sqlMap;
        }

        CoreDatasource engine = engineManage.getEngineDatasource();
        DatasourceSchemaDTO datasource = new DatasourceSchemaDTO();
        BeanUtils.copyBean(datasource, engine);
        datasource.setSchemaAlias(String.format(SQLConstants.SCHEMA, datasource.getId()));

        DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(datasource.getType());
        Map<Long, DatasourceSchemaDTO> dsMap = new LinkedHashMap<>();
        dsMap.put(datasource.getId(), datasource);

        Map<String, Object> routed = new LinkedHashMap<>(sqlMap);
        routed.put("sql", DatasetSyncUtils.buildCacheSelectSql(
                dataset.getId(),
                fields,
                datasourceType.getPrefix(),
                datasourceType.getSuffix(),
                datasource.getSchemaAlias()
        ));
        routed.put("dsMap", dsMap);
        routed.put("isFullJoin", false);
        return routed;
    }
}
