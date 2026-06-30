package io.crest.dataset.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 数据集表实体。
 */
@TableName("core_dataset_table")
public class CoreDatasetTable implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 数据集表主键
     */
    private Long id;

    /**
     * 数据集表显示名称
     */
    private String name;

    /**
     * 数据源中的物理表名
     */
    private String tableName;

    /**
     * 所属数据源编号
     */
    private Long datasourceId;

    /**
     * 所属数据集分组编号
     */
    private Long datasetGroupId;

    /**
     * 数据集表来源类型，包含数据库表、SQL、关联、Excel 和 API
     */
    private String type;

    /**
     * 表原始信息，保存表名、SQL 或 API 配置等内容
     */
    private String info;

    /**
     * SQL 参数配置详情
     */
    private String sqlVariableDetails;

    /**
     * 获取数据集表主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置数据集表主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取数据集表显示名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置数据集表显示名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取数据源中的物理表名
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * 设置数据源中的物理表名
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * 获取所属数据源编号
     */
    public Long getDatasourceId() {
        return datasourceId;
    }

    /**
     * 设置所属数据源编号
     */
    public void setDatasourceId(Long datasourceId) {
        this.datasourceId = datasourceId;
    }

    /**
     * 获取所属数据集分组编号
     */
    public Long getDatasetGroupId() {
        return datasetGroupId;
    }

    /**
     * 设置所属数据集分组编号
     */
    public void setDatasetGroupId(Long datasetGroupId) {
        this.datasetGroupId = datasetGroupId;
    }

    /**
     * 获取数据集表来源类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置数据集表来源类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取表原始信息配置
     */
    public String getInfo() {
        return info;
    }

    /**
     * 设置表原始信息配置
     */
    public void setInfo(String info) {
        this.info = info;
    }

    /**
     * 获取 SQL 参数配置详情
     */
    public String getSqlVariableDetails() {
        return sqlVariableDetails;
    }

    /**
     * 设置 SQL 参数配置详情
     */
    public void setSqlVariableDetails(String sqlVariableDetails) {
        this.sqlVariableDetails = sqlVariableDetails;
    }

    /**
     * 返回数据集表实体的调试字符串
     */
    @Override
    public String toString() {
        return "CoreDatasetTable{" +
        "id = " + id +
        ", name = " + name +
        ", tableName = " + tableName +
        ", datasourceId = " + datasourceId +
        ", datasetGroupId = " + datasetGroupId +
        ", type = " + type +
        ", info = " + info +
        ", sqlVariableDetails = " + sqlVariableDetails +
        "}";
    }
}
