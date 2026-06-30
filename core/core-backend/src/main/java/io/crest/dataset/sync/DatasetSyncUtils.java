package io.crest.dataset.sync;

import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.api.dataset.union.UnionDTO;
import io.crest.commons.constants.TaskStatus;
import io.crest.dataset.dao.auto.entity.CoreDatasetSyncTask;
import io.crest.engine.constant.ExtFieldConstant;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasetTableDTO;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import io.crest.extensions.datasource.dto.TableField;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据集同步缓存工具类，集中维护缓存表命名、任务状态判断、增量水位和分页 SQL 生成规则
 */
public class DatasetSyncUtils {

    /**
     * 缓存表名前缀，后续追加数据集分组编号形成稳定物理表名
     */
    public static final String CACHE_TABLE_PREFIX = "dataset_sync_";
    /**
     * 增量任务默认全量校准间隔，避免长期只增量导致缓存漂移
     */
    public static final int DEFAULT_FULL_SYNC_INTERVAL_HOURS = 24;
    /**
     * 缓存默认过期时长，略大于默认全量同步间隔以覆盖调度抖动
     */
    public static final int DEFAULT_CACHE_EXPIRE_HOURS = 26;
    /**
     * 单次同步任务默认超时时间，防止执行线程长期占用
     */
    public static final int DEFAULT_TASK_TIMEOUT_MINUTES = 360;
    /**
     * 连续失败告警默认阈值，首次失败即可进入告警判断
     */
    public static final int DEFAULT_FAILURE_WARN_THRESHOLD = 1;
    /**
     * 当前可路由到缓存的源库类型，分页和水位 SQL 只覆盖这些方言
     */
    private static final Set<String> SUPPORTED_CACHE_SOURCE_TYPES = Set.of(
            DatasourceConfiguration.DatasourceType.obOracle.name(),
            DatasourceConfiguration.DatasourceType.mysql.name(),
            DatasourceConfiguration.DatasourceType.obMysql.name()
    );

    /**
     * 工具类不允许实例化，所有行为都通过静态方法暴露
     */
    private DatasetSyncUtils() {
    }

    /**
     * 根据数据集分组编号生成正式缓存表名
     */
    public static String cacheTableName(Long datasetGroupId) {
        return CACHE_TABLE_PREFIX + datasetGroupId;
    }

    /**
     * 根据数据集分组编号生成临时缓存表名，用于全量重建期间承接数据
     */
    public static String tmpCacheTableName(Long datasetGroupId) {
        return "tmp_" + cacheTableName(datasetGroupId);
    }

    /**
     * 判断查询是否可以直接路由到缓存表，只有单源、非跨源、无 SQL 参数且源库受支持时才允许
     */
    public static boolean shouldRouteToCache(DatasetGroupInfoDTO dataset, Map<String, Object> sqlMap) {
        if (dataset == null || sqlMap == null || !Objects.equals(dataset.getMode(), 1)
                || Objects.equals(dataset.getIsCross(), true)) {
            return false;
        }
        Object sql = sqlMap.get("sql");
        if (!(sql instanceof String sqlText) || StringUtils.isBlank(sqlText)) {
            return false;
        }
        Object dsMapObject = sqlMap.get("dsMap");
        if (!(dsMapObject instanceof Map<?, ?> dsMap) || dsMap.size() != 1) {
            return false;
        }
        if (hasSqlParameters(dataset, sqlMap)) {
            return false;
        }
        Object datasourceObject = dsMap.values().iterator().next();
        if (!(datasourceObject instanceof DatasourceSchemaDTO datasource)) {
            return false;
        }
        return isSupportedCacheSource(datasource.getType());
    }

    /**
     * 判断数据集查询是否包含运行时 SQL 参数，任一来源存在参数都不能走静态缓存
     */
    public static boolean hasSqlParameters(DatasetGroupInfoDTO dataset, Map<String, Object> sqlMap) {
        return hasSqlParameterValues(sqlMap) || hasSqlParameterDefinitions(dataset) || hasSqlParameterMarkers(sqlMap);
    }

