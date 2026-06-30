package io.crest.datasource.provider;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jcraft.jsch.*;
import io.crest.constant.SQLConstants;
import io.crest.dataset.utils.FieldUtils;
import io.crest.datasource.dao.auto.entity.CoreDatasource;
import io.crest.datasource.dao.auto.entity.CoreDriver;
import io.crest.datasource.dao.auto.mapper.CoreDatasourceMapper;
import io.crest.datasource.dao.auto.mapper.CoreDriverMapper;
import io.crest.datasource.manage.EngineManage;
import io.crest.datasource.request.EngineRequest;
import io.crest.datasource.security.SqlSecurityPolicy;
import io.crest.datasource.security.JdbcUrlSecurityPolicy;
import io.crest.datasource.type.*;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.*;
import io.crest.extensions.datasource.provider.DriverShim;
import io.crest.extensions.datasource.provider.CrestSqlFunctionTranslator;
import io.crest.extensions.datasource.provider.ExtendedJdbcClassLoader;
import io.crest.extensions.datasource.provider.Provider;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import io.crest.i18n.Translator;
import io.crest.utils.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.apache.calcite.adapter.jdbc.JdbcSchema;
import org.apache.calcite.config.CalciteConnectionProperty;
import org.apache.calcite.config.Lex;
import org.apache.calcite.config.NullCollation;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Calcite 数据源 provider，负责 JDBC 连接、方言转换、表字段探测、跨源查询和引擎连接池维护
 * 该类只处理数据访问和元数据读取，不负责数据源权限、前端参数解码和同步任务编排
 */
@Component("calciteProvider")
@SuppressWarnings({"deprecation", "unchecked"})
public class CalciteProvider extends Provider {

    @Resource
    protected CoreDatasourceMapper coreDatasourceMapper;
    @Resource
    protected CoreDriverMapper coreDriverMapper;
    @Resource
    private EngineManage engineManage;
    protected ExtendedJdbcClassLoader extendedJdbcClassLoader;
    private Map<Long, ExtendedJdbcClassLoader> customJdbcClassLoaders = new HashMap<>();
    @Value("${crest.path.driver:/opt/crest/drivers}")
    private String FILE_PATH;
    @Value("${crest.path.custom-drivers:/opt/crest/custom-drivers/}")
    private String CUSTOM_PATH;

    @Resource
    private CommonThreadPool commonThreadPool;

    /**
     * 判断数据源类型是否使用 Oracle 兼容路径，包含原生 Oracle 和 OceanBase Oracle 模式
     */
    private boolean isOracleLike(String type) {
        return Strings.CI.equalsAny(type,
                DatasourceConfiguration.DatasourceType.oracle.getType(),
                DatasourceConfiguration.DatasourceType.obOracle.getType());
    }

    /**
     * 判断数据源类型是否为 OceanBase Oracle 模式，用于只读连接和兼容 SQL 分支
     */
    private boolean isObOracle(String type) {
        return Strings.CI.equals(type, DatasourceConfiguration.DatasourceType.obOracle.getType());
    }

    /**
     * 判断当前请求是否需要只读连接。显式只读优先，其次使用 OceanBase Oracle 的默认只读策略
     */
    boolean shouldUseReadOnlyConnection(
            DatasourceRequest datasourceRequest,
            DatasourceConfiguration datasourceConfiguration,
            String datasourceType
    ) {
        if (Boolean.TRUE.equals(datasourceRequest.getReadOnly())) {
            return true;
        }
        return isObOracle(datasourceType)
                && datasourceConfiguration instanceof ObOracle obOracle
                && !Boolean.FALSE.equals(obOracle.getReadOnly());
    }

    /**
     * 在执行源库只读查询前开启 JDBC 只读标记，驱动不支持时转为业务异常
     */
    private void setConnectionReadOnly(Connection connection, boolean readOnly) {
        if (!readOnly || connection == null) {
            return;
        }
        try {
            connection.setReadOnly(true);
        } catch (SQLException e) {
            CrestException.throwException("源库只读连接启用失败：" + e.getMessage());
        }
    }

    /**
     * 查询结束后恢复连接只读标记，恢复失败只记录日志，避免覆盖原始查询结果
     */
    private void resetConnectionReadOnly(Connection connection, boolean readOnly) {
        if (!readOnly || connection == null) {
            return;
        }
        try {
            connection.setReadOnly(false);
        } catch (SQLException e) {
            LogUtil.warn("源库只读连接恢复失败：" + e.getMessage());
        }
    }

    /**
     * 按 Oracle 兼容数据源类型解析配置，保证 OceanBase Oracle 使用专属配置类
     */
    private DatasourceConfiguration parseOracleLikeConfiguration(String configuration, String type) {
        if (isObOracle(type)) {
            return JsonUtil.parseObject(configuration, ObOracle.class);
        }
        return JsonUtil.parseObject(configuration, Oracle.class);
    }

    /**
     * 按数据源类型解析配置对象，Oracle 兼容类型走独立配置解析分支
     */
    private DatasourceConfiguration parseDatasourceConfiguration(String configuration, String type) {
        if (isOracleLike(type)) {
            return parseOracleLikeConfiguration(configuration, type);
        }
        return JsonUtil.parseObject(configuration, DatasourceConfiguration.class);
    }

    /**
     * 数据源配置解析失败时返回清晰业务错误，避免后续连接池初始化继续触发空指针。
     */
    private DatasourceConfiguration requireDatasourceConfiguration(DatasourceConfiguration configuration) {
        if (configuration == null) {
            CrestException.throwException("数据源配置不完整或无法连接");
        }
        return configuration;
    }

    /**
     * 初始化默认驱动类加载器，把驱动目录下的 JDBC 包加入运行时搜索路径
     */
    @PostConstruct
    public void init() throws Exception {
        try {
            String jarPath = FILE_PATH;
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            extendedJdbcClassLoader = new ExtendedJdbcClassLoader(new URL[]{new File(jarPath).toURI().toURL()}, classLoader);
            File file = new File(jarPath);
            File[] array = file.listFiles();
            Optional.ofNullable(array).ifPresent(files -> {
                for (File tmp : array) {
                    if (tmp.getName().endsWith(".jar")) {
                        try {
                            extendedJdbcClassLoader.addFile(tmp);
                        } catch (IOException e) {
                            io.crest.utils.LogUtil.error(e.getMessage(), e);
                        }
                    }
                }
            });
        } catch (Exception e) {

        }
    }

    /**
     * 读取数据源 schema 列表。支持只读连接策略，并过滤 PostgreSQL 系统 schema
     */
    @Override
    public List<String> getSchema(DatasourceRequest datasourceRequest) {
        List<String> schemas = new ArrayList<>();
        String queryStr = getSchemaSql(datasourceRequest.getDatasource());
        try (ConnectionObj con = getConnection(datasourceRequest.getDatasource())) {
            DatasourceConfiguration datasourceConfiguration = parseDatasourceConfiguration(
                    datasourceRequest.getDatasource().getConfiguration(),
                    datasourceRequest.getDatasource().getType()
            );
            boolean readOnly = shouldUseReadOnlyConnection(
                    datasourceRequest,
                    datasourceConfiguration,
                    datasourceRequest.getDatasource().getType()
            );
            setConnectionReadOnly(con.getConnection(), readOnly);
            try (Statement statement = getStatement(con.getConnection(), 30);
                 ResultSet resultSet = statement.executeQuery(queryStr)) {
                while (resultSet.next()) {
                    schemas.add(resultSet.getString(1));
                }
            } finally {
                resetConnectionReadOnly(con.getConnection(), readOnly);
            }
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            CrestException.throwException(e.getMessage());
        }
        if (datasourceRequest.getDatasource().getType().equalsIgnoreCase(DatasourceConfiguration.DatasourceType.pg.name())) {
            Set<String> SYSTEM_SCHEMAS = new HashSet<>(Arrays.asList("information_schema", "pg_catalog", "pg_temp_1", "pg_toast", "pg_toast_temp_1"));
            return schemas.stream().filter(schema -> !SYSTEM_SCHEMAS.contains(schema)).collect(Collectors.toList());
        }
        return schemas;
    }

