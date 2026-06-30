package io.crest.dataset.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 数据集 SQL 日志实体。
 */
@TableName("core_dataset_sql_log")
public class CoreDatasetTableSqlLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * SQL 日志主键
     */
    private String id;

    /**
     * 数据集 SQL 节点 ID
     */
    private String tableId;

    /**
     * SQL 执行开始时间
     */
    private Long startTime;

    /**
     * SQL 执行结束时间
     */
    private Long endTime;

    /**
     * SQL 执行耗时，单位为毫秒
     */
    private Long spend;

    /**
     * SQL 语句或执行详情
     */
    private String sql;

    /**
     * SQL 执行状态
     */
    private String status;

    /**
     * 获取 SQL 日志主键
     *
     * @return SQL 日志主键
     */
    public String getId() {
        return id;
    }

    /**
     * 设置 SQL 日志主键
     *
     * @param id SQL 日志主键
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取数据集 SQL 节点 ID
     *
     * @return 数据集 SQL 节点 ID
     */
    public String getTableId() {
        return tableId;
    }

    /**
     * 设置数据集 SQL 节点 ID
     *
     * @param tableId 数据集 SQL 节点 ID
     */
    public void setTableId(String tableId) {
        this.tableId = tableId;
    }

    /**
     * 获取 SQL 执行开始时间
     *
     * @return SQL 执行开始时间
     */
    public Long getStartTime() {
        return startTime;
    }

    /**
     * 设置 SQL 执行开始时间
     *
     * @param startTime SQL 执行开始时间
     */
    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    /**
     * 获取 SQL 执行结束时间
     *
     * @return SQL 执行结束时间
     */
    public Long getEndTime() {
        return endTime;
    }

    /**
     * 设置 SQL 执行结束时间
     *
     * @param endTime SQL 执行结束时间
     */
    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    /**
     * 获取 SQL 执行耗时
     *
     * @return SQL 执行耗时，单位为毫秒
     */
    public Long getSpend() {
        return spend;
    }

    /**
     * 设置 SQL 执行耗时
     *
     * @param spend SQL 执行耗时，单位为毫秒
     */
    public void setSpend(Long spend) {
        this.spend = spend;
    }

    /**
     * 获取 SQL 语句或执行详情
     *
     * @return SQL 语句或执行详情
     */
    public String getSql() {
        return sql;
    }

    /**
     * 设置 SQL 语句或执行详情
     *
     * @param sql SQL 语句或执行详情
     */
    public void setSql(String sql) {
        this.sql = sql;
    }

    /**
     * 获取 SQL 执行状态
     *
     * @return SQL 执行状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置 SQL 执行状态
     *
     * @param status SQL 执行状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 返回 SQL 日志的调试字符串
     *
     * @return SQL 日志的字符串表示
     */
    @Override
    public String toString() {
        return "CoreDatasetTableSqlLog{" +
        "id = " + id +
        ", tableId = " + tableId +
        ", startTime = " + startTime +
        ", endTime = " + endTime +
        ", spend = " + spend +
        ", sql = " + sql +
        ", status = " + status +
        "}";
    }
}
