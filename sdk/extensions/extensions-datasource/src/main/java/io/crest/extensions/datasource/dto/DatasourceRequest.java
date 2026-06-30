package io.crest.extensions.datasource.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Data
@SuppressWarnings("deprecation")
/**
 * 数据源查询请求，封装 SQL、分页、预览和数据源上下文
 */
public class DatasourceRequest implements Serializable {
    /**
     * 识别前置公共表达式的正则模板
     */
    private final String REG_WITH_SQL_FRAGMENT = "((?i)WITH[\\s\\S]+(?i)AS?\\s*\\([\\s\\S]+\\))\\s*(?i)SELECT";
    /**
     * 原始查询语句
     */
    protected String query;
    /**
     * 查询表名
     */
    protected String table;
    /**
     * 当前查询使用的数据源配置
     */
    protected DatasourceDTO datasource;
    /**
     * 数据源版本号
     */
    private Integer dsVersion;
    /**
     * 每页数据量
     */
    private Integer pageSize;
    /**
     * 当前页码
     */
    private Integer page;
    /**
     * 实际返回数据量
     */
    private Integer realSize;
    /**
     * 数据库抓取批量大小
     */
    private Integer fetchSize = 10000;
    /**
     * 单次查询超时时间，单位秒；为空时使用数据源配置
     */
    private Integer queryTimeout;
    /**
     * 是否启用分页查询
     */
    private boolean pageable = false;
    /**
     * 是否为数据预览请求
     */
    private boolean previewData = false;
    /**
     * 是否需要查询总页数
     */
    private boolean totalPageFlag;
    /**
     * 多数据源上下文映射
     */
    private Map<Long, DatasourceSchemaDTO> dsList;
    /**
     * 表字段及筛选值列表
     */
    private List<TableFieldWithValue> tableFieldWithValues;
    /**
     * 数据源访问令牌
     */
    private  String token;
    /**
     * 是否为跨源查询
     */
    private Boolean isCross;
    /**
     * 是否只读访问
     */
    private Boolean readOnly;

    /**
     * 创建空的数据源查询请求
     */
    public DatasourceRequest() {
    }

    /**
     * 获取处理前置公共表达式后的查询语句
     */
    public String getQuery() {
        return this.rebuildSqlWithFragment(this.query);
    }

    /**
     * 设置原始查询语句
     */
    public void setQuery(String query) {
        this.query = query;
    }

    /**
     * 将嵌入在查询前部之后的公共表达式移动到语句开头
     */
    private String rebuildSqlWithFragment(String sql) {
        String lowerSql = sql.toLowerCase(Locale.ROOT);
        if (!lowerSql.startsWith("with")) {
            int withIndex = lowerSql.indexOf("with");
            int selectIndex = withIndex < 0 ? -1 : lowerSql.indexOf("select", withIndex + 4);
            if (withIndex >= 0 && selectIndex > withIndex) {
                String withFragment = sql.substring(withIndex, selectIndex);
                String withSelectFragment = sql.substring(withIndex, selectIndex + 6);
                sql = sql.replace(withSelectFragment, "SELECT");
                sql = (withFragment + " " + sql).replace("  ", " ");
            }
        }

        return sql;
    }

    /**
     * 获取前置公共表达式识别正则
     */
    public String getREG_WITH_SQL_FRAGMENT() {
        this.getClass();
        return "((?i)WITH[\\s\\S]+(?i)AS?\\s*\\([\\s\\S]+\\))\\s*(?i)SELECT";
    }
}
