package io.crest.datasource.request;

import io.crest.datasource.dao.auto.entity.CoreEngine;
import lombok.Data;

import java.util.Locale;

@Data
@SuppressWarnings("deprecation")
/**
 * 引擎查询请求对象，承载 SQL、分页和预览控制参数
 */
public class EngineRequest {
    /**
     * WITH 片段识别正则
     */
    private final String REG_WITH_SQL_FRAGMENT = "((?i)WITH[\\s\\S]+(?i)AS?\\s*\\([\\s\\S]+\\))\\s*(?i)SELECT";
    /**
     * 原始查询 SQL
     */
    protected String query;
    /**
     * 查询表名
     */
    protected String table;
    /**
     * 查询使用的引擎配置
     */
    protected CoreEngine engine;
    /**
     * 分页大小
     */
    private Integer pageSize;
    /**
     * 当前页码
     */
    private Integer page;
    /**
     * 实际读取条数
     */
    private Integer realSize;
    /**
     * JDBC 抓取大小
     */
    private Integer fetchSize = 10000;
    /**
     * 单次查询超时时间，单位秒；为空时使用引擎配置
     */
    private Integer queryTimeout;
    /**
     * 是否启用分页
     */
    private boolean pageable = false;
    /**
     * 是否为数据预览请求
     */
    private boolean previewData = false;
    /**
     * 是否为总页数查询
     */
    private boolean totalPageFlag;

    /**
     * 创建空引擎请求
     */
    public EngineRequest() {
    }

    /**
     * 返回经过 WITH 片段重排后的查询 SQL
     */
    public String getQuery() {
        return this.rebuildSqlWithFragment(this.query);
    }

    /**
     * 设置原始查询 SQL
     */
    public void setQuery(String query) {
        this.query = query;
    }

    /**
     * 将非开头 WITH 片段移动到查询开头
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
     * 返回 WITH 片段识别正则
     */
    public String getREG_WITH_SQL_FRAGMENT() {
        this.getClass();
        return "((?i)WITH[\\s\\S]+(?i)AS?\\s*\\([\\s\\S]+\\))\\s*(?i)SELECT";
    }
}
