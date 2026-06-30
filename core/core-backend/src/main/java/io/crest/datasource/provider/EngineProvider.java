package io.crest.datasource.provider;

import io.crest.datasource.dao.auto.entity.CoreEngine;
import io.crest.datasource.request.EngineRequest;
import io.crest.datasource.server.DatasourceServer;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.TableField;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

import static io.crest.engine.utils.Utils.SQL_INJECTION_PATTERNS;

/**
 * 定义不同数据引擎需要提供的 SQL 生成能力
 */
public abstract class EngineProvider {
    /**
     * 生成创建视图的 SQL
     */
    public abstract String createView(String name, String viewSQL);

    /**
     * 生成删除物理表的 SQL
     */
    public abstract String dropTable(String name);

    /**
     * 生成删除视图的 SQL
     */
    public abstract String dropView(String name);

    /**
     * 生成替换物理表的 SQL
     */
    public abstract String replaceTable(String name);

    /**
     * 生成创建物理表的 SQL
     */
    public abstract String createTableSql(String name, List<TableField> tableFields, CoreEngine engine);

    /**
     * 生成向物理表写入抽取数据的 SQL
     */
    public abstract String insertSql(String dsType, String tableName, DatasourceServer.UpdateType extractType, List<String[]> dataList, int page, int pageNumber, List<TableField> tableFields);

    /**
     * 获取字段在物理引擎中的实际字段名
     */
    protected static String physicalFieldName(TableField field) {
        return StringUtils.defaultIfBlank(field.getDbFieldName(), field.getName());
    }

    /**
     * 校验动态对象名称中是否包含高风险 SQL 注入片段
     */
    public static void validateSqlInjectionRisk(String value) {
        String normalized = StringUtils.defaultString(value);
        if (StringUtils.isEmpty(normalized)) {
            return;
        }
        for (Pattern pattern : SQL_INJECTION_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                CrestException.throwException("Illegal table name");
            }
        }
    }
}
