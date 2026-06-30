package io.crest.engine.sql;

import io.crest.constant.SQLConstants;
import io.crest.extensions.datasource.model.SQLMeta;
import io.crest.extensions.datasource.model.SQLObj;
import io.crest.extensions.view.dto.ChartViewDTO;
import io.crest.extensions.view.dto.SortAxis;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupString;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 将 SQLMeta 的各部分转换结果拼接为最终查询语句。
 */
public class SQLProvider {
    /**
     * StringTemplate 中预览 SQL 模板名称
     */
    private static final String PREVIEW_SQL_TEMPLATE = "previewSql";

    /**
     * @param sqlMeta sql作为table，首尾用'(',')'
     * @param isGroup 是否聚合
     * @return
     */
    public static String createQuerySQLAsTmp(SQLMeta sqlMeta, boolean isGroup, boolean needOrder, boolean distinct) {
        return createQuerySQL(sqlMeta, isGroup, needOrder, distinct);
    }

    /**
     * 创建带分页限制的查询 SQL
     */
    public static String createQuerySQLWithLimit(SQLMeta sqlMeta, boolean isGroup, boolean needOrder, boolean distinct, int start, int count) {
        return createQuerySQL(sqlMeta, isGroup, needOrder, distinct) + " LIMIT " + count + " OFFSET " + start;
    }

    /**
     * 根据 SQLMeta 创建基础查询 SQL
     */
    public static String createQuerySQL(SQLMeta sqlMeta, boolean isGroup, boolean needOrder, boolean distinct) {
        List<SQLObj> xFields = sqlMeta.getXFields();
        SQLObj tableObj = sqlMeta.getTable();
        List<SQLObj> xOrders = sqlMeta.getXOrders();

        STGroup stg = new STGroupString(SqlTemplate.PREVIEW_SQL);
        ST st_sql = stg.getInstanceOf(PREVIEW_SQL_TEMPLATE);
        st_sql.add("isGroup", isGroup);
        st_sql.add("distinct", distinct);
        if (ObjectUtils.isNotEmpty(xFields)) st_sql.add("groups", xFields);
        if (ObjectUtils.isNotEmpty(tableObj)) st_sql.add("table", tableObj);
        String customWheres = sqlMeta.getCustomWheres();
        String extWheres = sqlMeta.getExtWheres();
        String whereTrees = sqlMeta.getWhereTrees();
        List<String> wheres = new ArrayList<>();
        if (customWheres != null) wheres.add(customWheres);
        if (extWheres != null) wheres.add(extWheres);
        if (whereTrees != null) wheres.add(whereTrees);
        if (ObjectUtils.isNotEmpty(wheres)) st_sql.add("filters", wheres);

        // 判断数据源是否需要默认排序
        if (needOrder && ObjectUtils.isEmpty(xOrders)) {
            if (ObjectUtils.isNotEmpty(xFields)) {
                xOrders = new ArrayList<>();
                SQLObj sqlObj = xFields.get(0);
                SQLObj result = SQLObj.builder()
                        .orderField(String.format(SQLConstants.FIELD_DOT, sqlObj.getFieldAlias()))
                        .orderAlias(String.format(SQLConstants.FIELD_DOT, sqlObj.getFieldAlias()))
                        .orderDirection("ASC").build();
                xOrders.add(result);
            }
        }
        if (ObjectUtils.isNotEmpty(xOrders)) {
            st_sql.add("orders", xOrders);
        }

        return st_sql.render();
    }

    /**
     * 按图表配置创建带聚合过滤和排序的查询 SQL
     */
    public static String createQuerySQL(SQLMeta sqlMeta, boolean isGroup, boolean needOrder, ChartViewDTO view) {
        STGroup stg = new STGroupString(SqlTemplate.PREVIEW_SQL);
        ST st_sql = stg.getInstanceOf(PREVIEW_SQL_TEMPLATE);

        st_sql.add("isGroup", isGroup);

        SQLObj tableObj = sqlMeta.getTable();
        if (ObjectUtils.isNotEmpty(tableObj)) st_sql.add("table", tableObj);

        List<SQLObj> xFields = sqlMeta.getXFields();
        List<SQLObj> xOrders = sqlMeta.getXOrders();
        if (ObjectUtils.isNotEmpty(xFields)) st_sql.add("groups", xFields);

        List<SQLObj> yFields = sqlMeta.getYFields();
        List<String> yWheres = sqlMeta.getYWheres();
        List<SQLObj> yOrders = sqlMeta.getYOrders();
        if (ObjectUtils.isNotEmpty(yFields)) st_sql.add("aggregators", yFields);

        String customWheres = sqlMeta.getCustomWheres();
        String extWheres = sqlMeta.getExtWheres();
        String whereTrees = sqlMeta.getWhereTrees();
        List<String> wheres = new ArrayList<>();
        if (customWheres != null) wheres.add(customWheres);
        if (extWheres != null) wheres.add(extWheres);
        if (whereTrees != null) wheres.add(whereTrees);
        if (ObjectUtils.isNotEmpty(wheres)) st_sql.add("filters", wheres);
        String sql = st_sql.render();

        ST st = stg.getInstanceOf(PREVIEW_SQL_TEMPLATE);
        st.add("isGroup", isGroup);

        SQLObj tableSQL = SQLObj.builder()
                .tableName(String.format(SQLConstants.BRACKETS, sql))
                .tableAlias(String.format(SQLConstants.TABLE_ALIAS_PREFIX, 1))
                .build();
        if (ObjectUtils.isNotEmpty(tableSQL)) st.add("table", tableSQL);

        List<String> aggWheres = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(yWheres)) aggWheres.addAll(yWheres);
        if (ObjectUtils.isNotEmpty(aggWheres)) st.add("filters", aggWheres);