    /**
     * 探测数据源连接状态，并通过一次表元数据查询验证 schema 和驱动可用性
     */
    @Override
    public String checkStatus(DatasourceRequest datasourceRequest) throws Exception {
        DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(datasourceRequest.getDatasource().getType());
        switch (datasourceType) {
            case pg:
                DatasourceConfiguration configuration = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), Pg.class);
                List<String> schemas = getSchema(datasourceRequest);
                if (CollectionUtils.isEmpty(schemas) || !schemas.contains(configuration.getSchema())) {
                    CrestException.throwException("无效的 schema！");
                }
                break;
            default:
                break;
        }

        try (ConnectionObj con = getConnection(datasourceRequest.getDatasource())) {
            DatasourceConfiguration datasourceConfiguration = parseDatasourceConfiguration(
                    datasourceRequest.getDatasource().getConfiguration(),
                    datasourceRequest.getDatasource().getType()
            );
            boolean readOnly = shouldUseReadOnlyConnection(
                    datasourceRequest,
                    datasourceConfiguration,
                    datasourceRequest.getDatasource().getType()
            );
            setConnectionReadOnly(con.getConnection(), readOnly);
            try {
                datasourceRequest.setDsVersion(con.getConnection().getMetaData().getDatabaseMajorVersion());
                QueryAndParams queryAndParams = tablesSql(datasourceRequest).get(0);
                try (PreparedStatement statement = prepareStatement(con.getConnection(), queryAndParams, 30);
                     ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet != null) {
                        resultSet.close();
                    }
                }
            } finally {
                resetConnectionReadOnly(con.getConnection(), readOnly);
            }
        } catch (Exception e) {
            throw e;
        }
        return "Success";
    }

    /**
     * 读取连接型数据源的表清单，表元数据 SQL 由具体数据库类型决定
     */
    @Override
    public List<DatasetTableDTO> tables(DatasourceRequest datasourceRequest) {
        List<DatasetTableDTO> tables = new ArrayList<>();
        try (Connection con = getConnectionFromPool(datasourceRequest.getDatasource().getId())) {
            DatasourceConfiguration datasourceConfiguration = parseDatasourceConfiguration(
                    datasourceRequest.getDatasource().getConfiguration(),
                    datasourceRequest.getDatasource().getType()
            );
            boolean readOnly = shouldUseReadOnlyConnection(
                    datasourceRequest,
                    datasourceConfiguration,
                    datasourceRequest.getDatasource().getType()
            );
            setConnectionReadOnly(con, readOnly);
            try {
                datasourceRequest.setDsVersion(con.getMetaData().getDatabaseMajorVersion());
                List<QueryAndParams> tablesSqls = tablesSql(datasourceRequest);
                for (QueryAndParams tablesSql : tablesSqls) {
                    try (PreparedStatement statement = prepareStatement(con, tablesSql, 30);
                         ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            tables.add(getTableDesc(datasourceRequest, resultSet));
                        }
                    }
                }
            } finally {
                resetConnectionReadOnly(con, readOnly);
            }
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            CrestException.throwException(e.getMessage());
        }
        return tables;
    }

    /**
     * 获取查询字段和预览数据。非跨源查询直接走 JDBC，跨源查询通过 Calcite 执行
     */
    @Override
    @SuppressWarnings("java/sql-injection")
    public Map<String, Object> fetchResultField(DatasourceRequest datasourceRequest) throws CrestException {
        // 非跨源查询使用源库 JDBC，避免不必要的 Calcite 解析和 schema 注册成本
        if (datasourceRequest.getIsCross() == null || !datasourceRequest.getIsCross()) {
            return jdbcFetchResultField(datasourceRequest);
        }

        List<TableField> datasetTableFields = new ArrayList<>();
        List<String[]> list = new LinkedList<>();
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        Connection connection = take();
        try {
            CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
            statement = calciteConnection.prepareStatement(SqlSecurityPolicy.validateReadQuery(datasourceRequest.getQuery()));
            bindPreparedStatementValues(statement, datasourceRequest.getTableFieldWithValues());
            resultSet = statement.executeQuery();
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                TableField tableField = new TableField();
                tableField.setOriginName(metaData.getColumnLabel(i));
                tableField.setType(metaData.getColumnTypeName(i));
                tableField.setPrecision(metaData.getPrecision(i));
                int fieldType = FieldUtils.resolveFieldType(tableField.getType());
                tableField.setExtractedFieldType(fieldType);
                tableField.setFieldType(fieldType);
                tableField.setScale(metaData.getScale(i));
                datasetTableFields.add(tableField);
            }
            list = dataResult(resultSet);
        } catch (Exception | AssertionError e) {
            String msg;
            if (e.getCause() != null && e.getCause().getCause() != null) {
                msg = e.getMessage() + " [" + e.getCause().getCause().getMessage() + "]";
            } else {
                msg = e.getMessage();
            }
            CrestException.throwException(Translator.get("i18n_fetch_error") + msg);
        } finally {
            try {
                if (resultSet != null) resultSet.close();
                if (statement != null) statement.close();
            } catch (Exception e) {
            }
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fields", datasetTableFields);
        map.put("data", list);
        return map;
    }

    /**
     * 将通用 SQL 转换为目标数据源方言，并补充自定义函数的兼容翻译
     */
    @Override
    public String transSqlDialect(String sql, Map<Long, DatasourceSchemaDTO> dsMap) throws CrestException {
        DatasourceSchemaDTO value = dsMap.entrySet().iterator().next().getValue();
        try (Connection connection = getConnectionFromPool(value.getId());) {
            // 方言转换依赖部分数据库版本差异，转换前先读取当前源库版本
            if (connection != null) {
                value.setDsVersion(connection.getMetaData().getDatabaseMajorVersion());
            }
            SqlParser parser = SqlParser.create(sql, SqlParser.Config.DEFAULT.withLex(Lex.JAVA));
            SqlNode sqlNode = parser.parseStmt();
            String dialect = restoreUnicodeStringLiterals(sqlNode.toSqlString(getDialect(value)).toString());
            return CrestSqlFunctionTranslator.translate(dialect, value);
        } catch (Exception e) {
            CrestException.throwException(e.getMessage());
        }
        return null;
    }

    /**
     * 从查询结果元数据构建字段列表，字段类型同时写入原生类型和平台字段类型
     */
    private List<TableField> fetchResultField(ResultSet rs) throws Exception {
        List<TableField> fieldList = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        for (int j = 0; j < columnCount; j++) {
            String columnName = metaData.getColumnName(j + 1);
            String label = StringUtils.isNotEmpty(metaData.getColumnLabel(j + 1)) ? metaData.getColumnLabel(j + 1) : columnName;
            TableField tableField = new TableField();
            tableField.setOriginName(columnName);
            tableField.setName(label);
            tableField.setType(metaData.getColumnTypeName(j + 1));
            tableField.setNativeType(tableField.getType());
            int fieldType = FieldUtils.resolveFieldType(tableField.getType());
            tableField.setExtractedFieldType(fieldType);
            tableField.setFieldType(fieldType);
            fieldList.add(tableField);
        }
        return fieldList;
    }

    /**
     * 通过空结果查询获取表字段 JDBC 类型编号，后续字段描述会用它补充类型精度信息
     */
    private Map<String, Integer> getTableTypeMap(DatasourceRequest datasourceRequest, DatasourceConfiguration datasourceConfiguration, String tableName) throws CrestException {
        Map<String, Integer> map = new HashMap<>();
        String schemaTable = (ObjectUtils.isNotEmpty(datasourceConfiguration.getSchema()) ? (quoteIdentifierPart(datasourceConfiguration.getSchema()) + ".") : "") + quoteIdentifierPart(tableName);
        // 表名由 quoteIdentifierPart 逐段转义，JDBC 占位符不能绑定标识符
        // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
        String sql = "SELECT * FROM " + schemaTable + " LIMIT 0 OFFSET 0";
        sql = transSqlDialect(sql, datasourceRequest.getDsList());
        ResultSet resultSet = null;
        try (Connection con = getConnectionFromPool(datasourceRequest.getDatasource().getId());
             PreparedStatement statement = con.prepareStatement(sql)) {
            boolean readOnly = shouldUseReadOnlyConnection(
                    datasourceRequest,
                    datasourceConfiguration,
                    datasourceRequest.getDatasource().getType()
            );
            setConnectionReadOnly(con, readOnly);
            try {
                statement.setQueryTimeout(30);
                // 表标识符已在构造 SQL 时完成校验和转义
                // nosemgrep: java.lang.security.audit.formatted-sql-string.formatted-sql-string
                resultSet = statement.executeQuery();

                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();
                for (int j = 0; j < columnCount; j++) {
                    String name = StringUtils.lowerCase(metaData.getColumnName(j + 1));
                    Integer type = metaData.getColumnType(j + 1);
                    map.put(name, type);
                }
            } finally {
                resetConnectionReadOnly(con, readOnly);
            }
        } catch (Exception e) {
            io.crest.utils.LogUtil.error(e.getMessage(), e);
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException e) {
                    io.crest.utils.LogUtil.error(e.getMessage(), e);
                }
            }
        }
        return map;
    }

    /**
     * 转义单段 SQL 标识符，调用方负责按数据库上下文判断该标识符是否允许出现
     */
    private String quoteIdentifierPart(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            CrestException.throwException("Illegal empty identifier");
        }
        return "`" + identifier.replace("`", "``") + "`";
    }

    /**
     * 转义带点号的多段 SQL 标识符，保留 schema、catalog 或表名的层级结构
     */
    private String quoteQualifiedIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            CrestException.throwException("Illegal empty identifier");
        }
        StringBuilder result = new StringBuilder();
        int start = 0;
        while (start <= identifier.length()) {
            int end = identifier.indexOf('.', start);
            String part = end < 0 ? identifier.substring(start) : identifier.substring(start, end);
            if (result.length() > 0) {
                result.append('.');
            }
            result.append(quoteIdentifierPart(part));
            if (end < 0) {
                break;
            }
            start = end + 1;
        }
        return result.toString();
    }

    /**
     * 转义 SQL 字符串字面量，用于只能拼接常量的元数据查询模板
     */
    private String sqlLiteral(String value) {
        return "'" + StringUtils.defaultString(value).replace("'", "''") + "'";
    }

    /**
     * 按字段类型绑定预编译参数，CLOB 字符串使用字符流写入以兼容大文本字段
     */
    private void bindPreparedStatementValues(PreparedStatement statement, List<TableFieldWithValue> tableFieldWithValues) throws SQLException {
        if (statement == null || CollectionUtils.isEmpty(tableFieldWithValues)) {
            return;
        }
        for (int i = 0; i < tableFieldWithValues.size(); i++) {
            TableFieldWithValue tableFieldWithValue = tableFieldWithValues.get(i);
            Object valueObject = tableFieldWithValue.getValue();
            if (Objects.equals(tableFieldWithValue.getType(), Types.CLOB) && valueObject instanceof String stringValue) {
                Reader reader = new StringReader(stringValue);
                statement.setCharacterStream(i + 1, reader, stringValue.length());
            } else if (tableFieldWithValue.getType() != null) {
                statement.setObject(i + 1, valueObject, tableFieldWithValue.getType());
            } else {
                statement.setObject(i + 1, valueObject);
            }
        }
    }

    /**
     * 获取表字段信息。跨源查询读取结果元数据，单源表读取数据库系统表或 desc 结果
     */
    @Override
    @SuppressWarnings("java/sql-injection")
    public List<TableField> fetchTableField(DatasourceRequest datasourceRequest) throws CrestException {
        if (datasourceRequest.getIsCross() != null && datasourceRequest.getIsCross()) {
            List<TableField> datasetTableFields = new ArrayList<>();
            try (Connection connection = take()) {
                CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
                try (PreparedStatement statement = calciteConnection.prepareStatement(SqlSecurityPolicy.validateReadQuery(datasourceRequest.getQuery()))) {
                    bindPreparedStatementValues(statement, datasourceRequest.getTableFieldWithValues());
                    try (ResultSet resultSet = statement.executeQuery()) {
                        ResultSetMetaData metaData = resultSet.getMetaData();
                        int columnCount = metaData.getColumnCount();
                        for (int i = 1; i <= columnCount; i++) {
                            TableField tableField = new TableField();
                            tableField.setOriginName(metaData.getColumnLabel(i));
                            tableField.setType(metaData.getColumnTypeName(i));
                            tableField.setPrecision(metaData.getPrecision(i));
                            int fieldType = FieldUtils.resolveFieldType(tableField.getType());
                            tableField.setExtractedFieldType(fieldType);
                            tableField.setFieldType(fieldType);
                            tableField.setScale(metaData.getScale(i));
                            datasetTableFields.add(tableField);
                        }
                    }
                }
            } catch (Exception e) {
                throw CrestException.getException(e.getMessage());
            }
            return datasetTableFields;
        }
        List<TableField> datasetTableFields = new ArrayList<>();
        DatasourceSchemaDTO datasourceSchemaDTO = datasourceRequest.getDsList().entrySet().iterator().next().getValue();
        datasourceRequest.setDatasource(datasourceSchemaDTO);

        DatasourceConfiguration datasourceConfiguration = parseDatasourceConfiguration(datasourceRequest.getDatasource().getConfiguration(), datasourceSchemaDTO.getType());

        String table = datasourceRequest.getTable();
        if (StringUtils.isEmpty(table)) {
            ResultSet resultSet = null;
            try (Connection con = getConnectionFromPool(datasourceRequest.getDatasource().getId());
                 Statement statement = getStatement(con, 30)) {
                if (isOracleLike(datasourceSchemaDTO.getType())) {
                    statement.executeUpdate("ALTER SESSION SET CURRENT_SCHEMA = " + oracleSchemaIdentifier(datasourceConfiguration.getSchema()));
                }
                boolean readOnly = shouldUseReadOnlyConnection(datasourceRequest, datasourceConfiguration, datasourceSchemaDTO.getType());
                setConnectionReadOnly(con, readOnly);
                try {
                    resultSet = statement.executeQuery(SqlSecurityPolicy.validateReadQuery(datasourceRequest.getQuery()));
                    datasetTableFields.addAll(getField(resultSet, datasourceRequest));
                } finally {
                    resetConnectionReadOnly(con, readOnly);
                }
            } catch (Exception e) {
                CrestException.throwException(e.getMessage());
            } finally {
                if (resultSet != null) {
                    try {
                        resultSet.close();
                    } catch (SQLException e) {
                        io.crest.utils.LogUtil.error(e.getMessage(), e);
                    }
                }
            }
        } else {
            if (!tables(datasourceRequest).stream().map(DatasetTableDTO::getTableName).collect(Collectors.toList()).contains(table)) {
                CrestException.throwException(Translator.get("i18n_invalid_table_name"));
            }
            ResultSet resultSet = null;
            try (Connection con = getConnectionFromPool(datasourceRequest.getDatasource().getId());
                 Statement statement = getStatement(con, 30)) {
                boolean readOnly = shouldUseReadOnlyConnection(datasourceRequest, datasourceConfiguration, datasourceSchemaDTO.getType());
                setConnectionReadOnly(con, readOnly);
                try {
                    datasourceRequest.setDsVersion(con.getMetaData().getDatabaseMajorVersion());
                    if (datasourceRequest.getDatasource().getType().equalsIgnoreCase("mongo")) {
                        resultSet = statement.executeQuery("select * from " + quoteQualifiedIdentifier(table) + " limit 0 offset 0 ");
                        return fetchResultField(resultSet);
                    }
                    if (isDorisCatalog(datasourceRequest)) {
                        resultSet = statement.executeQuery("desc " + quoteQualifiedIdentifier(table));
                    } else {
                        resultSet = statement.executeQuery(getTableFiledSql(datasourceRequest));
                    }

                    Map<String, Integer> tableTypeMap = getTableTypeMap(datasourceRequest, datasourceConfiguration, table);

                    while (resultSet.next()) {
                        TableField tableFieldDesc = getTableFieldDesc(datasourceRequest, resultSet, 3, tableTypeMap);
                        boolean repeat = false;
                        for (TableField ele : datasetTableFields) {
                            if (Strings.CI.equals(ele.getOriginName(), tableFieldDesc.getOriginName())) {
                                repeat = true;
                                break;
                            }
                        }
                        if (!repeat) {
                            datasetTableFields.add(tableFieldDesc);
                        }
                    }
                    if (isObOracle(datasourceRequest.getDatasource().getType())) {
                        DatasourceConfiguration configuration = parseOracleLikeConfiguration(datasourceRequest.getDatasource().getConfiguration(), datasourceRequest.getDatasource().getType());
                        applyOracleColumnComments(datasetTableFields, getOracleColumnComments(con, configuration.getSchema(), table));
                    }
                } finally {
                    resetConnectionReadOnly(con, readOnly);
                }
            } catch (Exception e) {
                CrestException.throwException(e.getMessage());
            } finally {
                if (resultSet != null) {
                    try {
                        resultSet.close();
                    } catch (SQLException e) {
                        io.crest.utils.LogUtil.error(e.getMessage(), e);
                    }
                }
            }
        }

        return datasetTableFields;
    }

    /**
     * 判断 Doris/StarRocks 是否使用 catalog.database 形式，决定字段读取走 desc 还是 information_schema
     */
    private boolean isDorisCatalog(DatasourceRequest datasourceRequest) {
        if (!datasourceRequest.getDatasource().getType().equalsIgnoreCase("doris")) {
            return false;
        }
        DatasourceConfiguration configuration = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), Mysql.class);
        String database = "";
        if (StringUtils.isEmpty(configuration.getUrlType()) || configuration.getUrlType().equalsIgnoreCase("hostName")) {
            database = configuration.getDataBase();
        } else {
            database = databaseFromJdbcUrl(configuration.getJdbcUrl());
        }
        return database.contains(".");
    }

    /**
     * 从 JDBC URL 中提取数据库名，忽略 query 参数和分号参数
     */
    private String databaseFromJdbcUrl(String jdbcUrl) {
        if (StringUtils.isBlank(jdbcUrl)) {
            CrestException.throwException(Translator.get("i18n_invalid_connection"));
        }
        int schemeEnd = jdbcUrl.indexOf("://");
        int databaseStart = jdbcUrl.indexOf('/', schemeEnd < 0 ? 0 : schemeEnd + 3);
        if (databaseStart < 0 || databaseStart + 1 >= jdbcUrl.length()) {
            CrestException.throwException(Translator.get("i18n_invalid_connection"));
        }
        int queryStart = jdbcUrl.indexOf('?', databaseStart + 1);
        int semicolonStart = jdbcUrl.indexOf(';', databaseStart + 1);
        int databaseEnd = jdbcUrl.length();
        if (queryStart > -1) {
            databaseEnd = Math.min(databaseEnd, queryStart);
        }
        if (semicolonStart > -1) {
            databaseEnd = Math.min(databaseEnd, semicolonStart);
        }
        return jdbcUrl.substring(databaseStart + 1, databaseEnd);
    }

    /**
     * 创建单个源库 JDBC 连接，完成配置解析、驱动选择、SSH 隧道和 JDBC URL 安全校验
     */
    @Override
    public ConnectionObj getConnection(DatasourceDTO coreDatasource) throws Exception {
        ConnectionObj connectionObj = new ConnectionObj();
        DatasourceConfiguration configuration = null;
        DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(coreDatasource.getType());
        switch (datasourceType) {
            case mysql:
            case obMysql:
            case mongo:
            case StarRocks:
            case doris:
            case TiDB:
            case mariadb:
                configuration = datasourceType == DatasourceConfiguration.DatasourceType.obMysql
                        ? JsonUtil.parseObject(coreDatasource.getConfiguration(), ObMysql.class)
                        : JsonUtil.parseObject(coreDatasource.getConfiguration(), Mysql.class);
                break;
            case impala:
                configuration = JsonUtil.parseObject(coreDatasource.getConfiguration(), Impala.class);
                break;
            case sqlServer:
                configuration = JsonUtil.parseObject(coreDatasource.getConfiguration(), Sqlserver.class);
                break;
            case oracle:
            case obOracle:
                configuration = parseOracleLikeConfiguration(coreDatasource.getConfiguration(), coreDatasource.getType());
                break;
            case db2:
                configuration = JsonUtil.parseObject(coreDatasource.getConfiguration(), Db2.class);
                break;
            case pg:
                configuration = JsonUtil.parseObject(coreDatasource.getConfiguration(), Pg.class);
                break;
            case redshift:
                configuration = JsonUtil.parseObject(coreDatasource.getConfiguration(), Redshift.class);
                break;
            case h2:
                configuration = JsonUtil.parseObject(coreDatasource.getConfiguration(), H2.class);
                break;
            case ck:
                configuration = JsonUtil.parseObject(coreDatasource.getConfiguration(), CK.class);
                break;
            default:
                configuration = JsonUtil.parseObject(coreDatasource.getConfiguration(), Mysql.class);
        }
        configuration = requireDatasourceConfiguration(configuration);
        CoreDriver customDriver = resolveCustomDriver(coreDatasource.getType(), configuration.getCustomDriver());
        String driverClassName = JdbcUrlSecurityPolicy.resolveDriverClass(coreDatasource.getType(), configuration.getDriver(), configuration.getCustomDriver(), customDriver);
        configuration.setDriver(driverClassName);
        startSshSession(configuration, connectionObj, null);
        Properties props = new Properties();
        if (StringUtils.isNotBlank(configuration.getUsername())) {
            props.setProperty("user", configuration.getUsername());
        }
        if (StringUtils.isNotBlank(configuration.getPassword())) {
            props.setProperty("password", configuration.getPassword());
        }
        ExtendedJdbcClassLoader jdbcClassLoader = JdbcUrlSecurityPolicy.isDefaultCustomDriver(configuration.getCustomDriver()) ? extendedJdbcClassLoader : getCustomJdbcClassLoader(customDriver);
        Connection conn = null;
        try {
            Driver driverClass = (Driver) jdbcClassLoader.loadClass(driverClassName).newInstance();
            String jdbcUrl = JdbcUrlSecurityPolicy.validate(coreDatasource.getType(), configuration.getJdbc(), configuration.getExtraParams());
            conn = driverClass.connect(jdbcUrl, props);

        } catch (Exception e) {
            CrestException.throwException(e.getMessage());
        }
        connectionObj.setConnection(conn);
        return connectionObj;
    }

    /**
     * 校验并读取自定义驱动配置，默认驱动返回空值，由默认类加载器处理
     */
    private CoreDriver resolveCustomDriver(String datasourceType, String customDriver) {
        if (JdbcUrlSecurityPolicy.isDefaultCustomDriver(customDriver)) {
            return null;
        }
        Long customDriverId;
        try {
            customDriverId = Long.valueOf(customDriver);
        } catch (NumberFormatException e) {
            CrestException.throwException("invalid driver");
            return null;
        }
        CoreDriver coreDriver = coreDriverMapper.selectById(customDriverId);
        if (coreDriver == null || !StringUtils.equalsIgnoreCase(coreDriver.getType(), datasourceType)) {
            CrestException.throwException("invalid driver");
        }
        return coreDriver;
    }

    /**
     * 将表清单查询结果转换为数据集表描述，第二列存在时作为展示名称
     */
    private DatasetTableDTO getTableDesc(DatasourceRequest datasourceRequest, ResultSet resultSet) throws SQLException {
        DatasetTableDTO tableDesc = new DatasetTableDTO();
        tableDesc.setDatasourceId(datasourceRequest.getDatasource().getId());
        tableDesc.setType("db");
        tableDesc.setTableName(resultSet.getString(1));
        if (resultSet.getMetaData().getColumnCount() > 1) {
            tableDesc.setName(resultSet.getString(2));
        } else {
            tableDesc.setName(resultSet.getString(1));
        }
        return tableDesc;
    }

    /**
     * 收集所有内置数据源配置声明的驱动类，用于启动时注册默认驱动
     */
    private List<String> getDriver() {
        List<String> drivers = new ArrayList<>();
        Map<String, DatasourceConfiguration> beansOfType = CommonBeanFactory.getApplicationContext().getBeansOfType((DatasourceConfiguration.class));
        beansOfType.keySet().forEach(key -> drivers.add(beansOfType.get(key).getDriver()));
        return drivers;
    }

    /**
     * 直接在源库执行查询并返回字段和数据，主要用于非跨源预览和字段发现
     */
    @SuppressWarnings("java/sql-injection")
    public Map<String, Object> jdbcFetchResultField(DatasourceRequest datasourceRequest) throws CrestException {
        DatasourceSchemaDTO value = datasourceRequest.getDsList().entrySet().iterator().next().getValue();
        datasourceRequest.setDatasource(value);

        DatasourceConfiguration datasourceConfiguration = parseDatasourceConfiguration(datasourceRequest.getDatasource().getConfiguration(), value.getType());

        Map<String, Object> map = new LinkedHashMap<>();
        List<TableField> fieldList = new ArrayList<>();
        List<String[]> dataList = new LinkedList<>();

        // 查询前准备 schema、只读状态和 Oracle 字符集转换上下文
        ResultSet resultSet = null;
        String oracleCharset = normalizeOracleCharset(datasourceConfiguration.getCharset());
        String oracleTargetCharset = normalizeOracleCharset(datasourceConfiguration.getTargetCharset());

        try (Connection con = getConnectionFromPool(datasourceRequest.getDatasource().getId())) {

            Statement statement = getStatement(value, con, datasourceRequest, datasourceConfiguration, null);
            boolean readOnly = shouldUseReadOnlyConnection(datasourceRequest, datasourceConfiguration, value.getType());
            setConnectionReadOnly(con, readOnly);
            try {
                if (CollectionUtils.isNotEmpty(datasourceRequest.getTableFieldWithValues())) {
                    LogUtil.info("execWithPreparedStatement sql: " + datasourceRequest.getQuery());
                    for (int i = 0; i < datasourceRequest.getTableFieldWithValues().size(); i++) {
                        try {
                            Object valueObject = datasourceRequest.getTableFieldWithValues().get(i).getValue();

                            if (valueObject instanceof String && isOracleLike(value.getType())) {
                                if (StringUtils.isNotEmpty(oracleCharset) && StringUtils.isNotEmpty(oracleTargetCharset)) {
                                    // Oracle 兼容库按目标库字符集写入参数，避免中文或大文本参数乱码
                                    valueObject = convertOracleText((String) valueObject, oracleTargetCharset, oracleCharset);
                                }
                                if (datasourceRequest.getTableFieldWithValues().get(i).getType().equals(Types.CLOB)) {
                                    Reader reader = new StringReader((String) valueObject);
                                    ((PreparedStatement) statement).setCharacterStream(i + 1, reader, ((String) valueObject).length());
                                } else {
                                    ((PreparedStatement) statement).setObject(i + 1, valueObject, datasourceRequest.getTableFieldWithValues().get(i).getType());
                                }
                            } else {
                                ((PreparedStatement) statement).setObject(i + 1, valueObject, datasourceRequest.getTableFieldWithValues().get(i).getType());
                            }
                            LogUtil.info("execWithPreparedStatement param[" + (i + 1) + "](" + datasourceRequest.getTableFieldWithValues().get(i).getColumnTypeName() + "): " + datasourceRequest.getTableFieldWithValues().get(i).getValue());
                        } catch (SQLException e) {
                            throw new SQLException(e.getMessage() + ". VALUE: " + datasourceRequest.getTableFieldWithValues().get(i).getValue().toString() + " , TARGET TYPE: " + datasourceRequest.getTableFieldWithValues().get(i).getColumnTypeName());
                        }
                    }
                    resultSet = ((PreparedStatement) statement).executeQuery();
                } else {
                    resultSet = statement.executeQuery(SqlSecurityPolicy.validateReadQuery(datasourceRequest.getQuery()));
                }
                fieldList = getField(resultSet, datasourceRequest);
                dataList = data(resultSet, datasourceRequest);
            } finally {
                resetConnectionReadOnly(con, readOnly);
            }
        } catch (SQLException e) {
            CrestException.throwException("SQL ERROR: " + e.getMessage());
        } catch (Exception e) {
            CrestException.throwException("Datasource connection exception: " + e.getMessage());
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException e) {
                    io.crest.utils.LogUtil.error(e.getMessage(), e);
                }
            }
        }

        map.put("fields", fieldList);
        map.put("data", dataList);
        return map;
    }

    /**
     * 在源库执行无返回值 SQL，支持预编译参数和 Oracle 字符集转换
     */
    @Override
    public void exec(DatasourceRequest datasourceRequest) throws CrestException {
        DatasourceSchemaDTO value = datasourceRequest.getDsList().entrySet().iterator().next().getValue();
        datasourceRequest.setDatasource(value);
        DatasourceConfiguration datasourceConfiguration = parseDatasourceConfiguration(datasourceRequest.getDatasource().getConfiguration(), value.getType());
        // 执行前准备 schema、参数字符集和结果集清理上下文
        ResultSet resultSet = null;
        String oracleCharset = normalizeOracleCharset(datasourceConfiguration.getCharset());
        String oracleTargetCharset = normalizeOracleCharset(datasourceConfiguration.getTargetCharset());

        try (Connection con = getConnectionFromPool(datasourceRequest.getDatasource().getId())) {

            Statement statement = getStatement(value, con, datasourceRequest, datasourceConfiguration, null);

            if (CollectionUtils.isNotEmpty(datasourceRequest.getTableFieldWithValues())) {
                LogUtil.info("execWithPreparedStatement sql: " + datasourceRequest.getQuery());
                for (int i = 0; i < datasourceRequest.getTableFieldWithValues().size(); i++) {
                    try {
                        Object valueObject = datasourceRequest.getTableFieldWithValues().get(i).getValue();
                        if (valueObject instanceof String && isOracleLike(value.getType())) {
                            if (StringUtils.isNotEmpty(oracleCharset) && StringUtils.isNotEmpty(oracleTargetCharset)) {
                                // Oracle 兼容库按目标库字符集写入参数，避免中文或大文本参数乱码
                                valueObject = convertOracleText((String) valueObject, oracleTargetCharset, oracleCharset);
                            }
                            if (datasourceRequest.getTableFieldWithValues().get(i).getType().equals(Types.CLOB)) {
                                Reader reader = new StringReader((String) valueObject);
                                ((PreparedStatement) statement).setCharacterStream(i + 1, reader, ((String) valueObject).length());
                            } else {
                                ((PreparedStatement) statement).setObject(i + 1, valueObject, datasourceRequest.getTableFieldWithValues().get(i).getType());
                            }
                        } else {
                            ((PreparedStatement) statement).setObject(i + 1, valueObject, datasourceRequest.getTableFieldWithValues().get(i).getType());
                        }
                        LogUtil.info("execWithPreparedStatement param[" + (i + 1) + "](" + datasourceRequest.getTableFieldWithValues().get(i).getColumnTypeName() + "): " + datasourceRequest.getTableFieldWithValues().get(i).getValue());
                    } catch (SQLException e) {
                        throw new SQLException(e.getMessage() + ". VALUE: " + datasourceRequest.getTableFieldWithValues().get(i).getValue().toString() + " , TARGET TYPE: " + datasourceRequest.getTableFieldWithValues().get(i).getColumnTypeName());
                    }
                }
                ((PreparedStatement) statement).execute();
            } else {
                statement.execute(datasourceRequest.getQuery());
            }

        } catch (SQLException e) {
            CrestException.throwException("SQL ERROR: " + e.getMessage());
        } catch (Exception e) {
            CrestException.throwException("Datasource connection exception: " + e.getMessage());
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException e) {
                    io.crest.utils.LogUtil.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 创建当前请求使用的 Statement。Oracle 兼容库需要先设置 schema、时间格式和字符集
     */
    private Statement getStatement(DatasourceSchemaDTO value, Connection con, DatasourceRequest datasourceRequest, DatasourceConfiguration datasourceConfiguration, String autoIncrementPkName) throws Exception {
        Statement statement;
        int queryTimeout = datasourceQueryTimeout(datasourceRequest, datasourceConfiguration);
        if (isOracleLike(value.getType())) {
            statement = getStatement(con, queryTimeout);
            if (StringUtils.isNotBlank(datasourceConfiguration.getSchema())) {
                statement.executeUpdate("ALTER SESSION SET CURRENT_SCHEMA = " + oracleSchemaIdentifier(datasourceConfiguration.getSchema()));
            }
            statement.executeUpdate("ALTER SESSION SET NLS_TIMESTAMP_FORMAT = 'YYYY-MM-DD HH24:MI:SS'");
            // 查询文本按目标库字符集转换，避免 Oracle 兼容库解析中文 SQL 条件失败
            String oracleCharset = normalizeOracleCharset(datasourceConfiguration.getCharset());
            String oracleTargetCharset = normalizeOracleCharset(datasourceConfiguration.getTargetCharset());
            if (StringUtils.isNotEmpty(oracleCharset) && StringUtils.isNotEmpty(oracleTargetCharset)) {
                datasourceRequest.setQuery(convertOracleText(datasourceRequest.getQuery(), oracleTargetCharset, oracleCharset));
            }
        }
        statement = getPreparedStatement(con, queryTimeout, datasourceRequest.getQuery(), datasourceRequest.getTableFieldWithValues(), autoIncrementPkName, datasourceConfiguration);
        return statement;
    }

    /**
     * 执行源库更新 SQL，并在需要时返回数据库生成的自增主键
     */
    @Override
    public ExecuteResult executeUpdate(DatasourceRequest datasourceRequest, String autoIncrementPkName) throws CrestException {
        DatasourceSchemaDTO value = datasourceRequest.getDsList().entrySet().iterator().next().getValue();
        datasourceRequest.setDatasource(value);
        DatasourceConfiguration datasourceConfiguration = parseDatasourceConfiguration(datasourceRequest.getDatasource().getConfiguration(), value.getType());
        // 更新前准备 schema、参数字符集和生成主键读取上下文
        ResultSet resultSet = null;
        String oracleCharset = normalizeOracleCharset(datasourceConfiguration.getCharset());
        String oracleTargetCharset = normalizeOracleCharset(datasourceConfiguration.getTargetCharset());
        try (Connection con = getConnectionFromPool(datasourceRequest.getDatasource().getId())) {

            Statement statement = getStatement(value, con, datasourceRequest, datasourceConfiguration, autoIncrementPkName);

            int count = 0;
            if (CollectionUtils.isNotEmpty(datasourceRequest.getTableFieldWithValues())) {
                LogUtil.info("execWithPreparedStatement sql: " + datasourceRequest.getQuery());
                for (int i = 0; i < datasourceRequest.getTableFieldWithValues().size(); i++) {
                    try {
                        Object valueObject = datasourceRequest.getTableFieldWithValues().get(i).getValue();

                        if (valueObject instanceof String && isOracleLike(value.getType())) {
                            if (StringUtils.isNotEmpty(oracleCharset) && StringUtils.isNotEmpty(oracleTargetCharset)) {
                                // Oracle 兼容库按目标库字符集写入参数，避免中文或大文本参数乱码
                                valueObject = convertOracleText((String) valueObject, oracleTargetCharset, oracleCharset);
                            }
                            if (datasourceRequest.getTableFieldWithValues().get(i).getType().equals(Types.CLOB)) {
                                Reader reader = new StringReader((String) valueObject);
                                ((PreparedStatement) statement).setCharacterStream(i + 1, reader, ((String) valueObject).length());
                            } else {
                                ((PreparedStatement) statement).setObject(i + 1, valueObject, datasourceRequest.getTableFieldWithValues().get(i).getType());
                            }
                        } else {
                            ((PreparedStatement) statement).setObject(i + 1, valueObject, datasourceRequest.getTableFieldWithValues().get(i).getType());
                        }
                        LogUtil.info("execWithPreparedStatement param[" + (i + 1) + "](" + datasourceRequest.getTableFieldWithValues().get(i).getColumnTypeName() + "): " + datasourceRequest.getTableFieldWithValues().get(i).getValue());
                    } catch (SQLException e) {
                        throw new SQLException(e.getMessage() + ". VALUE: " + datasourceRequest.getTableFieldWithValues().get(i).getValue().toString() + " , TARGET TYPE: " + datasourceRequest.getTableFieldWithValues().get(i).getColumnTypeName());
                    }
                }
                count = ((PreparedStatement) statement).executeUpdate();
            } else {
                count = statement.executeUpdate(datasourceRequest.getQuery());
            }

            ExecuteResult result = new ExecuteResult();
            result.setCount(count);

            if (StringUtils.isNotBlank(autoIncrementPkName)) {
                List<String> generatedKeys = new ArrayList<>();
                ResultSet keys = statement.getGeneratedKeys();
                while (keys.next()) {
                    generatedKeys.add(keys.getObject(1).toString());
                }
                result.setGeneratedKeys(generatedKeys);
            }

            return result;
        } catch (SQLException e) {
            CrestException.throwException("SQL ERROR: " + e.getMessage());
        } catch (Exception e) {
            CrestException.throwException("Datasource connection exception: " + e.getMessage());
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException e) {
                    io.crest.utils.LogUtil.error(e.getMessage(), e);
                }
            }
        }

        return new ExecuteResult();
    }

    /**
     * 从查询结果元数据生成字段列表，并过滤 Oracle ROWNUM 这类分页辅助列
     */
    private List<TableField> getField(ResultSet rs, DatasourceRequest datasourceRequest) throws Exception {
        List<TableField> fieldList = new ArrayList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        for (int j = 0; j < columnCount; j++) {
            String f = metaData.getColumnName(j + 1);
            if (Strings.CI.contains(f, "ROWNUM")) {
                continue;
            }
            String l = StringUtils.isNotEmpty(metaData.getColumnLabel(j + 1)) ? metaData.getColumnLabel(j + 1) : f;
            String t = metaData.getColumnTypeName(j + 1).toUpperCase();
            TableField field = new TableField();
            field.setOriginName(l);
            field.setName(l);
            field.setNativeType(t);
            field.setType(t);
            fieldList.add(field);
        }
        return fieldList;
    }

    /**
     * 将查询结果转换为字符串二维数组，按 JDBC 类型处理日期、布尔、数值和 Oracle 字符集
     */
    private List<String[]> data(ResultSet rs, DatasourceRequest datasourceRequest) throws Exception {
        String targetCharset = null;
        String originCharset = null;
        if (datasourceRequest != null && isOracleLike(datasourceRequest.getDatasource().getType())) {
            DatasourceConfiguration jdbcConfiguration = parseDatasourceConfiguration(datasourceRequest.getDatasource().getConfiguration(), datasourceRequest.getDatasource().getType());

            if (StringUtils.isNotEmpty(jdbcConfiguration.getCharset())) {
                originCharset = normalizeOracleCharset(jdbcConfiguration.getCharset());
            }
            if (StringUtils.isNotEmpty(jdbcConfiguration.getTargetCharset())) {
                targetCharset = normalizeOracleCharset(jdbcConfiguration.getTargetCharset());
            }
        }
        List<String[]> list = new LinkedList<>();
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        while (rs.next()) {
            String[] row = new String[columnCount];
            for (int j = 0; j < columnCount; j++) {
                int columnType = metaData.getColumnType(j + 1);
                switch (columnType) {
                    case Types.DATE:
                        if (rs.getDate(j + 1) != null) {
                            row[j] = rs.getDate(j + 1).toString();
                        }
                        break;
                    case Types.TIMESTAMP:
                        if (rs.getTimestamp(j + 1) != null) {
                            row[j] = rs.getTimestamp(j + 1).toString();
                        }
                        break;
                    case Types.BOOLEAN:
                        row[j] = rs.getBoolean(j + 1) ? "1" : "0";
                        break;
                    case Types.NUMERIC:
                        BigDecimal bigDecimal = rs.getBigDecimal(j + 1);
                        row[j] = bigDecimal == null ? null : bigDecimal.toString();
                        break;
                    default:
                        if (metaData.getColumnTypeName(j + 1).toLowerCase().equalsIgnoreCase("blob")) {
                            row[j] = rs.getBlob(j + 1) == null ? "" : rs.getBlob(j + 1).toString();
                        }
                        if (targetCharset != null && StringUtils.isNotEmpty(rs.getString(j + 1)) && columnType == Types.CLOB) {
                            if (originCharset == null) {
                                row[j] = new String(rs.getString(j + 1).getBytes(), targetCharset);
                            } else {
                                row[j] = new String(rs.getString(j + 1).getBytes(originCharset), targetCharset);
                            }
                        } else if (targetCharset != null && StringUtils.isNotEmpty(rs.getString(j + 1)) && (columnType != Types.NVARCHAR && columnType != Types.NCHAR)) {
                            row[j] = new String(rs.getBytes(j + 1), targetCharset);
                        } else {
                            row[j] = rs.getString(j + 1);
                        }

                        break;
                }
            }
            list.add(row);
        }
        return list;
    }

    /**
     * 规范化 Oracle 字符集名称，Default 表示不做额外转换
     */
    private String normalizeOracleCharset(String charset) {
        if (StringUtils.isBlank(charset) || Strings.CI.equals(charset, "Default")) {
            return null;
        }
        String normalized = Strings.CI.equals(charset, "US7ASCII") ? "US-ASCII" : charset;
        try {
            return Charset.forName(normalized).name();
        } catch (UnsupportedCharsetException | IllegalCharsetNameException e) {
            CrestException.throwException("Unsupported charset: " + charset);
        }
        return null;
    }

    /**
     * 在两个字符集之间转换 Oracle 文本参数或结果值
     */
    private String convertOracleText(String value, String fromCharset, String toCharset) throws UnsupportedEncodingException {
        if (StringUtils.isEmpty(value) || StringUtils.isBlank(fromCharset) || StringUtils.isBlank(toCharset)) {
            return value;
        }
        return new String(value.getBytes(fromCharset), toCharset);
    }

    /**
     * 隐藏数据源配置中的密码，主要处理自定义 JDBC URL 中显式携带的 password 参数
     */
    @Override
    public void hidePW(DatasourceDTO datasourceDTO) {
        DatasourceConfiguration configuration = null;
        DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(datasourceDTO.getType());
        switch (datasourceType) {
            case mysql:
            case obMysql:
            case mongo:
            case mariadb:
            case TiDB:
            case StarRocks:
            case doris:
                configuration = datasourceType == DatasourceConfiguration.DatasourceType.obMysql
                        ? JsonUtil.parseObject(datasourceDTO.getConfiguration(), ObMysql.class)
                        : JsonUtil.parseObject(datasourceDTO.getConfiguration(), Mysql.class);
                if (StringUtils.isNotEmpty(configuration.getUrlType()) && configuration.getUrlType().equalsIgnoreCase("jdbcUrl")) {
                    if (configuration.getJdbcUrl().contains("password=")) {
                        String[] params = configuration.getJdbcUrl().split("\\?")[1].split("&");
                        String pd = "";
                        for (int i = 0; i < params.length; i++) {
                            if (params[i].contains("password=")) {
                                pd = params[i];
                            }
                        }
                        configuration.setJdbcUrl(configuration.getJdbcUrl().replace(pd, "password=******"));
                        datasourceDTO.setConfiguration(JsonUtil.toJSONString(configuration).toString());
                    }
                }
                break;
            case obOracle:
                configuration = JsonUtil.parseObject(datasourceDTO.getConfiguration(), ObOracle.class);
                if (StringUtils.isNotEmpty(configuration.getUrlType()) && configuration.getUrlType().equalsIgnoreCase("jdbcUrl")) {
                    if (configuration.getJdbcUrl().contains("password=")) {
                        String[] params = configuration.getJdbcUrl().split("\\?")[1].split("&");
                        String pd = "";
                        for (int i = 0; i < params.length; i++) {
                            if (params[i].contains("password=")) {
                                pd = params[i];
                            }
                        }
                        configuration.setJdbcUrl(configuration.getJdbcUrl().replace(pd, "password=******"));
                        datasourceDTO.setConfiguration(JsonUtil.toJSONString(configuration).toString());
                    }
                }
                break;
            case pg:
                configuration = JsonUtil.parseObject(datasourceDTO.getConfiguration(), Pg.class);
                if (StringUtils.isNotEmpty(configuration.getUrlType()) && configuration.getUrlType().equalsIgnoreCase("jdbcUrl")) {
                    if (configuration.getJdbcUrl().contains("password=")) {
                        String[] params = configuration.getJdbcUrl().split("\\?")[1].split("&");
                        String pd = "";
                        for (int i = 0; i < params.length; i++) {
                            if (params[i].contains("password=")) {
                                pd = params[i];
                            }
                        }
                        configuration.setJdbcUrl(configuration.getJdbcUrl().replace(pd, "password=******"));
                        datasourceDTO.setConfiguration(JsonUtil.toJSONString(configuration).toString());
                    }
                }
                break;
            case redshift:
                configuration = JsonUtil.parseObject(datasourceDTO.getConfiguration(), Redshift.class);
                if (StringUtils.isNotEmpty(configuration.getUrlType()) && configuration.getUrlType().equalsIgnoreCase("jdbcUrl")) {
                    if (configuration.getJdbcUrl().contains("password=")) {
                        String[] params = configuration.getJdbcUrl().split("\\?")[1].split("&");
                        String pd = "";
                        for (int i = 0; i < params.length; i++) {
                            if (params[i].contains("password=")) {
                                pd = params[i];
                            }
                        }
                        configuration.setJdbcUrl(configuration.getJdbcUrl().replace(pd, "password=******"));
                        datasourceDTO.setConfiguration(JsonUtil.toJSONString(configuration).toString());
                    }
                }
                break;
            case ck:
                configuration = JsonUtil.parseObject(datasourceDTO.getConfiguration(), CK.class);
                if (StringUtils.isNotEmpty(configuration.getUrlType()) && configuration.getUrlType().equalsIgnoreCase("jdbcUrl")) {
                    if (configuration.getJdbcUrl().contains("password=")) {
                        String[] params = configuration.getJdbcUrl().split("\\?")[1].split("&");
                        String pd = "";
                        for (int i = 0; i < params.length; i++) {
                            if (params[i].contains("password=")) {
                                pd = params[i];
                            }
                        }
                        configuration.setJdbcUrl(configuration.getJdbcUrl().replace(pd, "password=******"));
                        datasourceDTO.setConfiguration(JsonUtil.toJSONString(configuration).toString());
                    }
                }
                break;
            case impala:
                configuration = JsonUtil.parseObject(datasourceDTO.getConfiguration(), Impala.class);
                if (StringUtils.isNotEmpty(configuration.getUrlType()) && configuration.getUrlType().equalsIgnoreCase("jdbcUrl")) {
                    if (configuration.getJdbcUrl().contains("password=")) {
                        String[] params = configuration.getJdbcUrl().split(";");
                        String pd = "";
                        for (int i = 0; i < params.length; i++) {
                            if (params[i].contains("password=")) {
                                pd = params[i];
                            }
                        }
                        configuration.setJdbcUrl(configuration.getJdbcUrl().replace(pd, "password=******"));
                        datasourceDTO.setConfiguration(JsonUtil.toJSONString(configuration).toString());
                    }
                }
                break;
            default:
                break;
        }
    }

    /**
     * 将 Oracle 字段注释应用为字段展示名，字段原始名仍保留用于实际查询
     */
    static void applyOracleColumnComments(List<TableField> fields, Map<String, String> columnComments) {
        if (CollectionUtils.isEmpty(fields) || columnComments == null || columnComments.isEmpty()) {
            return;
        }
        for (TableField field : fields) {
            String comments = columnComments.get(normalizeOracleName(field.getOriginName()));
            if (StringUtils.isNotBlank(comments)) {
                field.setName(comments.trim());
            }
        }
    }

    /**
     * 读取 Oracle 字段注释，返回值使用大写字段名作为匹配键
     */
    private Map<String, String> getOracleColumnComments(Connection connection, String schema, String table) throws SQLException {
        Map<String, String> comments = new HashMap<>();
        if (StringUtils.isBlank(schema) || StringUtils.isBlank(table)) {
            return comments;
        }
        String sql = "select column_name, comments from all_col_comments where owner = ? and table_name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizeOracleName(schema));
            statement.setString(2, normalizeOracleName(table));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    comments.put(normalizeOracleName(resultSet.getString("column_name")), resultSet.getString("comments"));
                }
            }
        }
        return comments;
    }

    /**
     * 规范化 Oracle 元数据名称，避免大小写差异导致注释匹配失败
     */
    private static String normalizeOracleName(String name) {
        return StringUtils.trimToEmpty(name).toUpperCase(Locale.ROOT);
    }

    /**
     * 格式化 Oracle schema 标识符，CURRENT_SCHEMA 不支持用 JDBC 参数绑定。
     */
    private String oracleSchemaIdentifier(String schema) {
        String normalized = normalizeOracleName(schema);
        if (!normalized.matches("[A-Z][A-Z0-9_$#]*")) {
            CrestException.throwException("Illegal schema: " + schema);
        }
        return "\"" + normalized + "\"";
    }

    /**
     * 将数据库字段元数据行转换为 TableField，并补充主键、自增和 JDBC 类型编号
     */
    private TableField getTableFieldDesc(DatasourceRequest datasourceRequest, ResultSet resultSet, int commentIndex, Map<String, Integer> tableTypeMap) throws SQLException {
        TableField tableField = new TableField();
        tableField.setOriginName(resultSet.getString(1));
        tableField.setType(resultSet.getString(2).toUpperCase());
        tableField.setNativeType(tableField.getType());
        int fieldType = FieldUtils.resolveFieldType(tableField.getType());
        tableField.setExtractedFieldType(fieldType);
        tableField.setFieldType(fieldType);
        tableField.setName(resultSet.getString(commentIndex));
        try {
            tableField.setPrimary(resultSet.getInt(4) > 0);
        } catch (Exception e) {
        }
        try {
            if (StringUtils.endsWithIgnoreCase(datasourceRequest.getDatasource().getType(), "oracle")) {
                if (Strings.CS.contains(resultSet.getString(5), "nextval") || Strings.CI.equals(resultSet.getString(5), "GENERATED ALWAYS AS IDENTITY")) {
                    tableField.setAutoIncrement(true);
                }
            } else {
                tableField.setAutoIncrement(resultSet.getInt(5) > 0);
            }
        } catch (Exception e) {
        }
        try {
            tableField.setTypeNumber(tableTypeMap.get(StringUtils.lowerCase(tableField.getOriginName())));
        } catch (Exception e) {
        }
        return tableField;
    }

    /**
     * 初始化一组数据源 schema 到 Calcite 连接中，并返回可复用的 Calcite 连接
     */
    public Connection initConnection(Map<Long, DatasourceSchemaDTO> dsMap) throws SQLException {
        Connection connection = take();
        CalciteConnection calciteConnection = null;
        calciteConnection = connection.unwrap(CalciteConnection.class);
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDsList(dsMap);
        buildSchema(datasourceRequest, calciteConnection);
        return connection;
    }

    /**
     * 注册内置 JDBC 驱动，无法加载的驱动会跳过，避免影响其他可用数据源
     */
    private void registerDriver() {
        for (String driverClass : getDriver()) {
            try {
                Driver driver = (Driver) extendedJdbcClassLoader.loadClass(driverClass).newInstance();
                DriverManager.registerDriver(new DriverShim(driver));
            } catch (Exception e) {
                LogUtil.debug("Skip unavailable datasource driver: " + driverClass);
            }
        }
    }

    /**
     * 创建 Calcite 连接，并设置解析、函数和空值排序等全局查询行为
     */
    private Connection getCalciteConnection() {
        registerDriver();
        Properties info = new Properties();
        info.setProperty(CalciteConnectionProperty.LEX.camelName(), "JAVA");
        info.setProperty(CalciteConnectionProperty.FUN.camelName(), "all");
        info.setProperty(CalciteConnectionProperty.CASE_SENSITIVE.camelName(), "false");
        info.setProperty(CalciteConnectionProperty.PARSER_FACTORY.camelName(), "org.apache.calcite.sql.parser.impl.SqlParserImpl#FACTORY");
        info.setProperty(CalciteConnectionProperty.DEFAULT_NULL_COLLATION.camelName(), NullCollation.LAST.name());
        info.setProperty("remarks", "true");
        Connection connection = null;
        try {
            Class.forName("org.apache.calcite.jdbc.Driver");
            connection = DriverManager.getConnection("jdbc:calcite:", info);
        } catch (Exception e) {
            CrestException.throwException(e.getMessage());
        }
        return connection;
    }

    /**
     * 为连接池写入驱动类、类加载器和经过安全校验的 JDBC URL
     */
    private void applyDatasourceUrl(BasicDataSource dataSource, DatasourceSchemaDTO ds, DatasourceConfiguration configuration) {
        CoreDriver customDriver = resolveCustomDriver(ds.getType(), configuration.getCustomDriver());
        String driverClassName = JdbcUrlSecurityPolicy.resolveDriverClass(ds.getType(), configuration.getDriver(), configuration.getCustomDriver(), customDriver);
        configuration.setDriver(driverClassName);
        dataSource.setDriverClassName(driverClassName);
        if (!JdbcUrlSecurityPolicy.isDefaultCustomDriver(configuration.getCustomDriver())) {
            dataSource.setDriverClassLoader(getCustomJdbcClassLoader(customDriver));
        }
        dataSource.setUrl(JdbcUrlSecurityPolicy.validate(ds.getType(), configuration.getJdbc(), configuration.getExtraParams()));
    }

    /**
     * 构建或刷新 Calcite root schema 下的数据源子 schema，并为每个数据源创建独立连接池
     */
    private synchronized SchemaPlus buildSchema(DatasourceRequest datasourceRequest, CalciteConnection calciteConnection) {
        SchemaPlus rootSchema = calciteConnection.getRootSchema();
        Map<Long, DatasourceSchemaDTO> dsList = datasourceRequest.getDsList();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsList.entrySet()) {
            DatasourceSchemaDTO ds = next.getValue();
            if (StringUtils.isBlank(ds.getConfiguration())) {
                LogUtil.info("Skip datasource pool with empty configuration: " + ds.getName());
                continue;
            }
            try {
                    BasicDataSource dataSource = new BasicDataSource();
                    dataSource.setMaxWaitMillis(5 * 1000);
                    dataSource.setTestWhileIdle(true);
                    dataSource.setTestOnBorrow(true);
                    dataSource.setTestOnReturn(true);
                    dataSource.setTimeBetweenEvictionRunsMillis(60 * 1000);
                    dataSource.setValidationQuery("select 1");
                    dataSource.setValidationQueryTimeout(5);
                    Schema schema = null;
                    DatasourceConfiguration configuration = null;
                    DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(ds.getType());
                    try {
                        if (rootSchema.getSubSchema(ds.getSchemaAlias()) != null) {
                            JdbcSchema jdbcSchema = rootSchema.getSubSchema(ds.getSchemaAlias()).unwrap(JdbcSchema.class);
                            BasicDataSource basicDataSource = (BasicDataSource) jdbcSchema.getDataSource();
                            basicDataSource.close();
                            removeSubSchema(rootSchema, ds.getSchemaAlias());
                        }
                        switch (datasourceType) {
                            case mysql:
                            case obMysql:
                            case mongo:
                            case mariadb:
                            case TiDB:
                            case StarRocks:
                            case doris:
                                configuration = datasourceType == DatasourceConfiguration.DatasourceType.obMysql
                                        ? JsonUtil.parseObject(ds.getConfiguration(), ObMysql.class)
                                        : JsonUtil.parseObject(ds.getConfiguration(), Mysql.class);
                                configuration = requireDatasourceConfiguration(configuration);
                                if (StringUtils.isNotBlank(configuration.getUsername())) {
                                    dataSource.setUsername(configuration.getUsername());
                                }
                                if (StringUtils.isNotBlank(configuration.getPassword())) {
                                    dataSource.setPassword(configuration.getPassword());
                                }
                                dataSource.setDefaultQueryTimeout(Integer.valueOf(configuration.getQueryTimeout()));
                                dataSource.setInitialSize(configuration.getInitialPoolSize());
                                dataSource.setMaxTotal(configuration.getMaxPoolSize());
                                dataSource.setMinIdle(configuration.getMinPoolSize());
                                startSshSession(configuration, null, ds.getId());
                                applyDatasourceUrl(dataSource, ds, configuration);
                                schema = JdbcSchema.create(rootSchema, ds.getSchemaAlias(), dataSource, null, configuration.getDataBase());
                                rootSchema.add(ds.getSchemaAlias(), schema);
                                break;
                            case impala:
                                configuration = JsonUtil.parseObject(ds.getConfiguration(), Impala.class);
                                configuration = requireDatasourceConfiguration(configuration);
                                if (StringUtils.isNotBlank(configuration.getUsername())) {
                                    dataSource.setUsername(configuration.getUsername());
                                }
                                if (StringUtils.isNotBlank(configuration.getPassword())) {
                                    dataSource.setPassword(configuration.getPassword());
                                }
                                dataSource.setInitialSize(configuration.getInitialPoolSize());
                                dataSource.setMaxTotal(configuration.getMaxPoolSize());
                                dataSource.setMinIdle(configuration.getMinPoolSize());
                                dataSource.setDefaultQueryTimeout(Integer.valueOf(configuration.getQueryTimeout()));
                                startSshSession(configuration, null, ds.getId());
                                applyDatasourceUrl(dataSource, ds, configuration);
                                schema = JdbcSchema.create(rootSchema, ds.getSchemaAlias(), dataSource, null, configuration.getDataBase());
                                rootSchema.add(ds.getSchemaAlias(), schema);
                                break;
                            case sqlServer:
                                configuration = JsonUtil.parseObject(ds.getConfiguration(), Sqlserver.class);
                                configuration = requireDatasourceConfiguration(configuration);
                                if (StringUtils.isNotBlank(configuration.getUsername())) {
                                    dataSource.setUsername(configuration.getUsername());
                                }
                                if (StringUtils.isNotBlank(configuration.getPassword())) {
                                    dataSource.setPassword(configuration.getPassword());
                                }
                                dataSource.setInitialSize(configuration.getInitialPoolSize());
                                dataSource.setMaxTotal(configuration.getMaxPoolSize());
                                dataSource.setMinIdle(configuration.getMinPoolSize());
                                dataSource.setDefaultQueryTimeout(Integer.valueOf(configuration.getQueryTimeout()));
                                startSshSession(configuration, null, ds.getId());
                                applyDatasourceUrl(dataSource, ds, configuration);
                                schema = JdbcSchema.create(rootSchema, ds.getSchemaAlias(), dataSource, null, configuration.getSchema());
                                rootSchema.add(ds.getSchemaAlias(), schema);
                                break;
                            case oracle:
                            case obOracle:
                                dataSource.setValidationQuery("SELECT 1 FROM DUAL");
                                configuration = parseOracleLikeConfiguration(ds.getConfiguration(), ds.getType());
                                configuration = requireDatasourceConfiguration(configuration);
                                if (StringUtils.isNotBlank(configuration.getUsername())) {
                                    dataSource.setUsername(configuration.getUsername());
                                }
                                if (StringUtils.isNotBlank(configuration.getPassword())) {
                                    dataSource.setPassword(configuration.getPassword());
                                }
                                dataSource.setInitialSize(configuration.getInitialPoolSize());
                                dataSource.setMaxTotal(configuration.getMaxPoolSize());
                                dataSource.setMinIdle(configuration.getMinPoolSize());
                                dataSource.setDefaultQueryTimeout(Integer.valueOf(configuration.getQueryTimeout()));
                                startSshSession(configuration, null, ds.getId());
                                applyDatasourceUrl(dataSource, ds, configuration);
                                schema = JdbcSchema.create(rootSchema, ds.getSchemaAlias(), dataSource, null, configuration.getSchema());
                                rootSchema.add(ds.getSchemaAlias(), schema);
                                break;
                            case db2:
                                configuration = JsonUtil.parseObject(ds.getConfiguration(), Db2.class);
                                configuration = requireDatasourceConfiguration(configuration);
                                dataSource.setValidationQuery("select 1 from syscat.tables  WHERE TABSCHEMA ='CREST_SCHEMA' AND \"TYPE\" = 'T'".replace("CREST_SCHEMA", configuration.getSchema()));
                                if (StringUtils.isNotBlank(configuration.getUsername())) {
                                    dataSource.setUsername(configuration.getUsername());
                                }
                                if (StringUtils.isNotBlank(configuration.getPassword())) {
                                    dataSource.setPassword(configuration.getPassword());
                                }
                                dataSource.setInitialSize(configuration.getInitialPoolSize());
                                dataSource.setMaxTotal(configuration.getMaxPoolSize());
                                dataSource.setMinIdle(configuration.getMinPoolSize());
                                dataSource.setDefaultQueryTimeout(Integer.valueOf(configuration.getQueryTimeout()));
                                startSshSession(configuration, null, ds.getId());
                                applyDatasourceUrl(dataSource, ds, configuration);
                                schema = JdbcSchema.create(rootSchema, ds.getSchemaAlias(), dataSource, null, configuration.getSchema());
                                rootSchema.add(ds.getSchemaAlias(), schema);
                                break;
                            case ck:
                                configuration = JsonUtil.parseObject(ds.getConfiguration(), CK.class);
                                configuration = requireDatasourceConfiguration(configuration);
                                if (StringUtils.isNotBlank(configuration.getUsername())) {
                                    dataSource.setUsername(configuration.getUsername());
                                }
                                if (StringUtils.isNotBlank(configuration.getPassword())) {
                                    dataSource.setPassword(configuration.getPassword());
                                }
                                dataSource.setInitialSize(configuration.getInitialPoolSize());
                                dataSource.setMaxTotal(configuration.getMaxPoolSize());
                                dataSource.setMinIdle(configuration.getMinPoolSize());
                                dataSource.setDefaultQueryTimeout(Integer.valueOf(configuration.getQueryTimeout()));
                                startSshSession(configuration, null, ds.getId());
                                applyDatasourceUrl(dataSource, ds, configuration);
                                schema = JdbcSchema.create(rootSchema, ds.getSchemaAlias(), dataSource, null, configuration.getDataBase());
                                rootSchema.add(ds.getSchemaAlias(), schema);
                                break;
                            case pg:
                                configuration = JsonUtil.parseObject(ds.getConfiguration(), Pg.class);
                                configuration = requireDatasourceConfiguration(configuration);
                                if (StringUtils.isNotBlank(configuration.getUsername())) {
                                    dataSource.setUsername(configuration.getUsername());
                                }
                                if (StringUtils.isNotBlank(configuration.getPassword())) {
                                    dataSource.setPassword(configuration.getPassword());
                                }
                                dataSource.setInitialSize(configuration.getInitialPoolSize());
                                dataSource.setMaxTotal(configuration.getMaxPoolSize());
                                dataSource.setMinIdle(configuration.getMinPoolSize());
                                dataSource.setDefaultQueryTimeout(Integer.valueOf(configuration.getQueryTimeout()));
                                startSshSession(configuration, null, ds.getId());
                                applyDatasourceUrl(dataSource, ds, configuration);
                                schema = JdbcSchema.create(rootSchema, ds.getSchemaAlias(), dataSource, null, configuration.getSchema());
                                rootSchema.add(ds.getSchemaAlias(), schema);
                                break;
                            case redshift:
                                configuration = JsonUtil.parseObject(ds.getConfiguration(), Redshift.class);
                                configuration = requireDatasourceConfiguration(configuration);
                                if (StringUtils.isNotBlank(configuration.getUsername())) {
                                    dataSource.setUsername(configuration.getUsername());
                                }
                                if (StringUtils.isNotBlank(configuration.getPassword())) {
                                    dataSource.setPassword(configuration.getPassword());
                                }
                                dataSource.setInitialSize(configuration.getInitialPoolSize());
                                dataSource.setMaxTotal(configuration.getMaxPoolSize());
                                dataSource.setMinIdle(configuration.getMinPoolSize());
                                dataSource.setDefaultQueryTimeout(Integer.valueOf(configuration.getQueryTimeout()));
                                startSshSession(configuration, null, ds.getId());
                                applyDatasourceUrl(dataSource, ds, configuration);
                                schema = JdbcSchema.create(rootSchema, ds.getSchemaAlias(), dataSource, null, configuration.getSchema());
                                rootSchema.add(ds.getSchemaAlias(), schema);
                                break;
                            case h2:
                                configuration = JsonUtil.parseObject(ds.getConfiguration(), H2.class);
                                configuration = requireDatasourceConfiguration(configuration);
                                if (StringUtils.isNotBlank(configuration.getUsername())) {
                                    dataSource.setUsername(configuration.getUsername());
                                }
                                if (StringUtils.isNotBlank(configuration.getPassword())) {
                                    dataSource.setPassword(configuration.getPassword());
                                }
                                dataSource.setInitialSize(configuration.getInitialPoolSize());
                                dataSource.setMaxTotal(configuration.getMaxPoolSize());
                                dataSource.setMinIdle(configuration.getMinPoolSize());
                                dataSource.setDefaultQueryTimeout(Integer.valueOf(configuration.getQueryTimeout()));
                                startSshSession(configuration, null, ds.getId());
                                applyDatasourceUrl(dataSource, ds, configuration);
                                schema = JdbcSchema.create(rootSchema, ds.getSchemaAlias(), dataSource, null, configuration.getDataBase());
                                rootSchema.add(ds.getSchemaAlias(), schema);
                                break;
                            default:
                                configuration = JsonUtil.parseObject(ds.getConfiguration(), Mysql.class);
                                configuration = requireDatasourceConfiguration(configuration);
                                if (StringUtils.isNotBlank(configuration.getUsername())) {
                                    dataSource.setUsername(configuration.getUsername());
                                }
                                if (StringUtils.isNotBlank(configuration.getPassword())) {
                                    dataSource.setPassword(configuration.getPassword());
                                }
                                dataSource.setInitialSize(configuration.getInitialPoolSize());
                                dataSource.setMaxTotal(configuration.getMaxPoolSize());
                                dataSource.setMinIdle(configuration.getMinPoolSize());
                                dataSource.setDefaultQueryTimeout(Integer.valueOf(configuration.getQueryTimeout()));
                                startSshSession(configuration, null, ds.getId());
                                applyDatasourceUrl(dataSource, ds, configuration);
                                schema = JdbcSchema.create(rootSchema, ds.getSchemaAlias(), dataSource, null, configuration.getDataBase());
                                rootSchema.add(ds.getSchemaAlias(), schema);
                        }
                } catch (Exception e) {
                    LogUtil.error("Skip unavailable datasource pool: " + ds.getName() + ", " + e.getMessage(), e);
                }
            } catch (Exception e) {
                LogUtil.error("Init datasource pool failed: " + ds.getName() + ", " + e.getMessage(), e);
            }
        }
        return rootSchema;
    }

    /**
     * 将 Calcite 查询结果转换为字符串二维数组，保持与普通 JDBC 预览返回形态一致
     */
    private List<String[]> dataResult(ResultSet rs) {
        List<String[]> list = new LinkedList<>();
        try {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            while (rs.next()) {
                String[] row = new String[columnCount];
                for (int j = 0; j < columnCount; j++) {
                    int columnType = metaData.getColumnType(j + 1);
                    switch (columnType) {
                        case Types.DATE:
                            if (rs.getDate(j + 1) != null) {
                                row[j] = rs.getDate(j + 1).toString();
                            }
                            break;
                        case Types.BOOLEAN:
                            row[j] = rs.getBoolean(j + 1) ? "true" : "false";
                            break;
                        default:
                            if (metaData.getColumnTypeName(j + 1).toLowerCase().equalsIgnoreCase("blob")) {
                                row[j] = rs.getBlob(j + 1) == null ? "" : rs.getBlob(j + 1).toString();
                            } else {
                                row[j] = rs.getString(j + 1);
                            }
                            break;
                    }
                }
                list.add(row);
            }
        } catch (Exception e) {
            CrestException.throwException(e.getMessage());
        }
        return list;
    }

    /**
     * 按数据库类型生成字段元数据查询 SQL，返回列顺序固定为字段名、类型、注释、主键和自增标识
     */
    private String getTableFiledSql(DatasourceRequest datasourceRequest) {
        String sql = "";
        DatasourceConfiguration configuration = null;
        String database = "";
        DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(datasourceRequest.getDatasource().getType());
        switch (datasourceType) {
            case StarRocks:
            case doris:
                configuration = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), Mysql.class);
                if (StringUtils.isEmpty(configuration.getUrlType()) || configuration.getUrlType().equalsIgnoreCase("hostName")) {
                    database = configuration.getDataBase();
                } else {
                    database = databaseFromJdbcUrl(configuration.getJdbcUrl());
                }
                if (database.contains(".")) {
                    sql = "select * from " + quoteQualifiedIdentifier(datasourceRequest.getTable()) + " limit 0 offset 0 ";
                } else {
                    sql = String.format("SELECT COLUMN_NAME,DATA_TYPE,COLUMN_COMMENT,IF(COLUMN_KEY='PRI',1,0),IF(EXTRA LIKE '%%auto_increment%%',1,0) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = %s AND TABLE_NAME = %s", sqlLiteral(database), sqlLiteral(datasourceRequest.getTable()));
                }
                break;
            case mysql:
            case obMysql:
            case mongo:
            case mariadb:
            case TiDB:
                configuration = datasourceType == DatasourceConfiguration.DatasourceType.obMysql
                        ? JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), ObMysql.class)
                        : JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), Mysql.class);
                if (StringUtils.isEmpty(configuration.getUrlType()) || configuration.getUrlType().equalsIgnoreCase("hostName")) {
                    database = configuration.getDataBase();
                } else {
                    database = databaseFromJdbcUrl(configuration.getJdbcUrl());
                }
                sql = String.format("SELECT COLUMN_NAME,DATA_TYPE,COLUMN_COMMENT,IF(COLUMN_KEY='PRI',1,0),IF(EXTRA LIKE '%%auto_increment%%',1,0) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = %s AND TABLE_NAME = %s", sqlLiteral(database), sqlLiteral(datasourceRequest.getTable()));
                break;
            case oracle:
            case obOracle:
                configuration = parseOracleLikeConfiguration(datasourceRequest.getDatasource().getConfiguration(), datasourceRequest.getDatasource().getType());
                if (StringUtils.isEmpty(configuration.getSchema())) {
                    CrestException.throwException(Translator.get("i18n_schema_is_empty"));
                }
                sql = String.format("""
                        SELECT tc.COLUMN_NAME AS ColumnName,
                               tc.DATA_TYPE,
                               cc.COMMENTS,
                               CASE
                                   WHEN ac.COLUMN_NAME IS NOT NULL THEN 1
                                   ELSE 0
                                   END,
                               tc.DATA_DEFAULT
                        FROM ALL_TAB_COLUMNS tc
                                 LEFT JOIN (SELECT cols.OWNER,
                                                   cols.TABLE_NAME,
                                                   cols.COLUMN_NAME
                                            FROM ALL_CONSTRAINTS cons
                                                     JOIN
                                                 ALL_CONS_COLUMNS cols
                                                 ON cons.OWNER = cols.OWNER
                                                     AND cons.CONSTRAINT_NAME = cols.CONSTRAINT_NAME
                                            WHERE cons.TABLE_NAME = %s
                                              AND cons.CONSTRAINT_TYPE = 'P') ac
                                           ON tc.OWNER = ac.OWNER
                                               AND tc.TABLE_NAME = ac.TABLE_NAME
                                               AND tc.COLUMN_NAME = ac.COLUMN_NAME
                                 LEFT JOIN ALL_COL_COMMENTS cc
                                           ON tc.owner = cc.owner AND tc.table_name = cc.table_name AND tc.column_name = cc.column_name
                        WHERE tc.TABLE_NAME = %s
                          AND tc.OWNER = %s
                        ORDER BY tc.TABLE_NAME, tc.COLUMN_ID
                        """, sqlLiteral(datasourceRequest.getTable()), sqlLiteral(datasourceRequest.getTable()), sqlLiteral(configuration.getSchema()));
                break;
            case db2:
                configuration = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), Db2.class);
                if (StringUtils.isEmpty(configuration.getSchema())) {
                    CrestException.throwException(Translator.get("i18n_schema_is_empty"));
                }
                sql = String.format("SELECT COLNAME, TYPENAME, REMARKS, 0, 0 FROM SYSCAT.COLUMNS WHERE TABSCHEMA = %s AND TABNAME = %s ", sqlLiteral(configuration.getSchema()), sqlLiteral(datasourceRequest.getTable()));
                break;
            case sqlServer:
                configuration = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), Sqlserver.class);
                if (StringUtils.isEmpty(configuration.getSchema())) {
                    CrestException.throwException(Translator.get("i18n_schema_is_empty"));
                }

                sql = String.format("""
                        SELECT
                            c.name,
                            t.name,
                            CAST(ep.value AS NVARCHAR(4000)),
                            CASE
                                WHEN pk.column_id IS NOT NULL THEN 1
                                ELSE 0
                                END,
                            COLUMNPROPERTY(c.object_id, c.name, 'IsIdentity')
                        FROM sys.columns AS c
                                 INNER JOIN sys.objects AS o ON c.object_id = o.object_id
                                 INNER JOIN sys.schemas AS s ON o.schema_id = s.schema_id
                                 LEFT JOIN sys.types AS t ON c.user_type_id = t.user_type_id
                                 LEFT JOIN sys.extended_properties AS ep
                                           ON c.object_id = ep.major_id
                                               AND c.column_id = ep.minor_id
                                               AND ep.name = 'MS_Description'
                                 LEFT JOIN (
                            SELECT ic.object_id, ic.column_id
                            FROM sys.indexes i
                                     INNER JOIN sys.index_columns ic
                                                ON i.object_id = ic.object_id
                                                    AND i.index_id = ic.index_id
                            WHERE i.is_primary_key = 1
                        ) pk ON c.object_id = pk.object_id AND c.column_id = pk.column_id
                        WHERE o.name = %s
                          AND s.name = %s
                        ORDER BY c.column_id
                        """, sqlLiteral(datasourceRequest.getTable()), sqlLiteral(configuration.getSchema()));
                break;
            case pg:
                configuration = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), Pg.class);
                if (StringUtils.isEmpty(configuration.getSchema())) {
                    CrestException.throwException(Translator.get("i18n_schema_is_empty"));
                }
                sql = String.format("""
                        SELECT a.attname     AS ColumnName,
                               t.typname,
                               b.description AS ColumnDescription,
                               CASE
                                   WHEN d.indisprimary THEN 1
                                   ELSE 0
                                   END,
                               CASE
                                   WHEN pg_get_expr(ad.adbin, ad.adrelid) LIKE 'nextval%%' THEN 1
                        """ + (datasourceRequest.getDsVersion() > 9 ? """
                                   WHEN a.attidentity = 'd' THEN 1
                                   WHEN a.attidentity = 'a' THEN 1
                        """ : "") + """
                                   ELSE 0
                                   END
                        FROM pg_class c
                                 JOIN pg_attribute a ON a.attrelid = c.oid
                                 LEFT JOIN pg_attrdef ad ON a.attrelid = ad.adrelid AND a.attnum = ad.adnum
                                 LEFT JOIN pg_description b ON a.attrelid = b.objoid AND a.attnum = b.objsubid
                                 JOIN pg_type t ON a.atttypid = t.oid
                                 LEFT JOIN pg_index d ON d.indrelid = a.attrelid AND d.indisprimary AND a.attnum = ANY (d.indkey)
                        where c.relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = %s)
                          AND c.relname = %s
                          AND a.attnum > 0
                          AND NOT a.attisdropped
                        ORDER BY a.attnum;
                        """, sqlLiteral(configuration.getSchema()), sqlLiteral(datasourceRequest.getTable()));
                break;
            case redshift:
                configuration = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), CK.class);
                sql = String.format("SELECT\n" + "    a.attname AS ColumnName,\n" + "    t.typname,\n" + "    b.description AS ColumnDescription,\n" + "    0, 0\n" + "FROM\n" + "    pg_class c\n" + "    JOIN pg_attribute a ON a.attrelid = c.oid\n" + "    LEFT JOIN pg_description b ON a.attrelid = b.objoid AND a.attnum = b.objsubid\n" + "    JOIN pg_type t ON a.atttypid = t.oid\n" + "WHERE\n" + "    c.relname = %s\n" + "    AND a.attnum > 0\n" + "    AND NOT a.attisdropped\n" + "ORDER BY\n" + "    a.attnum\n" + "   ", sqlLiteral(datasourceRequest.getTable()));
                break;
            case ck:
                configuration = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), CK.class);

                if (StringUtils.isEmpty(configuration.getUrlType()) || configuration.getUrlType().equalsIgnoreCase("hostName")) {
                    database = configuration.getDataBase();
                } else {
                    database = databaseFromJdbcUrl(configuration.getJdbcUrl());
                }
                sql = String.format(" SELECT\n" + "    name,\n" + "    type,\n" + "    comment,\n" + "    0, 0\n" + "FROM\n" + "    system.columns\n" + "WHERE\n" + "    database = %s  \n" + "    AND table = %s ", sqlLiteral(database), sqlLiteral(datasourceRequest.getTable()));
                break;
            case impala:
                sql = String.format("DESCRIBE %s", quoteQualifiedIdentifier(datasourceRequest.getTable()));
                break;
            case h2:
                sql = String.format("SELECT COLUMN_NAME, DATA_TYPE, REMARKS, 0, 0 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = %s", sqlLiteral(datasourceRequest.getTable()));
                break;
            default:
                break;
        }

        return sql;
    }

    /**
     * 按数据库类型生成表清单查询 SQL，多种对象类型会拆成多条查询顺序执行
     */
    private List<QueryAndParams> tablesSql(DatasourceRequest datasourceRequest) throws CrestException {
        List<QueryAndParams> tableSqls = new ArrayList<>();
        DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(datasourceRequest.getDatasource().getType());
        DatasourceConfiguration configuration = null;
        String database = "";
        switch (datasourceType) {
            case StarRocks:
            case doris:
                configuration = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), Mysql.class);
                if (StringUtils.isEmpty(configuration.getUrlType()) || configuration.getUrlType().equalsIgnoreCase("hostName")) {
                    database = configuration.getDataBase();
                } else {
                    database = databaseFromJdbcUrl(configuration.getJdbcUrl());
                }
                if (database.contains(".")) {
                    tableSqls.add(new QueryAndParams("show tables"));
                } else {
                    tableSqls.add(new QueryAndParams("SELECT TABLE_NAME,TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ?", database));
                }
                break;
            case mongo:
                tableSqls.add(new QueryAndParams("show tables"));
                break;
            case mysql:
            case obMysql:
            case mariadb:
            case TiDB:
                configuration = datasourceType == DatasourceConfiguration.DatasourceType.obMysql
                        ? JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), ObMysql.class)
                        : JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), Mysql.class);
                if (StringUtils.isEmpty(configuration.getUrlType()) || configuration.getUrlType().equalsIgnoreCase("hostName")) {
                    database = configuration.getDataBase();
                } else {
                    database = databaseFromJdbcUrl(configuration.getJdbcUrl());
                }
                tableSqls.add(new QueryAndParams("SELECT TABLE_NAME,TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ?", database));
                break;
            case oracle:
            case obOracle:
                configuration = parseOracleLikeConfiguration(datasourceRequest.getDatasource().getConfiguration(), datasourceRequest.getDatasource().getType());
                if (StringUtils.isEmpty(configuration.getSchema())) {
                    CrestException.throwException(Translator.get("i18n_schema_is_empty"));
                }
                tableSqls.add(new QueryAndParams("select table_name, comments, owner from all_tab_comments where owner = ? AND table_type = 'TABLE'", configuration.getSchema()));
                tableSqls.add(new QueryAndParams("select table_name, comments, owner from all_tab_comments where owner = ? AND table_type = 'VIEW'", configuration.getSchema()));
                if (!isObOracle(datasourceRequest.getDatasource().getType())) {
                    tableSqls.add(new QueryAndParams("SELECT \n" + "    m.mview_name,\n" + "    c.comments\n" + "FROM \n" + "    ALL_MVIEWS m\n" + "LEFT JOIN \n" + "    ALL_TAB_COMMENTS c \n" + "ON \n" + "    m.owner = c.owner \n" + "    AND m.mview_name = c.table_name\n" + "    AND c.table_type = 'MATERIALIZED VIEW'\n" + "WHERE m.OWNER = ?", configuration.getSchema()));
                }
                break;
            case db2:
                configuration = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), Db2.class);
                if (StringUtils.isEmpty(configuration.getSchema())) {
                    CrestException.throwException(Translator.get("i18n_schema_is_empty"));
                }
                tableSqls.add(new QueryAndParams("select TABNAME, REMARKS from syscat.tables WHERE TABSCHEMA = ? AND \"TYPE\" = 'T'", configuration.getSchema()));
                break;
            case sqlServer:
                configuration = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), Sqlserver.class);
                if (StringUtils.isEmpty(configuration.getSchema())) {
                    CrestException.throwException(Translator.get("i18n_schema_is_empty"));
                }
                tableSqls.add(new QueryAndParams("SELECT   \n" + "    t.name AS TableName,  \n" + "    ep.value AS TableDescription  \n" + "FROM   \n" + "    sys.tables t  \n" + "LEFT OUTER JOIN   sys.schemas sc ON sc.schema_id =t.schema_id \n" + "LEFT OUTER JOIN   \n" + "    sys.extended_properties ep ON t.object_id = ep.major_id   \n" + "                               AND ep.minor_id = 0   \n" + "                               AND ep.class = 1  \n" + "                               AND ep.name = 'MS_Description'\n" + "where sc.name = ?", configuration.getSchema()));
                tableSqls.add(new QueryAndParams("SELECT   \n" + "    t.name AS TableName,  \n" + "    ep.value AS TableDescription  \n" + "FROM   \n" + "    sys.views t  \n" + "LEFT OUTER JOIN   sys.schemas sc ON sc.schema_id =t.schema_id \n" + "LEFT OUTER JOIN   \n" + "    sys.extended_properties ep ON t.object_id = ep.major_id   \n" + "                               AND ep.minor_id = 0   \n" + "                               AND ep.class = 1  \n" + "                               AND ep.name = 'MS_Description'\n" + "where sc.name = ?", configuration.getSchema()));
                break;
            case pg:
                configuration = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), Pg.class);
                if (StringUtils.isEmpty(configuration.getSchema())) {
                    CrestException.throwException(Translator.get("i18n_schema_is_empty"));
                }
                tableSqls.add(new QueryAndParams("SELECT  \n" + "    relname AS TableName,  \n" + "    obj_description(relfilenode::regclass, 'pg_class') AS TableDescription  \n" + "FROM  \n" + "    pg_class  \n" + "WHERE  \n" + "   relkind in  ('r','p', 'f')  \n" + "    AND relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?) ", configuration.getSchema()));
                tableSqls.add(new QueryAndParams("SELECT \n" + "    c.relname AS view_name,\n" + "    COALESCE(d.description, 'No description provided') AS view_description\n" + "FROM \n" + "    pg_class c\n" + "JOIN \n" + "    pg_namespace n ON c.relnamespace = n.oid\n" + "LEFT JOIN \n" + "    pg_description d ON c.oid = d.objoid\n" + "WHERE \n" + "    c.relkind = 'v'  \n" + "    AND n.nspname = ?", configuration.getSchema()));
                tableSqls.add(new QueryAndParams("SELECT \n" + "    c.relname AS materialized_view_name,\n" + "    COALESCE(d.description, '') AS view_description\n" + "FROM \n" + "    pg_class c\n" + "JOIN \n" + "    pg_namespace n ON c.relnamespace = n.oid\n" + "LEFT JOIN \n" + "    pg_description d ON c.oid = d.objoid\n" + "WHERE \n" + "    c.relkind = 'm' and n.nspname = ?", configuration.getSchema()));
                break;
            case redshift:
                configuration = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), CK.class);
                tableSqls.add(new QueryAndParams("SELECT  \n" + "    relname AS TableName,  \n" + "    obj_description(relfilenode::regclass, 'pg_class') AS TableDescription  \n" + "FROM  \n" + "    pg_class  \n" + "WHERE  \n" + "   relkind in  ('r','p', 'f')  \n" + "    AND relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = ?) ", configuration.getSchema()));
                break;
            case ck:
                configuration = JsonUtil.parseObject(datasourceRequest.getDatasource().getConfiguration(), CK.class);
                if (StringUtils.isEmpty(configuration.getUrlType()) || configuration.getUrlType().equalsIgnoreCase("hostName")) {
                    database = configuration.getDataBase();
                } else {
                    database = databaseFromJdbcUrl(configuration.getJdbcUrl());
                }
                if (datasourceRequest.getDsVersion() < 22) {
                    tableSqls.add(new QueryAndParams("SELECT name, name FROM system.tables where database = ?", database));
                } else {
                    tableSqls.add(new QueryAndParams("SELECT name, comment FROM system.tables where database = ?", database));
                }


                break;
            default:
                tableSqls.add(new QueryAndParams("show tables"));
        }
        return tableSqls;

    }

    /**
     * 创建表清单或字段清单查询使用的 PreparedStatement，并按顺序绑定查询参数
     */
    private PreparedStatement prepareStatement(Connection connection, QueryAndParams queryAndParams, int queryTimeout) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(queryAndParams.sql());
        statement.setQueryTimeout(queryTimeout);
        List<Object> params = queryAndParams.params();
        for (int i = 0; i < params.size(); i++) {
            statement.setObject(i + 1, params.get(i));
        }
        return statement;
    }

    /**
     * 元数据查询和参数的不可变组合，避免把标识符和普通值混在同一字符串处理
     */
    private record QueryAndParams(String sql, List<Object> params) {
        /**
         * 创建无参数元数据查询
         */
        private QueryAndParams(String sql) {
            this(sql, Collections.emptyList());
        }

        /**
         * 创建带参数元数据查询，参数会在 prepareStatement 中按顺序绑定
         */
        private QueryAndParams(String sql, Object... params) {
            this(sql, Arrays.asList(params));
        }
    }

    /**
     * 按数据库类型生成 schema 列表查询 SQL
     */
    private String getSchemaSql(DatasourceDTO datasource) throws CrestException {
        DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(datasource.getType());
        switch (datasourceType) {
            case oracle:
            case obOracle:
                return "select * from all_users";
            case sqlServer:
                return "select name from sys.schemas;";
            case db2:
                DatasourceConfiguration configuration = JsonUtil.parseObject(datasource.getConfiguration(), Db2.class);
                return "select SCHEMANAME from syscat.SCHEMATA   WHERE \"DEFINER\" ='USER'".replace("USER", configuration.getUsername().toUpperCase());
            case pg:
                return "SELECT nspname FROM pg_namespace;";
            case redshift:
                return "SELECT nspname FROM pg_namespace;";
            default:
                return "show tables;";
        }
    }

    /**
     * 创建普通 Statement 并设置查询超时，用于无参数 SQL 或数据库会话设置语句
     */
    @Override
    public Statement getStatement(Connection connection, int queryTimeout) {
        if (connection == null) {
            CrestException.throwException("Failed to get connection!");
        }
        Statement stat = null;
        try {
            stat = connection.createStatement();
            stat.setQueryTimeout(queryTimeout);
        } catch (Exception e) {
            CrestException.throwException(e.getMessage());
        }
        return stat;
    }

    /**
     * 创建预编译语句，默认不读取自增主键
     */
    public Statement getPreparedStatement(Connection connection, int queryTimeout, String sql, List<TableFieldWithValue> values) throws Exception {
        return getPreparedStatement(connection, queryTimeout, sql, values, null, null);
    }

    /**
     * 根据是否存在参数选择 PreparedStatement 或普通 Statement，并对单语句边界做安全校验
     */
    @SuppressWarnings("java/sql-injection")
    public Statement getPreparedStatement(Connection connection, int queryTimeout, String sql, List<TableFieldWithValue> values, String autoIncrementPkName, DatasourceConfiguration datasourceConfiguration) throws Exception {
        if (connection == null) {
            throw new Exception("Failed to get connection!");
        }
        if (CollectionUtils.isNotEmpty(values)) {
            PreparedStatement stat = null;
            String pkName = autoIncrementPkName;
            try {
                if (StringUtils.isNotBlank(autoIncrementPkName)) {
                    String[] generatedColumns = {pkName};
                    stat = connection.prepareStatement(SqlSecurityPolicy.validateSingleStatement(sql), generatedColumns);
                } else {
                    stat = connection.prepareStatement(SqlSecurityPolicy.validateSingleStatement(sql));
                }
                stat.setQueryTimeout(queryTimeout);
            } catch (Exception e) {
                CrestException.throwException(e.getMessage());
            }
            return stat;
        } else {
            return getStatement(connection, queryTimeout);
        }
    }

    /**
     * 判断是否使用默认驱动类加载器，空值和 default 都走内置驱动路径
     */
    protected boolean isDefaultClassLoader(String customDriver) {
        return StringUtils.isEmpty(customDriver) || customDriver.equalsIgnoreCase("default");
    }

    /**
     * 获取指定自定义驱动的类加载器，驱动类变化时会重建缓存
     */
    protected ExtendedJdbcClassLoader getCustomJdbcClassLoader(CoreDriver coreDriver) {
        if (coreDriver == null) {
            CrestException.throwException("Can not found custom Driver");
        }
        ExtendedJdbcClassLoader customJdbcClassLoader = customJdbcClassLoaders.get(coreDriver.getId());
        if (customJdbcClassLoader == null) {
            return addCustomJdbcClassLoader(coreDriver);
        } else {
            if (StringUtils.isNotEmpty(customJdbcClassLoader.getDriver()) && customJdbcClassLoader.getDriver().equalsIgnoreCase(coreDriver.getDriverClass())) {
                return customJdbcClassLoader;
            } else {
                customJdbcClassLoaders.remove(coreDriver.getId());
                return addCustomJdbcClassLoader(coreDriver);
            }
        }
    }

    /**
     * 加载自定义驱动目录下的 jar 包，并缓存成独立类加载器
     */
    private synchronized ExtendedJdbcClassLoader addCustomJdbcClassLoader(CoreDriver coreDriver) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        while (classLoader.getParent() != null) {
            classLoader = classLoader.getParent();
            if (classLoader.toString().contains("ExtClassLoader")) {
                break;
            }
        }
        try {
            ExtendedJdbcClassLoader customJdbcClassLoader = new ExtendedJdbcClassLoader(new URL[]{new File(CUSTOM_PATH + coreDriver.getId()).toURI().toURL()}, classLoader);
            customJdbcClassLoader.setDriver(coreDriver.getDriverClass());
            File file = new File(CUSTOM_PATH + coreDriver.getId());
            File[] array = file.listFiles();
            Optional.ofNullable(array).ifPresent(files -> {
                for (File tmp : array) {
                    if (tmp.getName().endsWith(".jar")) {
                        try {
                            customJdbcClassLoader.addFile(tmp);
                        } catch (IOException e) {
                            io.crest.utils.LogUtil.error(e.getMessage(), e);
                        }
                    }
                }
            });
            customJdbcClassLoaders.put(coreDriver.getId(), customJdbcClassLoader);
            return customJdbcClassLoader;
        } catch (Exception e) {
            CrestException.throwException(e.getMessage());
        }
        return null;
    }

    private volatile Connection connection = null;

    /**
     * 启动时为可用连接型数据源和引擎数据源异步初始化 Calcite schema
     */
    public void initConnectionPool() {
        LogUtil.info("Begin to init datasource pool...");
        QueryWrapper<CoreDatasource> datasourceQueryWrapper = new QueryWrapper();
        List<CoreDatasource> coreDatasources = coreDatasourceMapper.selectList(datasourceQueryWrapper).stream()
                .filter(coreDatasource -> !Arrays.asList("folder", "API", "Excel", "ExcelRemote").contains(coreDatasource.getType()))
                .filter(coreDatasource -> Strings.CI.equals(coreDatasource.getStatus(), "Success"))
                .collect(Collectors.toList());
        CoreDatasource engine = engineManage.engineDatasource();
        if (engine != null) {
            coreDatasources.add(engine);
        }

        for (CoreDatasource coreDatasource : coreDatasources) {
            Map<Long, DatasourceSchemaDTO> dsMap = new HashMap<>();
            DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
            BeanUtils.copyBean(datasourceSchemaDTO, coreDatasource);
            datasourceSchemaDTO.setSchemaAlias(String.format(SQLConstants.SCHEMA, datasourceSchemaDTO.getId()));
            dsMap.put(datasourceSchemaDTO.getId(), datasourceSchemaDTO);
            commonThreadPool.addTask(() -> {
                try {
                    connection = initConnection(dsMap);
                } catch (Exception ignore) {
                }
            });
        }
        LogUtil.info("dsMap size..." + coreDatasources.size());

    }

    /**
     * 在数据源新增或保存后刷新对应的 Calcite schema 和底层连接池
     */
    public void update(DatasourceDTO datasourceDTO) throws CrestException {
        DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
        BeanUtils.copyBean(datasourceSchemaDTO, datasourceDTO);
        datasourceSchemaDTO.setSchemaAlias(String.format(SQLConstants.SCHEMA, datasourceSchemaDTO.getId()));
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDsList(Map.of(datasourceSchemaDTO.getId(), datasourceSchemaDTO));
        try {
            Connection conn = (connection != null) ? connection : getCalciteConnection();
            CalciteConnection calciteConnection = conn.unwrap(CalciteConnection.class);
            SchemaPlus rootSchema = buildSchema(datasourceRequest, calciteConnection);
        } catch (Exception e) {
            CrestException.throwException(e.getMessage());
        }
    }

    /**
     * 连接状态校验成功后确保连接池已注册，并在 SSH 配置变更时重建隧道
     */
    public void updateDsPoolAfterCheckStatus(DatasourceDTO datasourceDTO) throws CrestException {
        DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
        BeanUtils.copyBean(datasourceSchemaDTO, datasourceDTO);
        datasourceSchemaDTO.setSchemaAlias(String.format(SQLConstants.SCHEMA, datasourceSchemaDTO.getId()));
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDsList(Map.of(datasourceSchemaDTO.getId(), datasourceSchemaDTO));
        try {
            CalciteConnection calciteConnection = take().unwrap(CalciteConnection.class);
            SchemaPlus rootSchema = calciteConnection.getRootSchema();
            if (rootSchema.getSubSchema(datasourceSchemaDTO.getSchemaAlias()) == null) {
                buildSchema(datasourceRequest, calciteConnection);
            }
            DatasourceConfiguration configuration = parseDatasourceConfiguration(datasourceDTO.getConfiguration(), datasourceDTO.getType());
            if (configuration.isUseSSH()) {
                Session session = Provider.getSessions().get(datasourceDTO.getId());
                if (session != null) {
                    session.disconnect();
                }
                Provider.getSessions().remove(datasourceDTO.getId());
                startSshSession(configuration, null, datasourceDTO.getId());
            }
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            CrestException.throwException(e.getMessage());
        }
    }

    /**
     * 删除数据源对应的 Calcite schema、连接池和 SSH 隧道缓存
     */
    public void delete(CoreDatasource datasource) throws CrestException {
        DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
        BeanUtils.copyBean(datasourceSchemaDTO, datasource);
        datasourceSchemaDTO.setSchemaAlias(String.format(SQLConstants.SCHEMA, datasourceSchemaDTO.getId()));
        try {
            Connection conn = (connection != null) ? connection : getCalciteConnection();
            CalciteConnection calciteConnection = conn.unwrap(CalciteConnection.class);
            SchemaPlus rootSchema = calciteConnection.getRootSchema();
            if (rootSchema.getSubSchema(datasourceSchemaDTO.getSchemaAlias()) != null) {
                JdbcSchema jdbcSchema = rootSchema.getSubSchema(datasourceSchemaDTO.getSchemaAlias()).unwrap(JdbcSchema.class);
                BasicDataSource basicDataSource = (BasicDataSource) jdbcSchema.getDataSource();
                basicDataSource.close();
                removeSubSchema(rootSchema, datasourceSchemaDTO.getSchemaAlias());
            }
        } catch (Exception e) {
            CrestException.throwException(e.getMessage());
        }
        Provider.getLPorts().remove(datasource.getId());
        if (Provider.getSessions().get(datasource.getId()) != null) {
            Provider.getSessions().get(datasource.getId()).disconnect();
        }
        Provider.getSessions().remove(datasource.getId());
    }

    /**
     * 获取全局 Calcite 连接，首次调用时延迟创建并缓存
     */
    public Connection take() {
        Connection currentConnection = connection;
        if (currentConnection == null) {
            synchronized (this) {
                currentConnection = connection;
                if (currentConnection == null) {
                    currentConnection = getCalciteConnection();
                    connection = currentConnection;
                }
            }
        }
        return currentConnection;
    }

    /**
     * 从 Calcite root schema 中移除指定子 schema，用于刷新或删除数据源
     */
    private void removeSubSchema(SchemaPlus rootSchema, String schemaAlias) {
        CalciteSchema.from(rootSchema).removeSubSchema(schemaAlias);
    }

    /**
     * 读取可进入连接池的数据源，过滤目录、API、Excel 和状态异常的数据源
     */
    private CoreDatasource selectPoolDatasource(Long dsId) {
        CoreDatasource engine = engineManage.engineDatasource();
        if (engine != null && Objects.equals(engine.getId(), dsId)) {
            return engine;
        }
        CoreDatasource datasource = coreDatasourceMapper.selectById(dsId);
        if (datasource == null || Arrays.asList("folder", "API", "Excel", "ExcelRemote").contains(datasource.getType())
                || !Strings.CI.equals(datasource.getStatus(), "Success")) {
            return null;
        }
        return datasource;
    }

    /**
     * 确保指定数据源已经注册到 Calcite root schema，缺失时按当前数据库配置即时构建
     */
    private void ensureSchemaRegistered(CalciteConnection calciteConnection, Long dsId) {
        SchemaPlus rootSchema = calciteConnection.getRootSchema();
        String schemaAlias = String.format(SQLConstants.SCHEMA, dsId);
        if (rootSchema.getSubSchema(schemaAlias) != null) {
            return;
        }
        CoreDatasource datasource = selectPoolDatasource(dsId);
        if (datasource == null) {
            return;
        }
        DatasourceSchemaDTO datasourceSchemaDTO = new DatasourceSchemaDTO();
        BeanUtils.copyBean(datasourceSchemaDTO, datasource);
        datasourceSchemaDTO.setSchemaAlias(schemaAlias);
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDsList(Map.of(datasourceSchemaDTO.getId(), datasourceSchemaDTO));
        buildSchema(datasourceRequest, calciteConnection);
    }

    /**
     * 从指定数据源的 Calcite 子 schema 连接池中获取 JDBC 连接
     */
    private Connection getConnectionFromPool(Long dsId) {
        try {
            Connection connection = take();
            CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
            SchemaPlus rootSchema = calciteConnection.getRootSchema();
            if (rootSchema.getSubSchema(String.format(SQLConstants.SCHEMA, dsId)) == null) {
                ensureSchemaRegistered(calciteConnection, dsId);
            }
            if (rootSchema.getSubSchema(String.format(SQLConstants.SCHEMA, dsId)) == null) {
                CrestException.throwException(Translator.get("i18n_check_datasource_connection"));
            }
            JdbcSchema jdbcSchema = rootSchema.getSubSchema(String.format(SQLConstants.SCHEMA, dsId)).unwrap(JdbcSchema.class);
            BasicDataSource basicDataSource = (BasicDataSource) jdbcSchema.getDataSource();
            basicDataSource.setMaxWaitMillis(5 * 1000);
            return basicDataSource.getConnection();
        } catch (CrestException e) {
            throw e;
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
            CrestException.throwException(Translator.get("i18n_invalid_connection") + e.getMessage());
        }
        return null;
    }

    /**
     * 在引擎数据源上执行只读查询，返回字段和数据结果
     */
    @SuppressWarnings("java/sql-injection")
    public Map<String, Object> fetchResultField(EngineRequest engineRequest) throws Exception {
        int queryTimeout = engineQueryTimeout(engineRequest);
        DatasourceDTO datasource = new DatasourceDTO();
        BeanUtils.copyBean(datasource, engineRequest.getEngine());
        try (Connection connection = getConnectionFromPool(datasource.getId());
             PreparedStatement preparedStatement = connection.prepareStatement(SqlSecurityPolicy.validateReadQuery(engineRequest.getQuery()))) {
            preparedStatement.setQueryTimeout(queryTimeout);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("fields", fetchResultField(resultSet));
                map.put("data", dataResult(resultSet));
                return map;
            }
        }
    }

    /**
     * 在引擎数据源上执行单条非查询 SQL
     */
    @SuppressWarnings("java/sql-injection")
    public void exec(EngineRequest engineRequest) throws Exception {
        int queryTimeout = engineQueryTimeout(engineRequest);
        DatasourceDTO datasource = new DatasourceDTO();
        BeanUtils.copyBean(datasource, engineRequest.getEngine());
        try (Connection connection = getConnectionFromPool(datasource.getId());
             PreparedStatement preparedStatement = connection.prepareStatement(SqlSecurityPolicy.validateSingleStatement(engineRequest.getQuery()))) {
            preparedStatement.setQueryTimeout(queryTimeout);
            Boolean result = preparedStatement.execute();
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 在引擎数据源连接上执行事务回调，回调异常时回滚并恢复原自动提交状态
     */
    public void execWithEngineTransaction(EngineRequest engineRequest, EngineConnectionExecutor executor) throws Exception {
        int queryTimeout = engineQueryTimeout(engineRequest);
        DatasourceDTO datasource = new DatasourceDTO();
        BeanUtils.copyBean(datasource, engineRequest.getEngine());
        try (Connection connection = getConnectionFromPool(datasource.getId())) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                executor.execute(connection, queryTimeout);
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private int engineQueryTimeout(EngineRequest engineRequest) {
        if (engineRequest != null && engineRequest.getQueryTimeout() != null) {
            return Math.max(0, engineRequest.getQueryTimeout());
        }
        DatasourceConfiguration configuration = JsonUtil.parseObject(engineRequest.getEngine().getConfiguration(), DatasourceConfiguration.class);
        if (configuration == null) {
            return new DatasourceConfiguration().getQueryTimeout();
        }
        return configuration.getQueryTimeout();
    }

    private int datasourceQueryTimeout(DatasourceRequest datasourceRequest, DatasourceConfiguration configuration) {
        if (datasourceRequest != null && datasourceRequest.getQueryTimeout() != null) {
            return Math.max(0, datasourceRequest.getQueryTimeout());
        }
        return configuration == null ? new DatasourceConfiguration().getQueryTimeout() : configuration.getQueryTimeout();
    }

    /**
     * 引擎连接事务回调，调用方在回调内复用同一个连接和查询超时时间
     */
    @FunctionalInterface
    public interface EngineConnectionExecutor {
        void execute(Connection connection, int queryTimeout) throws Exception;
    }
}