    /**
     * 检查执行 SQL 上下文中是否带有实际参数值
     */
    public static boolean hasSqlParameterValues(Map<String, Object> sqlMap) {
        if (sqlMap == null) {
            return false;
        }
        Object tableFieldWithValues = sqlMap.get("tableFieldWithValues");
        if (tableFieldWithValues instanceof Collection<?> values) {
            return !values.isEmpty();
        }
        return tableFieldWithValues != null;
    }

    /**
     * 检查最终 SQL 是否还存在未绑定的 JDBC 参数占位符，字符串和注释中的问号不参与判断
     */
    private static boolean hasSqlParameterMarkers(Map<String, Object> sqlMap) {
        if (sqlMap == null) {
            return false;
        }
        Object sql = sqlMap.get("sql");
        return sql instanceof String value && hasUnboundParameterMarker(value);
    }

    /**
     * 判断 SQL 文本中是否存在裸问号参数占位符
     */
    public static boolean hasUnboundParameterMarker(String sql) {
        if (StringUtils.isEmpty(sql)) {
            return false;
        }
        char quote = 0;
        boolean bracketQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : 0;
            if (quote != 0) {
                if (bracketQuote && current == ']') {
                    quote = 0;
                    bracketQuote = false;
                } else if (current == '\\' && quote != '`' && !bracketQuote) {
                    i++;
                } else if (!bracketQuote && current == quote) {
                    if (next == quote && quote != '`') {
                        i++;
                    } else {
                        quote = 0;
                    }
                }
                continue;
            }
            if (current == '\'' || current == '"' || current == '`') {
                quote = current;
                continue;
            }
            if (current == '[') {
                quote = ']';
                bracketQuote = true;
                continue;
            }
            if (current == '-' && next == '-') {
                i = skipLineComment(sql, i + 2);
                continue;
            }
            if (current == '#') {
                i = skipLineComment(sql, i + 1);
                continue;
            }
            if (current == '/' && next == '*') {
                i = skipBlockComment(sql, i + 2);
                continue;
            }
            if (current == '?') {
                return true;
            }
        }
        return false;
    }

    /**
     * 跳过 SQL 行注释
     */
    private static int skipLineComment(String sql, int start) {
        int lineEnd = sql.indexOf('\n', start);
        return lineEnd < 0 ? sql.length() : lineEnd;
    }

    /**
     * 跳过 SQL 块注释
     */
    private static int skipBlockComment(String sql, int start) {
        int blockEnd = sql.indexOf("*/", start);
        return blockEnd < 0 ? sql.length() : blockEnd + 1;
    }

    /**
     * 检查数据集联合树中是否定义过 SQL 变量
     */
    private static boolean hasSqlParameterDefinitions(DatasetGroupInfoDTO dataset) {
        if (dataset == null || dataset.getUnion() == null) {
            return false;
        }
        return dataset.getUnion().stream().anyMatch(DatasetSyncUtils::hasSqlParameterDefinitions);
    }

    /**
     * 递归检查联合节点及其子节点是否存在 SQL 变量定义
     */
    private static boolean hasSqlParameterDefinitions(UnionDTO union) {
        if (union == null) {
            return false;
        }
        DatasetTableDTO currentDs = union.getCurrentDs();
        if (currentDs != null && hasSqlVariableDefinitions(currentDs.getSqlVariableDetails())) {
            return true;
        }
        List<UnionDTO> children = union.getChildrenDs();
        return children != null && children.stream().anyMatch(DatasetSyncUtils::hasSqlParameterDefinitions);
    }

    /**
     * 判断 SQL 变量定义文本是否包含有效内容，空数组视为未定义
     */
    private static boolean hasSqlVariableDefinitions(String sqlVariableDetails) {
        String value = StringUtils.trimToEmpty(sqlVariableDetails);
        return StringUtils.isNotBlank(value) && !Strings.CS.equals(value, "[]");
    }

    /**
     * 判断源库类型是否支持数据集缓存同步
     */
    public static boolean isSupportedCacheSource(String datasourceType) {
        return SUPPORTED_CACHE_SOURCE_TYPES.stream().anyMatch(type -> Strings.CI.equals(type, datasourceType));
    }

    /**
     * 判断任务是否已经具备可查询缓存
     */
    public static boolean isCacheReady(CoreDatasetSyncTask task) {
        return task != null && Objects.equals(task.getCacheReady(), 1);
    }

    /**
     * 判断任务缓存是否可用且字段结构哈希仍与当前数据集一致
     */
    public static boolean isCacheReady(CoreDatasetSyncTask task, String schemaHash) {
        return isCacheReady(task) && Strings.CS.equals(task.getSchemaHash(), schemaHash);
    }

    /**
     * 判断当前任务是否满足增量同步条件，要求增量字段、上次水位和结构哈希都有效
     */
    public static boolean canRunIncremental(CoreDatasetSyncTask task, String schemaHash) {
        return task != null
                && Strings.CI.equals(task.getUpdateType(), "add_scope")
                && StringUtils.isNotBlank(task.getIncrementalLastValue())
                && isCacheReady(task, schemaHash);
    }

    /**
     * 判断增量任务是否需要周期性全量校准
     */
    public static boolean shouldRunFullCalibration(CoreDatasetSyncTask task, long now) {
        if (task == null || !Strings.CI.equals(task.getUpdateType(), "add_scope")) {
            return false;
        }
        int intervalHours = Objects.requireNonNullElse(task.getFullSyncIntervalHours(), DEFAULT_FULL_SYNC_INTERVAL_HOURS);
        if (intervalHours <= 0) {
            return false;
        }
        Long lastFullSyncTime = task.getLastFullSyncTime();
        if (lastFullSyncTime == null || lastFullSyncTime <= 0) {
            return true;
        }
        return now - lastFullSyncTime >= intervalHours * 60L * 60L * 1000L;
    }

    /**
     * 判断可用缓存是否超过配置的有效期
     */
    public static boolean isCacheExpired(CoreDatasetSyncTask task, long now) {
        if (!isCacheReady(task) || task.getLastExecTime() == null || task.getLastExecTime() <= 0) {
            return false;
        }
        int expireHours = Objects.requireNonNullElse(task.getCacheExpireHours(), DEFAULT_CACHE_EXPIRE_HOURS);
        if (expireHours <= 0) {
            return false;
        }
        return now - task.getLastExecTime() > expireHours * 60L * 60L * 1000L;
    }

    /**
     * 判断任务执行时长是否超过配置超时时间
     */
    public static boolean isTaskTimedOut(CoreDatasetSyncTask task, long startTime, long now) {
        int timeoutMinutes = Objects.requireNonNullElse(task == null ? null : task.getTaskTimeoutMinutes(), DEFAULT_TASK_TIMEOUT_MINUTES);
        return timeoutMinutes > 0 && now - startTime > timeoutMinutes * 60L * 1000L;
    }

    /**
     * 判断连续失败次数是否达到告警阈值
     */
    public static boolean isFailureWarned(CoreDatasetSyncTask task) {
        if (task == null) {
            return false;
        }
        int failures = Objects.requireNonNullElse(task.getConsecutiveFailures(), 0);
        int threshold = Objects.requireNonNullElse(task.getFailureWarnThreshold(), DEFAULT_FAILURE_WARN_THRESHOLD);
        return threshold > 0 && failures >= threshold;
    }

    /**
     * 判断任务是否允许调度执行，停止和暂停状态不进入同步队列
     */
    public static boolean isTaskRunnable(CoreDatasetSyncTask task) {
        return task != null
                && !Strings.CI.equalsAny(task.getTaskStatus(), TaskStatus.Stopped.name(), TaskStatus.Suspend.name());
    }

    /**
     * 对比源端和缓存端的行数、水位，返回同步校验结果
     */
    public static ReconcileResult reconcile(Long sourceRowCount, Long cacheRowCount, String sourceWatermark, String cacheWatermark) {
        if (!Objects.equals(sourceRowCount, cacheRowCount)) {
            return new ReconcileResult("WARNING", "源数据行数 " + sourceRowCount + " 与缓存行数 " + cacheRowCount + " 不一致");
        }
        if (StringUtils.isNotBlank(sourceWatermark) || StringUtils.isNotBlank(cacheWatermark)) {
            if (!Strings.CS.equals(StringUtils.defaultString(sourceWatermark), StringUtils.defaultString(cacheWatermark))) {
                return new ReconcileResult(
                        "WARNING",
                        "源数据最大增量值 " + sourceWatermark + " 与缓存最大增量值 " + cacheWatermark + " 不一致"
                );
            }
        }
        return new ReconcileResult("PASSED", "源数据与缓存一致");
    }

    /**
     * 将数据集字段配置转换为引擎表字段，只保留勾选的普通字段
     */
    public static List<TableField> toEngineTableFields(List<DatasetTableFieldDTO> fields) {
        List<TableField> tableFields = new ArrayList<>();
        if (fields == null) {
            return tableFields;
        }
        for (DatasetTableFieldDTO field : fields) {
            if (!Objects.equals(field.getChecked(), true)) {
                continue;
            }
            if (!Objects.equals(field.getExtField(), ExtFieldConstant.EXT_NORMAL)) {
                continue;
            }
            String columnName = StringUtils.defaultIfBlank(field.getEngineFieldName(), field.getFieldShortName());
            if (StringUtils.isBlank(columnName)) {
                continue;
            }
            TableField tableField = new TableField();
            tableField.setName(columnName);
            tableField.setOriginName(columnName);
            tableField.setNativeType(field.getName());
            tableField.setType(field.getType());
            tableField.setExtractedFieldType(field.getExtractedFieldType());
            tableField.setFieldType(field.getFieldType());
            tableField.setChecked(true);
            tableFields.add(tableField);
        }
        return tableFields;
    }

    /**
     * 构造默认不包含等号的增量过滤条件
     */
    public static String buildIncrementalPredicate(DatasetTableFieldDTO field, String lastValue, String prefix, String suffix) {
        return buildIncrementalPredicate(field, lastValue, prefix, suffix, false);
    }

    /**
     * 构造指定是否包含等号的增量过滤条件，默认按 Oracle 时间表达式处理
     */
    public static String buildIncrementalPredicate(DatasetTableFieldDTO field, String lastValue, String prefix, String suffix, boolean inclusive) {
        return buildIncrementalPredicate(field, lastValue, prefix, suffix, inclusive, DatasourceConfiguration.DatasourceType.obOracle.name());
    }

    /**
     * 按字段类型和源库方言生成增量水位过滤条件
     */
    public static String buildIncrementalPredicate(DatasetTableFieldDTO field, String lastValue, String prefix, String suffix, boolean inclusive, String datasourceType) {
        String column = prefix + StringUtils.defaultIfBlank(field.getEngineFieldName(), field.getFieldShortName()) + suffix;
        String operator = inclusive ? " >= " : " > ";
        if (isTimeField(field)) {
            if (isMysqlCompatible(datasourceType)) {
                return column + operator + "'" + escapeSqlLiteral(lastValue) + "'";
            }
            return column + operator + "TO_TIMESTAMP('" + escapeSqlLiteral(lastValue) + "', 'YYYY-MM-DD HH24:MI:SS.FF')";
        }
        if (isNumericField(field)) {
            return column + operator + lastValue;
        }
        return column + operator + "'" + escapeSqlLiteral(lastValue) + "'";
    }

    /**
     * 针对缓存表生成水位比较条件，数值字段在水位合法时不加字符串引号
     */
    public static String buildCacheWatermarkPredicate(DatasetTableFieldDTO field, String lastValue, String prefix, String suffix, String operator) {
        return buildCacheWatermarkPredicate(field, lastValue, prefix, suffix, operator, null);
    }

    /**
     * 针对指定引擎方言生成缓存表水位比较条件，OB Oracle 时间字段使用显式 TIMESTAMP 字面量
     */
    public static String buildCacheWatermarkPredicate(DatasetTableFieldDTO field, String lastValue, String prefix, String suffix,
                                                      String operator, String datasourceType) {
        String column = prefix + StringUtils.defaultIfBlank(field.getEngineFieldName(), field.getFieldShortName()) + suffix;
        String safeOperator = StringUtils.defaultIfBlank(operator, ">").trim();
        if (isTimeField(field) && Strings.CI.equals(datasourceType, DatasourceConfiguration.DatasourceType.obOracle.getType())) {
            return column + " " + safeOperator + " TO_TIMESTAMP('" + escapeSqlLiteral(lastValue) + "', 'YYYY-MM-DD HH24:MI:SS.FF')";
        }
        if (isNumericField(field) && isNumericLiteral(lastValue)) {
            return column + " " + safeOperator + " " + lastValue;
        }
        return column + " " + safeOperator + " '" + escapeSqlLiteral(lastValue) + "'";
    }

    /**
     * 判断当前水位值是否适配字段类型，数值字段必须持有数值字面量
     */
    public static boolean isWatermarkCompatible(DatasetTableFieldDTO field, String lastValue) {
        if (field == null || StringUtils.isBlank(lastValue)) {
            return false;
        }
        return !isNumericField(field) || isNumericLiteral(lastValue);
    }

    /**
     * 判断字段是否可作为增量水位字段。增量字段只允许普通的时间或数值字段
     */
    public static boolean isIncrementalFieldSupported(DatasetTableFieldDTO field) {
        return field != null
                && Objects.equals(field.getChecked(), true)
                && Objects.equals(field.getExtField(), ExtFieldConstant.EXT_NORMAL)
                && (isTimeField(field) || isNumericField(field));
    }

    /**
     * 判断字段是否按时间类型处理
     */
    private static boolean isTimeField(DatasetTableFieldDTO field) {
        return Objects.equals(field.getExtractedFieldType(), 1) || Objects.equals(field.getFieldType(), 1);
    }

    /**
     * 判断字段是否按数值类型处理
     */
    private static boolean isNumericField(DatasetTableFieldDTO field) {
        return Objects.equals(field.getExtractedFieldType(), 2) || Objects.equals(field.getExtractedFieldType(), 3)
                || Objects.equals(field.getFieldType(), 2) || Objects.equals(field.getFieldType(), 3);
    }

    /**
     * 判断字符串是否为可直接拼入数值比较的字面量
     */
    private static boolean isNumericLiteral(String value) {
        return StringUtils.defaultString(value).trim().matches("-?\\d+(\\.\\d+)?");
    }

    /**
     * 构造从缓存表读取数据的 SELECT 语句，字段顺序沿用当前勾选字段
     */
    public static String buildCacheSelectSql(Long datasetGroupId, List<DatasetTableFieldDTO> fields, String prefix, String suffix, String schemaAlias) {
        String selectFields = fields.stream()
                .filter(field -> Objects.equals(field.getChecked(), true))
                .filter(field -> Objects.equals(field.getExtField(), ExtFieldConstant.EXT_NORMAL))
                .map(field -> StringUtils.defaultIfBlank(field.getEngineFieldName(), field.getFieldShortName()))
                .filter(StringUtils::isNotBlank)
                .map(fieldName -> quote(fieldName, prefix, suffix) + " AS " + quote(fieldName, prefix, suffix))
                .collect(Collectors.joining(","));
        String table = quote(cacheTableName(datasetGroupId), prefix, suffix);
        if (StringUtils.isNotBlank(schemaAlias)) {
            table = quote(schemaAlias, prefix, suffix) + "." + table;
        }
        return "SELECT " + selectFields + " FROM " + table;
    }

    /**
     * 构造 Oracle 分页 SQL，用 ROWNUM 在源 SQL 外层包裹分页范围
     */
    public static String buildOraclePageSql(String sourceSql, int limit, int offset, List<DatasetTableFieldDTO> fields, String prefix, String suffix) {
        String selectFields = fields.stream()
                .filter(field -> Objects.equals(field.getChecked(), true))
                .filter(field -> Objects.equals(field.getExtField(), ExtFieldConstant.EXT_NORMAL))
                .map(field -> StringUtils.defaultIfBlank(field.getEngineFieldName(), field.getFieldShortName()))
                .filter(StringUtils::isNotBlank)
                .map(fieldName -> "SYNC_PAGE." + quote(fieldName, prefix, suffix))
                .collect(Collectors.joining(","));
        return "SELECT " + selectFields
                + " FROM (SELECT SYNC_SRC.*, ROWNUM AS SYNC_ROWNUM FROM (" + sourceSql + ") SYNC_SRC WHERE ROWNUM <= "
                + (offset + limit) + ") SYNC_PAGE WHERE SYNC_ROWNUM > " + offset;
    }

    /**
     * 按源库类型选择分页 SQL 生成策略
     */
    public static String buildSourcePageSql(String datasourceType, String sourceSql, int limit, int offset,
                                            List<DatasetTableFieldDTO> fields, String prefix, String suffix) {
        if (isMysqlCompatible(datasourceType)) {
            return buildMysqlPageSql(sourceSql, limit, offset, fields, prefix, suffix);
        }
        if (Strings.CI.equals(datasourceType, DatasourceConfiguration.DatasourceType.obOracle.name())) {
            return buildOraclePageSql(sourceSql, limit, offset, fields, prefix, suffix);
        }
        throw new IllegalArgumentException("Unsupported dataset cache source type: " + datasourceType);
    }

    /**
     * 判断源库是否使用 MySQL 兼容分页和时间字面量规则
     */
    private static boolean isMysqlCompatible(String datasourceType) {
        return Strings.CI.equalsAny(datasourceType,
                DatasourceConfiguration.DatasourceType.mysql.name(),
                DatasourceConfiguration.DatasourceType.obMysql.name());
    }

    /**
     * 构造 MySQL 分页 SQL，并按导出字段追加稳定排序
     */
    public static String buildMysqlPageSql(String sourceSql, int limit, int offset, List<DatasetTableFieldDTO> fields, String prefix, String suffix) {
        String selectFields = fields.stream()
                .filter(field -> Objects.equals(field.getChecked(), true))
                .filter(field -> Objects.equals(field.getExtField(), ExtFieldConstant.EXT_NORMAL))
                .map(field -> StringUtils.defaultIfBlank(field.getEngineFieldName(), field.getFieldShortName()))
                .filter(StringUtils::isNotBlank)
                .map(fieldName -> "SYNC_PAGE." + quote(fieldName, prefix, suffix))
                .collect(Collectors.joining(","));
        String orderFields = fields.stream()
                .filter(field -> Objects.equals(field.getChecked(), true))
                .filter(field -> Objects.equals(field.getExtField(), ExtFieldConstant.EXT_NORMAL))
                .map(field -> StringUtils.defaultIfBlank(field.getEngineFieldName(), field.getFieldShortName()))
                .filter(StringUtils::isNotBlank)
                .map(fieldName -> "SYNC_PAGE." + quote(fieldName, prefix, suffix))
                .collect(Collectors.joining(","));
        String orderClause = StringUtils.isBlank(orderFields) ? "" : " ORDER BY " + orderFields;
        return "SELECT " + selectFields + " FROM (" + sourceSql + ") SYNC_PAGE" + orderClause
                + " LIMIT " + limit + " OFFSET " + offset;
    }

    /**
     * 使用方言前后缀包裹标识符
     */
    public static String quote(String value, String prefix, String suffix) {
        return prefix + value + suffix;
    }

    /**
     * 生成字段结构哈希，用于判断缓存表结构是否仍匹配当前数据集字段
     */
    public static String schemaHash(List<DatasetTableFieldDTO> fields) {
        String signature = fields.stream()
                .filter(field -> Objects.equals(field.getChecked(), true))
                .filter(field -> Objects.equals(field.getExtField(), ExtFieldConstant.EXT_NORMAL))
                .map(field -> String.join("|",
                        StringUtils.defaultString(field.getEngineFieldName()),
                        StringUtils.defaultString(field.getFieldShortName()),
                        StringUtils.defaultString(field.getName()),
                        StringUtils.defaultString(field.getOriginName()),
                        String.valueOf(field.getExtractedFieldType()),
                        String.valueOf(field.getFieldType())
                ))
                .collect(Collectors.joining("\n"));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(signature.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * 转义 SQL 字符串字面量中的单引号
     */
    public static String escapeSqlLiteral(String value) {
        return StringUtils.defaultString(value).replace("'", "''");
    }

    /**
     * 缓存校验结果，status 表示结果等级，message 面向任务日志展示
     */
    public record ReconcileResult(String status, String message) {
    }
}
