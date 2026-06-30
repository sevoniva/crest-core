package io.crest.extensions.datasource.vo;

import lombok.Data;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 描述通用数据源连接配置及 JDBC URL 解析能力
 */
@Data
@SuppressWarnings("deprecation")
public class Configuration {
    private String type;
    private String name;
    private String catalog;
    private String catalogDesc;
    private String extraParams;
    private String keywordPrefix = "";
    private String keywordSuffix = "";
    private String aliasPrefix = "";
    private String aliasSuffix = "";
    protected String jdbc;
    private String host;
    private String jdbcUrl;
    private String urlType;
    private Integer port;
    private String username;
    private String password;
    private String dataBase;
    private String schema;
    private String customDriver = "default";
    private String authMethod = "passwd";
    private String connectionType;
    private String charset;
    private String targetCharset;
    private String driver;
    private int initialPoolSize = 50;
    private int minPoolSize = 50;
    private int maxPoolSize = 100;
    private int queryTimeout = 30;
    private boolean useSSH = false;
    private String sshHost;
    private Integer sshPort;
    private Integer lPort;
    private String sshUserName;
    private String sshType = "password";
    private String sshPassword;
    private String sshKey;
    private String sshKeyPassword;
    private String url;


    /**
     * 获取实际连接主机，启用 SSH 时使用本地隧道地址
     */
    public String getLHost(){
        if(useSSH){
            return "127.0.0.1";
        }else {
            return this.host;
        }
    }

    /**
     * 获取实际连接端口，启用 SSH 时使用本地转发端口
     */
    public Integer getLPort(){
        if(useSSH && lPort != null){
            return lPort;
        }else {
            return this.port;
        }
    }

    protected static final Pattern HOST_PORT_PATTERN = Pattern.compile("//([^:/]+)(?::(\\d+))?");
    private static final Pattern DB_NAME_PATTERN = Pattern.compile("//[^/]+/([^?]+)");
    private Map<String, String> parameters = new HashMap<>();
    /**
     * 从 JDBC URL 中解析主机和端口
     */
    protected void parseHostAndPort(String jdbcUrl) {
        Matcher matcher = HOST_PORT_PATTERN.matcher(jdbcUrl);
        if (matcher.find()) {
            setHost(matcher.group(1));
            if (matcher.group(2) != null) {
                setPort(Integer.parseInt(matcher.group(2)));
            }
        }
    }

    /**
     * 解析 JDBC URL 查询参数到参数映射
     */
    protected void parseParameters(String jdbcUrl) {
        int paramStart = jdbcUrl.indexOf('?');
        if (paramStart > 0) {
            String paramString = jdbcUrl.substring(paramStart + 1);
            int start = 0;
            while (start <= paramString.length()) {
                int end = paramString.indexOf('&', start);
                if (end < 0) {
                    end = paramString.length();
                }
                int equals = paramString.indexOf('=', start);
                if (equals >= start && equals < end && equals > start) {
                    parameters.put(paramString.substring(start, equals), paramString.substring(equals + 1, end));
                }
                if (end == paramString.length()) {
                    break;
                }
                start = end + 1;
            }
        }
    }

    /**
     * 将常见连接参数转换为配置字段
     */
    protected void convertParameters(){
        if (ObjectUtils.isEmpty(parameters)) {
            return;
        }
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (Strings.CI.equals(key, "user")) {
                setUsername(value);
            }
            if (Strings.CI.equals(key, "password")) {
                setPassword(value);
            }
        }
    }

    /**
     * 从 JDBC URL 中解析数据库名称
     */
    protected void convertDatabase(String jdbcUrl) {
        Matcher matcher = databasePattern().matcher(jdbcUrl);
        if (matcher.find()) {
            setDataBase(matcher.group(1));
        }
    }

    /**
     * 返回用于解析数据库名称的正则表达式
     */
    protected Pattern databasePattern() {
        return DB_NAME_PATTERN;
    }

    /**
     * 当使用 JDBC URL 模式时解析并回填连接配置
     */
    public void convertJdbcUrl() {
        if (StringUtils.isNotBlank(urlType) && Strings.CI.equalsAny(this.urlType, "jdbcUrl")) {
            parseHostAndPort(jdbcUrl);
            parseParameters(jdbcUrl);
            convertParameters();
            convertDatabase(jdbcUrl);
        }
    }

}
