package io.crest.chart.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 组件图表实体。
 */
@TableName("core_chart_view")
public class CoreChartView implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 图表主键
     */
    private Long id;

    /**
     * 图表标题
     */
    private String title;

    /**
     * 所属场景编号，私有图表时对应仪表板编号
     */
    private Long sceneId;

    /**
     * 绑定的数据集表编号
     */
    private Long tableId;

    /**
     * 图表类型标识
     */
    private String type;

    /**
     * 图表渲染引擎标识
     */
    private String render;

    /**
     * 展示结果数量
     */
    private Integer resultCount;

    /**
     * 展示模式
     */
    private String resultMode;

    /**
     * 横轴字段配置
     */
    private String xAxis;

    /**
     * 横轴扩展字段配置
     */
    private String xAxisExt;

    /**
     * 纵轴字段配置
     */
    private String yAxis;

    /**
     * 副轴字段配置
     */
    private String yAxisExt;

    /**
     * 堆叠字段配置
     */
    private String extStack;

    /**
     * 气泡大小字段配置
     */
    private String extBubble;

    /**
     * 动态标签字段配置
     */
    private String extLabel;

    /**
     * 动态提示字段配置
     */
    private String extTooltip;

    /**
     * 图表属性配置
     */
    private String customAttr;

    /**
     * 组件样式配置
     */
    private String customStyle;

    /**
     * 图表结果过滤配置
     */
    private String customFilter;

    /**
     * 图表钻取字段配置
     */
    private String drillFields;

    /**
     * 图表高级配置
     */
    private String senior;

    /**
     * 创建人标识
     */
    private String createBy;

    /**
     * 创建时间戳
     */
    private Long createTime;

    /**
     * 更新时间戳
     */
    private Long updateTime;

    /**
     * 图表缩略图地址
     */
    private String snapshot;

    /**
     * 样式优先级，区分仪表板级和图表级配置
     */
    private String stylePriority;

    /**
     * 图表可见类型，区分公共复用图表和仪表板私有图表
     */
    private String chartType;

    /**
     * 是否为插件图表
     */
    private Boolean isPlugin;

    /**
     * 数据来源，区分模板数据和数据集数据
     */
    private String dataFrom;

    /**
     * 图表字段集合配置
     */
    private String viewFields;

    /**
     * 是否开启图表自动刷新
     */
    private Boolean refreshViewEnable;

    /**
     * 自动刷新时间单位
     */
    private String refreshUnit;

    /**
     * 自动刷新时间间隔
     */
    private Integer refreshTime;

    /**
     * 是否开启图表联动
     */
    private Boolean linkageActive;

    /**
     * 是否开启图表跳转
     */
    private Boolean jumpActive;

    /**
     * 复制来源资源编号
     */
    private Long copyFrom;

    /**
     * 复制来源图表编号
     */
    private Long copyId;

    /**
     * 区间条形图是否按时间维度聚合
     */
    private Boolean aggregate;

    /**
     * 流向地图起点名称字段
     */
    private String flowMapStartName;

    /**
     * 流向地图终点名称字段
     */
    private String flowMapEndName;

    /**
     * 颜色维度字段配置
     */
    private String extColor;

    /**
     * 移动端图表属性配置
     */
    private String customAttrMobile;

    /**
     * 移动端组件样式配置
     */
    private String customStyleMobile;

    /**
     * 字段排序优先级配置
     */
    private String sortPriority;

    /**
     * 获取图表主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置图表主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取图表标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 设置图表标题
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * 获取所属场景编号
     */
    public Long getSceneId() {
        return sceneId;
    }

    /**
     * 设置所属场景编号
     */
    public void setSceneId(Long sceneId) {
        this.sceneId = sceneId;
    }

    /**
     * 获取绑定的数据集表编号
     */
    public Long getTableId() {
        return tableId;
    }

    /**
     * 设置绑定的数据集表编号
     */
    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    /**
     * 获取图表类型标识
     */
    public String getType() {
        return type;
    }

    /**
     * 设置图表类型标识
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取图表渲染引擎标识
     */
    public String getRender() {
        return render;
    }

    /**
     * 设置图表渲染引擎标识
     */
    public void setRender(String render) {
        this.render = render;
    }

    /**
     * 获取展示结果数量
     */
    public Integer getResultCount() {
        return resultCount;
    }

    /**
     * 设置展示结果数量
     */
    public void setResultCount(Integer resultCount) {
        this.resultCount = resultCount;
    }

    /**
     * 获取展示模式
     */
    public String getResultMode() {
        return resultMode;
    }

    /**
     * 设置展示模式
     */
    public void setResultMode(String resultMode) {
        this.resultMode = resultMode;
    }

    /**
     * 获取横轴字段配置
     */
    public String getxAxis() {
        return xAxis;
    }

    /**
     * 设置横轴字段配置
     */
    public void setxAxis(String xAxis) {
        this.xAxis = xAxis;
    }

    /**
     * 获取横轴扩展字段配置
     */
    public String getxAxisExt() {
        return xAxisExt;
    }

    /**
     * 设置横轴扩展字段配置
     */
    public void setxAxisExt(String xAxisExt) {
        this.xAxisExt = xAxisExt;
    }

    /**
     * 获取纵轴字段配置
     */
    public String getyAxis() {
        return yAxis;
    }

    /**
     * 设置纵轴字段配置
     */
    public void setyAxis(String yAxis) {
        this.yAxis = yAxis;
    }

    /**
     * 获取副轴字段配置
     */
    public String getyAxisExt() {
        return yAxisExt;
    }

    /**
     * 设置副轴字段配置
     */
    public void setyAxisExt(String yAxisExt) {
        this.yAxisExt = yAxisExt;
    }

    /**
     * 获取堆叠字段配置
     */
    public String getExtStack() {
        return extStack;
    }

    /**
     * 设置堆叠字段配置
     */
    public void setExtStack(String extStack) {
        this.extStack = extStack;
    }

    /**
     * 获取气泡大小字段配置
     */
    public String getExtBubble() {
        return extBubble;
    }

    /**
     * 设置气泡大小字段配置
     */
    public void setExtBubble(String extBubble) {
        this.extBubble = extBubble;
    }

    /**
     * 获取动态标签字段配置
     */
    public String getExtLabel() {
        return extLabel;
    }

    /**
     * 设置动态标签字段配置
     */
    public void setExtLabel(String extLabel) {
        this.extLabel = extLabel;
    }

    /**
     * 获取动态提示字段配置
     */
    public String getExtTooltip() {
        return extTooltip;
    }

    /**
     * 设置动态提示字段配置
     */
    public void setExtTooltip(String extTooltip) {
        this.extTooltip = extTooltip;
    }

    /**
     * 获取图表属性配置
     */
    public String getCustomAttr() {
        return customAttr;
    }

    /**
     * 设置图表属性配置
     */
    public void setCustomAttr(String customAttr) {
        this.customAttr = customAttr;
    }

    /**
     * 获取组件样式配置
     */
    public String getCustomStyle() {
        return customStyle;
    }

    /**
     * 设置组件样式配置
     */
    public void setCustomStyle(String customStyle) {
        this.customStyle = customStyle;
    }

    /**
     * 获取图表结果过滤配置
     */
    public String getCustomFilter() {
        return customFilter;
    }

    /**
     * 设置图表结果过滤配置
     */
    public void setCustomFilter(String customFilter) {
        this.customFilter = customFilter;
    }

    /**
     * 获取图表钻取字段配置
     */
    public String getDrillFields() {
        return drillFields;
    }

    /**
     * 设置图表钻取字段配置
     */
    public void setDrillFields(String drillFields) {
        this.drillFields = drillFields;
    }

    /**
     * 获取图表高级配置
     */
    public String getSenior() {
        return senior;
    }

    /**
     * 设置图表高级配置
     */
    public void setSenior(String senior) {
        this.senior = senior;
    }

    /**
     * 获取创建人标识
     */
    public String getCreateBy() {
        return createBy;
    }

    /**
     * 设置创建人标识
     */
    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    /**
     * 获取创建时间戳
     */
    public Long getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间戳
     */
    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取更新时间戳
     */
    public Long getUpdateTime() {
        return updateTime;
    }

    /**
     * 设置更新时间戳
     */
    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * 获取图表缩略图地址
     */
    public String getSnapshot() {
        return snapshot;
    }

    /**
     * 设置图表缩略图地址
     */
    public void setSnapshot(String snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * 获取样式优先级
     */
    public String getStylePriority() {
        return stylePriority;
    }

    /**
     * 设置样式优先级
     */
    public void setStylePriority(String stylePriority) {
        this.stylePriority = stylePriority;
    }

    /**
     * 获取图表可见类型
     */
    public String getChartType() {
        return chartType;
    }

    /**
     * 设置图表可见类型
     */
    public void setChartType(String chartType) {
        this.chartType = chartType;
    }

    /**
     * 获取是否为插件图表
     */
    public Boolean getIsPlugin() {
        return isPlugin;
    }

    /**
     * 设置是否为插件图表
     */
    public void setIsPlugin(Boolean isPlugin) {
        this.isPlugin = isPlugin;
    }

    /**
     * 获取数据来源
     */
    public String getDataFrom() {
        return dataFrom;
    }

    /**
     * 设置数据来源
     */
    public void setDataFrom(String dataFrom) {
        this.dataFrom = dataFrom;
    }

    /**
     * 获取图表字段集合配置
     */
    public String getViewFields() {
        return viewFields;
    }

    /**
     * 设置图表字段集合配置
     */
    public void setViewFields(String viewFields) {
        this.viewFields = viewFields;
    }

    /**
     * 获取是否开启图表自动刷新
     */
    public Boolean getRefreshViewEnable() {
        return refreshViewEnable;
    }

    /**
     * 设置是否开启图表自动刷新
     */
    public void setRefreshViewEnable(Boolean refreshViewEnable) {
        this.refreshViewEnable = refreshViewEnable;
    }

    /**
     * 获取自动刷新时间单位
     */
    public String getRefreshUnit() {
        return refreshUnit;
    }

    /**
     * 设置自动刷新时间单位
     */
    public void setRefreshUnit(String refreshUnit) {
        this.refreshUnit = refreshUnit;
    }

    /**
     * 获取自动刷新时间间隔
     */
    public Integer getRefreshTime() {
        return refreshTime;
    }

    /**
     * 设置自动刷新时间间隔
     */
    public void setRefreshTime(Integer refreshTime) {
        this.refreshTime = refreshTime;
    }

    /**
     * 获取图表联动开启状态
     */
    public Boolean getLinkageActive() {
        return linkageActive;
    }

    /**
     * 设置图表联动开启状态
     */
    public void setLinkageActive(Boolean linkageActive) {
        this.linkageActive = linkageActive;
    }

    /**
     * 获取图表跳转开启状态
     */
    public Boolean getJumpActive() {
        return jumpActive;
    }

    /**
     * 设置图表跳转开启状态
     */
    public void setJumpActive(Boolean jumpActive) {
        this.jumpActive = jumpActive;
    }

    /**
     * 获取复制来源资源编号
     */
    public Long getCopyFrom() {
        return copyFrom;
    }

    /**
     * 设置复制来源资源编号
     */
    public void setCopyFrom(Long copyFrom) {
        this.copyFrom = copyFrom;
    }

    /**
     * 获取复制来源图表编号
     */
    public Long getCopyId() {
        return copyId;
    }

    /**
     * 设置复制来源图表编号
     */
    public void setCopyId(Long copyId) {
        this.copyId = copyId;
    }

    /**
     * 获取区间条形图时间维度聚合状态
     */
    public Boolean getAggregate() {
        return aggregate;
    }

    /**
     * 设置区间条形图时间维度聚合状态
     */
    public void setAggregate(Boolean aggregate) {
        this.aggregate = aggregate;
    }

    /**
     * 获取流向地图起点名称字段
     */
    public String getFlowMapStartName() {
        return flowMapStartName;
    }

    /**
     * 设置流向地图起点名称字段
     */
    public void setFlowMapStartName(String flowMapStartName) {
        this.flowMapStartName = flowMapStartName;
    }

    /**
     * 获取流向地图终点名称字段
     */
    public String getFlowMapEndName() {
        return flowMapEndName;
    }

    /**
     * 设置流向地图终点名称字段
     */
    public void setFlowMapEndName(String flowMapEndName) {
        this.flowMapEndName = flowMapEndName;
    }

    /**
     * 获取颜色维度字段配置
     */
    public String getExtColor() {
        return extColor;
    }

    /**
     * 设置颜色维度字段配置
     */
    public void setExtColor(String extColor) {
        this.extColor = extColor;
    }

    /**
     * 获取移动端图表属性配置
     */
    public String getCustomAttrMobile() {
        return customAttrMobile;
    }

    /**
     * 设置移动端图表属性配置
     */
    public void setCustomAttrMobile(String customAttrMobile) {
        this.customAttrMobile = customAttrMobile;
    }

    /**
     * 获取移动端组件样式配置
     */
    public String getCustomStyleMobile() {
        return customStyleMobile;
    }

    /**
     * 设置移动端组件样式配置
     */
    public void setCustomStyleMobile(String customStyleMobile) {
        this.customStyleMobile = customStyleMobile;
    }

    /**
     * 获取字段排序优先级配置
     */
    public String getSortPriority() {
        return sortPriority;
    }

    /**
     * 设置字段排序优先级配置
     */
    public void setSortPriority(String sortPriority) {
        this.sortPriority = sortPriority;
    }

    /**
     * 返回图表实体的调试字符串
     */
    @Override
    public String toString() {
        return "CoreChartView{" +
        "id = " + id +
        ", title = " + title +
        ", sceneId = " + sceneId +
        ", tableId = " + tableId +
        ", type = " + type +
        ", render = " + render +
        ", resultCount = " + resultCount +
        ", resultMode = " + resultMode +
        ", xAxis = " + xAxis +
        ", xAxisExt = " + xAxisExt +
        ", yAxis = " + yAxis +
        ", yAxisExt = " + yAxisExt +
        ", extStack = " + extStack +
        ", extBubble = " + extBubble +
        ", extLabel = " + extLabel +
        ", extTooltip = " + extTooltip +
        ", customAttr = " + customAttr +
        ", customStyle = " + customStyle +
        ", customFilter = " + customFilter +
        ", drillFields = " + drillFields +
        ", senior = " + senior +
        ", createBy = " + createBy +
        ", createTime = " + createTime +
        ", updateTime = " + updateTime +
        ", snapshot = " + snapshot +
        ", stylePriority = " + stylePriority +
        ", chartType = " + chartType +
        ", isPlugin = " + isPlugin +
        ", dataFrom = " + dataFrom +
        ", viewFields = " + viewFields +
        ", refreshViewEnable = " + refreshViewEnable +
        ", refreshUnit = " + refreshUnit +
        ", refreshTime = " + refreshTime +
        ", linkageActive = " + linkageActive +
        ", jumpActive = " + jumpActive +
        ", copyFrom = " + copyFrom +
        ", copyId = " + copyId +
        ", aggregate = " + aggregate +
        ", flowMapStartName = " + flowMapStartName +
        ", flowMapEndName = " + flowMapEndName +
        ", extColor = " + extColor +
        ", customAttrMobile = " + customAttrMobile +
        ", customStyleMobile = " + customStyleMobile +
        ", sortPriority = " + sortPriority +
        "}";
    }
}
