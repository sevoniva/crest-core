package io.crest.extensions.datasource.provider;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.constant.SqlPlaceholderConstants;
import io.crest.extensions.datasource.dto.*;
import io.crest.extensions.datasource.model.SQLMeta;
import io.crest.extensions.datasource.utils.SqlPlaceholderUtils;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import lombok.Getter;
import org.apache.calcite.config.Lex;
import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.dialect.*;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据源 Provider 抽象基类，封装连接、SQL 方言转换和 SSH 隧道等通用能力
 */
@SuppressWarnings("deprecation")
public abstract class Provider {

    /**
     * Provider 通用日志对象
     */
    public static Logger logger = LoggerFactory.getLogger(Provider.class);

    /**
     * 获取schema接口
     *
     * @param datasourceRequest
     * @return
     */
    public abstract List<String> getSchema(DatasourceRequest datasourceRequest);

    /**
     * 获取表接口
     *
     * @param datasourceRequest
     * @return
     */
    public abstract List<DatasetTableDTO> tables(DatasourceRequest datasourceRequest);

    /**
     * 创建数据库连接
     *
     * @param coreDatasource
     * @return
     * @throws Exception
     */
    public abstract ConnectionObj getConnection(DatasourceDTO coreDatasource) throws Exception;

    /**
     * 检测数据源状态是否有效
     *
     * @param datasourceRequest
     * @return
     * @throws Exception
     */
    public abstract String checkStatus(DatasourceRequest datasourceRequest) throws Exception;

    /**
     * 获取数据
     *
     * @param datasourceRequest
     * @return
     * @throws CrestException
     */
    public abstract Map<String, Object> fetchResultField(DatasourceRequest datasourceRequest) throws CrestException;

    /**
     * 获取表字段
     *
     * @param datasourceRequest
     * @return
     * @throws CrestException
     */
    public abstract List<TableField> fetchTableField(DatasourceRequest datasourceRequest) throws CrestException;

    /**
     * 隐藏密码
     *
     * @param datasourceDTO
     */
    public abstract void hidePW(DatasourceDTO datasourceDTO);

    /**
     * 执行无返回数据的自定义数据源操作
     */
    public void exec(DatasourceRequest datasourceRequest) {

    }

    /**
     * 执行更新语句并返回执行结果
     */
    public ExecuteResult executeUpdate(DatasourceRequest datasourceRequest, String autoIncrementPkName) {
        return new ExecuteResult();
    }


    /**
     * 数据源 SSH 本地端口缓存
     */
    @Getter
    private static final Map<Long, Integer> lPorts = new HashMap<>();
    /**
     * 数据源 SSH 会话缓存
     */
    @Getter
    private static final Map<Long, Session> sessions = new HashMap<>();

    /**
     * 创建带查询超时时间的 Statement
     */
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
     * 重建适配目标数据源的 SQL
     */
    public String rebuildSQL(String sql, SQLMeta sqlMeta, boolean crossDs, Map<Long, DatasourceSchemaDTO> dsMap) {
        return rebuildSQL(sql, sqlMeta, crossDs, dsMap, false);
    }
    /**
     * 重建适配目标数据源的 SQL，并支持 SQLBot 场景跳过真实连接
     */
    public String rebuildSQL(String sql, SQLMeta sqlMeta, boolean crossDs, Map<Long, DatasourceSchemaDTO> dsMap, boolean forSqlbot) {
        logger.debug("calcite sql: " + sql);
        if (crossDs) {
            return sql;
        }

        String s = transSqlDialect(sql, dsMap, forSqlbot);
        String tableDialect = sqlMeta.getTableDialect();
        s = replaceTablePlaceHolder(s, tableDialect);
        s = replaceCalcFieldPlaceHolder(s, sqlMeta);
        s = translateCrestSqlFunctions(s, dsMap);
        return replaceMssqlN(s);
    }

    /**
     * 将内置 SQL 函数翻译为目标数据源函数
     */
    private String translateCrestSqlFunctions(String sql, Map<Long, DatasourceSchemaDTO> dsMap) {
        if (dsMap == null || dsMap.isEmpty()) {
            return sql;
        }
        DatasourceSchemaDTO value = dsMap.entrySet().iterator().next().getValue();
        return CrestSqlFunctionTranslator.translate(sql, value);
    }

