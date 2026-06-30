package io.crest.dataset.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 数据集表字段实体。
 */
@TableName("core_dataset_field")
public class CoreDatasetTableField implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 数据集字段主键
     */
    private Long id;

    /**
     * 所属数据源编号
     */
    private Long datasourceId;

    /**
     * 所属数据集表编号
     */
    private Long datasetTableId;

    /**
     * 所属数据集分组编号
     */
    private Long datasetGroupId;

    /**
     * 关联图表编号
     */
    private Long chartId;

    /**
     * 数据源中的原始字段名
     */
    private String originName;

    /**
     * 页面展示字段名
     */
    private String name;

    /**
     * 字段描述
     */
    private String description;

    /**
     * 数据引擎字段名，用作字段唯一标识
     */
    private String engineFieldName;

    /**
     * 字段短别名
     */
    private String fieldShortName;

    /**
     * 字段分组配置
     */
    private String groupList;

    /**
     * 未命中分组时使用的默认分组值
     */
    private String otherGroup;

    /**
     * 字段用途类型，d 表示维度，q 表示指标
     */
    private String groupType;

    /**
     * 数据源原始字段类型
     */
    private String type;

    /**
     * 字段长度，允许为空
     */
    private Integer size;

    /**
     * 系统标准字段类型
     */
    private Integer fieldType;

    /**
     * 从数据源提取到的原始字段类型
     */
    private Integer extractedFieldType;

    /**
     * 扩展字段类型，区分原始字段、复制字段和计算字段
     */
    private Integer extField;

    /**
     * 字段是否被选中使用
     */
    private Boolean checked;

    /**
     * 字段在表中的列位置
     */
    private Integer columnIndex;

    /**
     * 字段最后同步时间戳
     */
    private Long lastSyncTime;

    /**
     * 数值字段精度
     */
    private Integer accuracy;

    /**
     * 时间字段格式
     */
    private String dateFormat;

    /**
     * 时间格式类型
     */
    private String dateFormatType;

    /**
     * 计算字段参数配置
     */
    private String params;

    /**
     * 是否参与字段排序
     */
    private Boolean orderChecked;

    /**
     * 获取数据集字段主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置数据集字段主键
     */
    public void setId(Long id) {
        this.id = id;
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
     * 获取所属数据集表编号
     */
    public Long getDatasetTableId() {
        return datasetTableId;
    }

    /**
     * 设置所属数据集表编号
     */
    public void setDatasetTableId(Long datasetTableId) {
        this.datasetTableId = datasetTableId;
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
     * 获取关联图表编号
     */
    public Long getChartId() {
        return chartId;
    }

    /**
     * 设置关联图表编号
     */
    public void setChartId(Long chartId) {
        this.chartId = chartId;
    }

    /**
     * 获取数据源中的原始字段名
     */
    public String getOriginName() {
        return originName;
    }

    /**
     * 设置数据源中的原始字段名
     */
    public void setOriginName(String originName) {
        this.originName = originName;
    }

    /**
     * 获取页面展示字段名
     */
    public String getName() {
        return name;
    }

    /**
     * 设置页面展示字段名
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取字段描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置字段描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取数据引擎字段名
     */
    public String getEngineFieldName() {
        return engineFieldName;
    }

    /**
     * 设置数据引擎字段名
     */
    public void setEngineFieldName(String engineFieldName) {
        this.engineFieldName = engineFieldName;
    }

    /**
     * 获取字段短别名
     */
    public String getFieldShortName() {
        return fieldShortName;
    }

    /**
     * 设置字段短别名
     */
    public void setFieldShortName(String fieldShortName) {
        this.fieldShortName = fieldShortName;
    }

    /**
     * 获取字段分组配置
     */
    public String getGroupList() {
        return groupList;
    }

    /**
     * 设置字段分组配置
     */
    public void setGroupList(String groupList) {
        this.groupList = groupList;
    }

    /**
     * 获取未命中分组时使用的默认分组值
     */
    public String getOtherGroup() {
        return otherGroup;
    }

    /**
     * 设置未命中分组时使用的默认分组值
     */
    public void setOtherGroup(String otherGroup) {
        this.otherGroup = otherGroup;
    }

    /**
     * 获取字段用途类型
     */
    public String getGroupType() {
        return groupType;
    }

    /**
     * 设置字段用途类型
     */
    public void setGroupType(String groupType) {
        this.groupType = groupType;
    }

    /**
     * 获取数据源原始字段类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置数据源原始字段类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取字段长度
     */
    public Integer getSize() {
        return size;
    }

    /**
     * 设置字段长度
     */
    public void setSize(Integer size) {
        this.size = size;
    }

    /**
     * 获取系统标准字段类型
     */
    public Integer getFieldType() {
        return fieldType;
    }

    /**
     * 设置系统标准字段类型
     */
    public void setFieldType(Integer fieldType) {
        this.fieldType = fieldType;
    }

    /**
     * 获取从数据源提取到的原始字段类型
     */
    public Integer getExtractedFieldType() {
        return extractedFieldType;
    }

    /**
     * 设置从数据源提取到的原始字段类型
     */
    public void setExtractedFieldType(Integer extractedFieldType) {
        this.extractedFieldType = extractedFieldType;
    }

    /**
     * 获取扩展字段类型
     */
    public Integer getExtField() {
        return extField;
    }

    /**
     * 设置扩展字段类型
     */
    public void setExtField(Integer extField) {
        this.extField = extField;
    }

    /**
     * 获取字段是否被选中使用
     */
    public Boolean getChecked() {
        return checked;
    }

    /**
     * 设置字段是否被选中使用
     */
    public void setChecked(Boolean checked) {
        this.checked = checked;
    }

    /**
     * 获取字段在表中的列位置
     */
    public Integer getColumnIndex() {
        return columnIndex;
    }

    /**
     * 设置字段在表中的列位置
     */
    public void setColumnIndex(Integer columnIndex) {
        this.columnIndex = columnIndex;
    }

    /**
     * 获取字段最后同步时间戳
     */
    public Long getLastSyncTime() {
        return lastSyncTime;
    }

    /**
     * 设置字段最后同步时间戳
     */
    public void setLastSyncTime(Long lastSyncTime) {
        this.lastSyncTime = lastSyncTime;
    }

    /**
     * 获取数值字段精度
     */
    public Integer getAccuracy() {
        return accuracy;
    }

    /**
     * 设置数值字段精度
     */
    public void setAccuracy(Integer accuracy) {
        this.accuracy = accuracy;
    }

    /**
     * 获取时间字段格式
     */
    public String getDateFormat() {
        return dateFormat;
    }

    /**
     * 设置时间字段格式
     */
    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    /**
     * 获取时间格式类型
     */
    public String getDateFormatType() {
        return dateFormatType;
    }

    /**
     * 设置时间格式类型
     */
    public void setDateFormatType(String dateFormatType) {
        this.dateFormatType = dateFormatType;
    }

    /**
     * 获取计算字段参数配置
     */
    public String getParams() {
        return params;
    }

    /**
     * 设置计算字段参数配置
     */
    public void setParams(String params) {
        this.params = params;
    }

    /**
     * 获取是否参与字段排序
     */
    public Boolean getOrderChecked() {
        return orderChecked;
    }

    /**
     * 设置是否参与字段排序
     */
    public void setOrderChecked(Boolean orderChecked) {
        this.orderChecked = orderChecked;
    }

    /**
     * 返回数据集表字段实体的调试字符串
     */
    @Override
    public String toString() {
        return "CoreDatasetTableField{" +
        "id = " + id +
        ", datasourceId = " + datasourceId +
        ", datasetTableId = " + datasetTableId +
        ", datasetGroupId = " + datasetGroupId +
        ", chartId = " + chartId +
        ", originName = " + originName +
        ", name = " + name +
        ", description = " + description +
        ", engineFieldName = " + engineFieldName +
        ", fieldShortName = " + fieldShortName +
        ", groupList = " + groupList +
        ", otherGroup = " + otherGroup +
        ", groupType = " + groupType +
        ", type = " + type +
        ", size = " + size +
        ", fieldType = " + fieldType +
        ", extractedFieldType = " + extractedFieldType +
        ", extField = " + extField +
        ", checked = " + checked +
        ", columnIndex = " + columnIndex +
        ", lastSyncTime = " + lastSyncTime +
        ", accuracy = " + accuracy +
        ", dateFormat = " + dateFormat +
        ", dateFormatType = " + dateFormatType +
        ", params = " + params +
        ", orderChecked = " + orderChecked +
        "}";
    }
}
