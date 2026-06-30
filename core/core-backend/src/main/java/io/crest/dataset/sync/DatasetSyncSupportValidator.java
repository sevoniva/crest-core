package io.crest.dataset.sync;

import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.dataset.manage.DatasetGroupManage;
import io.crest.dataset.manage.DatasetSQLManage;
import io.crest.engine.constant.ExtFieldConstant;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 数据集缓存支持性校验，统一保存、手动执行和后台同步的准入规则
 */
@Component
public class DatasetSyncSupportValidator {

    @Resource
    private DatasetGroupManage datasetGroupManage;
    @Resource
    private DatasetSQLManage datasetSQLManage;

    /**
     * 校验数据集是否可以进入缓存同步，并返回后续同步需要的 SQL 上下文
     */
    @SuppressWarnings("unchecked")
    public SupportContext assertSupported(Long datasetGroupId) throws Exception {
        DatasetGroupInfoDTO dataset = datasetGroupManage.getForCount(datasetGroupId);
        if (dataset == null || !Strings.CI.equals(dataset.getNodeType(), "dataset")) {
            CrestException.throwException("数据集不存在");
        }
        if (!Objects.equals(dataset.getMode(), 1)) {
            CrestException.throwException("请先开启数据集同步模式");
        }
        if (Objects.equals(dataset.getIsCross(), true)) {
            CrestException.throwException("数据集缓存暂不支持跨源数据集");
        }

        Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(dataset, null);
        if (sqlMap == null) {
            CrestException.throwException("数据集 SQL 生成失败，暂不支持缓存");
        }
        Object sql = sqlMap.get("sql");
        if (!(sql instanceof String sqlText) || StringUtils.isBlank(sqlText)) {
            CrestException.throwException("数据集 SQL 为空，暂不支持缓存");
        }
        if (DatasetSyncUtils.hasSqlParameters(dataset, sqlMap)) {
            CrestException.throwException("带 SQL 参数或未绑定占位符的数据集暂不支持缓存");
        }
        Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        if (dsMap == null || dsMap.size() != 1) {
            CrestException.throwException("数据集缓存仅支持单一数据源");
        }
        DatasourceSchemaDTO datasource = dsMap.entrySet().iterator().next().getValue();
        if (!DatasetSyncUtils.isSupportedCacheSource(datasource.getType())) {
            CrestException.throwException("当前缓存能力仅支持 OceanBase Oracle 数据源");
        }

        List<DatasetTableFieldDTO> checkedFields = (List<DatasetTableFieldDTO>) sqlMap.get("field");
        if (checkedFields == null) {
            checkedFields = Collections.emptyList();
        }
        List<DatasetTableFieldDTO> syncFields = checkedFields.stream()
                .filter(field -> Objects.equals(field.getChecked(), true))
                .filter(field -> Objects.equals(field.getExtField(), ExtFieldConstant.EXT_NORMAL))
                .toList();
        if (syncFields.isEmpty()) {
            CrestException.throwException("数据集没有可同步字段");
        }
        return new SupportContext(dataset, sqlMap, dsMap, checkedFields, syncFields);
    }

    /**
     * 缓存同步准入后的上下文
     */
    public record SupportContext(DatasetGroupInfoDTO dataset,
                                 Map<String, Object> sqlMap,
                                 Map<Long, DatasourceSchemaDTO> dsMap,
                                 List<DatasetTableFieldDTO> checkedFields,
                                 List<DatasetTableFieldDTO> syncFields) {
    }
}