        List<SQLObj> orders = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(xOrders)) orders.addAll(xOrders);
        if (ObjectUtils.isNotEmpty(yOrders)) orders.addAll(yOrders);
        if (!orders.isEmpty() && CollectionUtils.isNotEmpty(view.getSortPriority())) {
            var sortPriority = view.getSortPriority();
            var tmp = new ArrayList<SQLObj>();
            var ids = new HashSet<Long>();
            for (SortAxis sortAxis : sortPriority) {
                for (SQLObj order : orders) {
                    if (sortAxis.getId().equals(order.getId())){
                        tmp.add(order);
                        ids.add(order.getId());
                    }
                }
            }
            for (SQLObj order : orders) {
                if (!ids.contains(order.getId())) {
                    tmp.add(order);
                }
            }
            orders = tmp;
        }
        // 判断数据源是否需要默认排序
        if (needOrder && ObjectUtils.isEmpty(orders)) {
            if (ObjectUtils.isNotEmpty(xFields) || ObjectUtils.isNotEmpty(yFields)) {
                SQLObj sqlObj = ObjectUtils.isNotEmpty(xFields) ? xFields.get(0) : yFields.get(0);
                SQLObj result = SQLObj.builder()
                        .orderField(String.format(SQLConstants.FIELD_DOT, sqlObj.getFieldAlias()))
                        .orderAlias(String.format(SQLConstants.FIELD_DOT, sqlObj.getFieldAlias()))
                        .orderDirection("ASC").build();
                orders.add(result);
            }
        }
        if (ObjectUtils.isNotEmpty(orders)) st.add("orders", orders);

        return sqlLimit(st.render(), view);
    }

    /**
     * 按图表配置创建不包含排序的查询 SQL
     */
    public static String createQuerySQLNoSort(SQLMeta sqlMeta, boolean isGroup, ChartViewDTO view) {
        STGroup stg = new STGroupString(SqlTemplate.PREVIEW_SQL);
        ST st_sql = stg.getInstanceOf(PREVIEW_SQL_TEMPLATE);

        st_sql.add("isGroup", isGroup);

        SQLObj tableObj = sqlMeta.getTable();
        if (ObjectUtils.isNotEmpty(tableObj)) st_sql.add("table", tableObj);

        List<SQLObj> xFields = sqlMeta.getXFields();
        if (ObjectUtils.isNotEmpty(xFields)) st_sql.add("groups", xFields);

        List<SQLObj> yFields = sqlMeta.getYFields();
        List<String> yWheres = sqlMeta.getYWheres();
        if (ObjectUtils.isNotEmpty(yFields)) st_sql.add("aggregators", yFields);

        String customWheres = sqlMeta.getCustomWheres();
        String extWheres = sqlMeta.getExtWheres();
        String whereTrees = sqlMeta.getWhereTrees();
        List<String> wheres = new ArrayList<>();
        if (customWheres != null) wheres.add(customWheres);
        if (extWheres != null) wheres.add(extWheres);
        if (whereTrees != null) wheres.add(whereTrees);
        if (ObjectUtils.isNotEmpty(wheres)) st_sql.add("filters", wheres);
        String sql = st_sql.render();

        ST st = stg.getInstanceOf(PREVIEW_SQL_TEMPLATE);
        st.add("isGroup", isGroup);

        SQLObj tableSQL = SQLObj.builder()
                .tableName(String.format(SQLConstants.BRACKETS, sql))
                .tableAlias(String.format(SQLConstants.TABLE_ALIAS_PREFIX, 1))
                .build();
        if (ObjectUtils.isNotEmpty(tableSQL)) st.add("table", tableSQL);

        List<String> aggWheres = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(yWheres)) aggWheres.addAll(yWheres);
        if (ObjectUtils.isNotEmpty(aggWheres)) st.add("filters", aggWheres);

        return sqlLimit(st.render(), view);
    }

    /**
     * 按图表结果模式追加 SQL 限制条件
     */
    public static String sqlLimit(String sql, ChartViewDTO view) {
        if (Strings.CI.equalsAny(view.getType(), "table-info", "table-normal")) {
            return sql;
        }
        if (Strings.CI.equals(view.getResultMode(), "custom")) {
            return sql + " LIMIT " + view.getResultCount() + " OFFSET 0";
        } else {
            return sql;
        }
    }
}