    /**
     * 将 SQL 转换为目标数据源方言
     */
    public String transSqlDialect(String sql, Map<Long, DatasourceSchemaDTO> dsMap) throws CrestException {
        return transSqlDialect(sql, dsMap, false);
    }
    /**
     * 将 SQL 转换为目标数据源方言，并可选择 SQLBot 轻量转换模式
     */
    public String transSqlDialect(String sql, Map<Long, DatasourceSchemaDTO> dsMap, boolean forSqlbot) throws CrestException {
        DatasourceSchemaDTO value = dsMap.entrySet().iterator().next().getValue();
        ConnectionObj connection = null;
        try {
            if (!forSqlbot) {
                connection = getConnection(value);
                // 获取数据库version
                if (connection != null) {
                    value.setDsVersion(connection.getConnection().getMetaData().getDatabaseMajorVersion());
                }
            }
            SqlParser parser = SqlParser.create(sql, SqlParser.Config.DEFAULT.withLex(Lex.JAVA));
            SqlNode sqlNode = parser.parseStmt();
            String dialect = restoreUnicodeStringLiterals(sqlNode.toSqlString(getDialect(value)).toString());
            dialect = CrestSqlFunctionTranslator.translate(dialect, value);
            if (Strings.CI.equals(value.getType(), "sqlServer")) {
                dialect = dialect.replaceAll("\\[CONCAT]", "CONCAT");
            }
            return dialect;
        } catch (Exception e) {
            CrestException.throwException(e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return null;
    }

    /**
     * 替换 SQL 中的数据表占位符
     */
    public String replaceTablePlaceHolder(String s, String placeholder) {
        s = s.replaceAll("\r\n", " ")
                .replaceAll("\n", " ")
                .replaceAll(SqlPlaceholderConstants.TABLE_PLACEHOLDER_REGEX, Matcher.quoteReplacement(placeholder))
                .replaceAll("ASYMMETRIC", "")
                .replaceAll("SYMMETRIC", "");
        return s;
    }

    /**
     * 还原 Calcite 转换后的 Unicode 字符串字面量
     */
    protected static String restoreUnicodeStringLiterals(String sql) {
        if (StringUtils.isEmpty(sql)) {
            return sql;
        }
        StringBuilder result = new StringBuilder(sql.length());
        int index = 0;
        while (index < sql.length()) {
            if (isUnicodeStringLiteralStart(sql, index)) {
                int nextIndex = appendUnicodeStringLiteral(sql, index, result);
                if (nextIndex > index) {
                    index = nextIndex;
                    continue;
                }
            }
            if (sql.charAt(index) == '\'') {
                index = appendQuotedString(sql, index, result);
                continue;
            }
            result.append(sql.charAt(index));
            index++;
        }
        return result.toString();
    }

    /**
     * 判断当前位置是否为 Unicode 字符串字面量起点
     */
    private static boolean isUnicodeStringLiteralStart(String sql, int index) {
        if (index + 2 >= sql.length()) {
            return false;
        }
        char first = sql.charAt(index);
        return (first == 'u' || first == 'U')
                && sql.charAt(index + 1) == '&'
                && sql.charAt(index + 2) == '\''
                && (index == 0 || !isIdentifierCharacter(sql.charAt(index - 1)));
    }

    /**
     * 解析并追加 Unicode 字符串字面量
     */
    private static int appendUnicodeStringLiteral(String sql, int start, StringBuilder result) {
        StringBuilder literal = new StringBuilder();
        literal.append('\'');
        int index = start + 3;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            if (current == '\'') {
                literal.append(current);
                if (index + 1 < sql.length() && sql.charAt(index + 1) == '\'') {
                    literal.append('\'');
                    index += 2;
                    continue;
                }
                result.append(literal);
                return index + 1;
            }
            if (current == '\\' && index + 1 < sql.length()) {
                if (sql.charAt(index + 1) == '\\') {
                    literal.append("\\\\");
                    index += 2;
                    continue;
                }
                if (index + 4 < sql.length() && isHexSequence(sql, index + 1)) {
                    try {
                        literal.append((char) Integer.parseInt(sql.substring(index + 1, index + 5), 16));
                        index += 5;
                        continue;
                    } catch (NumberFormatException ignored) {
                        // 转义序列异常时保留原始文本继续处理
                    }
                }
            }
            literal.append(current);
            index++;
        }
        return start;
    }

    /**
     * 解析并追加普通单引号字符串
     */
    private static int appendQuotedString(String sql, int start, StringBuilder result) {
        result.append('\'');
        int index = start + 1;
        while (index < sql.length()) {
            char current = sql.charAt(index);
            result.append(current);
            index++;
            if (current == '\'') {
                if (index < sql.length() && sql.charAt(index) == '\'') {
                    result.append('\'');
                    index++;
                    continue;
                }
                return index;
            }
        }
        return index;
    }

    /**
     * 判断指定位置后四位是否为十六进制序列
     */
    private static boolean isHexSequence(String value, int start) {
        for (int index = start; index < start + 4; index++) {
            if (Character.digit(value.charAt(index), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断字符是否可作为 SQL 标识符的一部分
     */
    private static boolean isIdentifierCharacter(char value) {
        return Character.isLetterOrDigit(value) || value == '_' || value == '$';
    }

    /**
     * 为 SQL Server 中文字符串补充 N 前缀
     */
    public String replaceMssqlN(String s) {
        Pattern compile = Pattern.compile("'-DENS-.*?'");
        Matcher matcher = compile.matcher(s);
        while (matcher.find()) {
            String v = matcher.group();
            s = s.replaceAll(Pattern.quote(v), "N" + v.replace("-DENS-", ""));
        }
        return s;
    }

    /**
     * 替换 SQL 中计算字段和过滤条件占位符
     */
    public String replaceCalcFieldPlaceHolder(String s, SQLMeta sqlMeta) {
        Map<String, String> fieldsDialect = new HashMap<>();
        if (sqlMeta.getXFieldsDialect() != null && !sqlMeta.getXFieldsDialect().isEmpty()) {
            fieldsDialect.putAll(sqlMeta.getXFieldsDialect());
        }
        if (sqlMeta.getYFieldsDialect() != null && !sqlMeta.getYFieldsDialect().isEmpty()) {
            fieldsDialect.putAll(sqlMeta.getYFieldsDialect());
        }
        if (sqlMeta.getCustomWheresDialect() != null && !sqlMeta.getCustomWheresDialect().isEmpty()) {
            fieldsDialect.putAll(sqlMeta.getCustomWheresDialect());
        }
        if (sqlMeta.getExtWheresDialect() != null && !sqlMeta.getExtWheresDialect().isEmpty()) {
            fieldsDialect.putAll(sqlMeta.getExtWheresDialect());
        }
        if (sqlMeta.getWhereTreesDialect() != null && !sqlMeta.getWhereTreesDialect().isEmpty()) {
            fieldsDialect.putAll(sqlMeta.getWhereTreesDialect());
        }

        if (!fieldsDialect.isEmpty()) {
            for (Map.Entry<String, String> ele : fieldsDialect.entrySet()) {
                s = SqlPlaceholderUtils.replaceIdentifier(s, ele.getKey(), ele.getValue());
            }
        }
        return s;
    }

    /**
     * 移除 SQL 中的块注释和行注释
     */
    public String replaceComment(String s) {
        StringBuilder result = new StringBuilder(s.length());
        boolean blockComment = false;
        boolean lineComment = false;
        for (int i = 0; i < s.length(); i++) {
            char current = s.charAt(i);
            char next = i + 1 < s.length() ? s.charAt(i + 1) : '\0';
            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                    result.append(' ');
                }
                continue;
            }
            if (current == '/' && next == '*') {
                blockComment = true;
                result.append(' ');
                i++;
                continue;
            }
            if (current == '-' && next == '-') {
                lineComment = true;
                result.append(' ');
                i++;
                continue;
            }
            result.append(current);
        }
        return result.toString();
    }

    /**
     * 根据数据源类型获取 Calcite SQL 方言
     */
    public SqlDialect getDialect(DatasourceSchemaDTO coreDatasource) {
        SqlDialect sqlDialect = null;
        DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(coreDatasource.getType());
        switch (datasourceType) {
            case mysql:
            case obMysql:
            case mongo:
            case StarRocks:
            case TiDB:
            case mariadb:
                sqlDialect = MysqlSqlDialect.DEFAULT;
                break;
            case doris:
                sqlDialect = MysqlSqlDialect.DEFAULT;
                break;
            case impala:
                sqlDialect = HiveSqlDialect.DEFAULT;
                break;
            case sqlServer:
                sqlDialect = MssqlSqlDialect.DEFAULT;
                break;
            case oracle:
            case obOracle:
                sqlDialect = OracleSqlDialect.DEFAULT;
                break;
            case db2:
                sqlDialect = Db2SqlDialect.DEFAULT;
                break;
            case pg:
                sqlDialect = PostgresqlSqlDialect.DEFAULT;
                break;
            case redshift:
                sqlDialect = RedshiftSqlDialect.DEFAULT;
                break;
            case ck:
                sqlDialect = ClickHouseSqlDialect.DEFAULT;
                break;
            case h2:
                sqlDialect = H2SqlDialect.DEFAULT;
                break;
            case es:
                sqlDialect = AnsiSqlDialect.DEFAULT;
                break;
            default:
                sqlDialect = MysqlSqlDialect.DEFAULT;
        }
        return sqlDialect;
    }

    /**
     * 分配可用的本地 SSH 转发端口
     */
    synchronized public Integer getLport(Long datasourceId) throws Exception {
        for (int i = 10000; i < 20000; i++) {
            if (isPortAvailable(i) && !lPorts.values().contains(i)) {
                if (datasourceId == null) {
                    lPorts.put((long) i, i);
                } else {
                    lPorts.put(datasourceId, i);
                }
                return i;
            }
        }
        throw new Exception("localhost无可用端口！");
    }

    /**
     * 检查本机端口是否可用
     */
    public boolean isPortAvailable(int port) {
        // 仅探测本机端口，不发送应用数据或凭据
        try (Socket socket = new Socket("127.0.0.1", port)) { // nosemgrep: java.lang.security.audit.crypto.unencrypted-socket.unencrypted-socket
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * 按数据源配置初始化或复用 SSH 隧道会话
     */
    public void startSshSession(DatasourceConfiguration configuration, ConnectionObj connectionObj, Long datasourceId) throws Exception {
        if (configuration.isUseSSH()) {
            if (datasourceId == null) {
                configuration.setLPort(getLport(null));
                connectionObj.setLPort(configuration.getLPort());
                connectionObj.setConfiguration(configuration);
                Session session = initSession(configuration);
                connectionObj.setSession(session);
            } else {
                Integer lport = Provider.getLPorts().get(datasourceId);
                if (lport != null) {
                    configuration.setLPort(lport);
                    if (Provider.getSessions().get(datasourceId) == null || !Provider.getSessions().get(datasourceId).isConnected()) {
                        Session session = initSession(configuration);
                        Provider.getSessions().put(datasourceId, session);
                    }
                } else {
                    lport = getLport(datasourceId);
                    configuration.setLPort(lport);
                    Session session = initSession(configuration);
                    Provider.getSessions().put(datasourceId, session);
                }
            }
        }
    }

    /**
     * 创建并连接 JSch 会话，同时建立本地端口转发
     */
    public Session initSession(DatasourceConfiguration configuration) throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(configuration.getSshUserName(), configuration.getSshHost(), configuration.getSshPort());
        if (!configuration.getSshType().equalsIgnoreCase("password")) {
            session.setConfig("PreferredAuthentications", "publickey");
            jsch.addIdentity("sshkey", configuration.getSshKey().getBytes(StandardCharsets.UTF_8), null, configuration.getSshKeyPassword() == null ? null : configuration.getSshKeyPassword().getBytes(StandardCharsets.UTF_8));
        }
        if (configuration.getSshType().equalsIgnoreCase("password")) {
            session.setPassword(configuration.getSshPassword());
        }
        session.setConfig("StrictHostKeyChecking", "no");
        try {
            session.connect(1000 * 5);
        } catch (Exception e) {
            CrestException.throwException("SSH 连接失败：" + e.getMessage());
        }
        session.setPortForwardingL(configuration.getLPort(), configuration.getHost(), configuration.getPort());

        return session;
    }
}
