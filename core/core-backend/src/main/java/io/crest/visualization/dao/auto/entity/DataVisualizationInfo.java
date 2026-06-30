package io.crest.visualization.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 可视化资源实体。
 */
@TableName("core_visualization")
public class DataVisualizationInfo implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 可视化资源主键
     */
    private Long id;

    /**
     * 可视化资源名称
     */
    private String name;

    /**
     * 父级资源编号
     */
    private Long pid;

    /**
     * 所属组织编号
     */
    private Long orgId;

    /**
     * 资源层级
     */
    private Integer level;

    /**
     * 节点类型，区分文件夹和资源面板
     */
    private String nodeType;

    /**
     * 可视化资源类型
     */
    private String type;

    /**
     * 画布样式配置数据
     */
    private String canvasStyleData;

    /**
     * 画布组件配置数据
     */
    private String componentData;

    /**
     * 是否启用移动端布局
     */
    private Boolean mobileLayout;

    /**
     * 发布状态，0 表示未发布，1 表示已发布
     */
    private Integer status;

    /**
     * 独立水印开关状态
     */
    private Integer selfWatermarkStatus;

    /**
     * 同级资源排序值
     */
    private Integer sort;

    /**
     * 创建时间戳
     */
    private Long createTime;

    /**
     * 创建人标识
     */
    private String createBy;

    /**
     * 更新时间戳
     */
    private Long updateTime;

    /**
     * 更新人标识
     */
    private String updateBy;

    /**
     * 资源备注
     */
    private String remark;

    /**
     * 资源来源
     */
    private String source;

    /**
     * 逻辑删除标记
     */
    private Boolean deleteFlag;

    /**
     * 删除时间戳
     */
    private Long deleteTime;

    /**
     * 删除人标识
     */
    private String deleteBy;

    /**
     * 可视化资源版本号
     */
    private Integer version;

    /**
     * 内容版本标识
     */
    private String contentId;

    /**
     * 内容校验版本标识
     */
    private String checkVersion;

    /**
     * 设置内容版本标识
     */
    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    /**
     * 获取可视化资源主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置可视化资源主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取可视化资源名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置可视化资源名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取父级资源编号
     */
    public Long getPid() {
        return pid;
    }

    /**
     * 设置父级资源编号
     */
    public void setPid(Long pid) {
        this.pid = pid;
    }

    /**
     * 获取所属组织编号
     */
    public Long getOrgId() {
        return orgId;
    }

    /**
     * 设置所属组织编号
     */
    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    /**
     * 获取资源层级
     */
    public Integer getLevel() {
        return level;
    }

    /**
     * 设置资源层级
     */
    public void setLevel(Integer level) {
        this.level = level;
    }

    /**
     * 获取节点类型
     */
    public String getNodeType() {
        return nodeType;
    }

    /**
     * 设置节点类型
     */
    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * 获取可视化资源类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置可视化资源类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取画布样式配置数据
     */
    public String getCanvasStyleData() {
        return canvasStyleData;
    }

    /**
     * 设置画布样式配置数据
     */
    public void setCanvasStyleData(String canvasStyleData) {
        this.canvasStyleData = canvasStyleData;
    }

    /**
     * 获取画布组件配置数据
     */
    public String getComponentData() {
        return componentData;
    }

    /**
     * 设置画布组件配置数据
     */
    public void setComponentData(String componentData) {
        this.componentData = componentData;
    }

    /**
     * 获取是否启用移动端布局
     */
    public Boolean getMobileLayout() {
        return mobileLayout;
    }

    /**
     * 设置是否启用移动端布局
     */
    public void setMobileLayout(Boolean mobileLayout) {
        this.mobileLayout = mobileLayout;
    }

    /**
     * 获取发布状态
     */
    public Integer getStatus() {
        return status;
    }

    /**
     * 设置发布状态
     */
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * 获取独立水印开关状态
     */
    public Integer getSelfWatermarkStatus() {
        return selfWatermarkStatus;
    }

    /**
     * 设置独立水印开关状态
     */
    public void setSelfWatermarkStatus(Integer selfWatermarkStatus) {
        this.selfWatermarkStatus = selfWatermarkStatus;
    }

    /**
     * 获取同级资源排序值
     */
    public Integer getSort() {
        return sort;
    }

    /**
     * 设置同级资源排序值
     */
    public void setSort(Integer sort) {
        this.sort = sort;
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
     * 获取更新人标识
     */
    public String getUpdateBy() {
        return updateBy;
    }

    /**
     * 设置更新人标识
     */
    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    /**
     * 获取资源备注
     */
    public String getRemark() {
        return remark;
    }

    /**
     * 设置资源备注
     */
    public void setRemark(String remark) {
        this.remark = remark;
    }

    /**
     * 获取资源来源
     */
    public String getSource() {
        return source;
    }

    /**
     * 设置资源来源
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * 获取逻辑删除标记
     */
    public Boolean getDeleteFlag() {
        return deleteFlag;
    }

    /**
     * 设置逻辑删除标记
     */
    public void setDeleteFlag(Boolean deleteFlag) {
        this.deleteFlag = deleteFlag;
    }

    /**
     * 获取删除时间戳
     */
    public Long getDeleteTime() {
        return deleteTime;
    }

    /**
     * 设置删除时间戳
     */
    public void setDeleteTime(Long deleteTime) {
        this.deleteTime = deleteTime;
    }

    /**
     * 获取删除人标识
     */
    public String getDeleteBy() {
        return deleteBy;
    }

    /**
     * 设置删除人标识
     */
    public void setDeleteBy(String deleteBy) {
        this.deleteBy = deleteBy;
    }

    /**
     * 获取可视化资源版本号
     */
    public Integer getVersion() {
        return version;
    }

    /**
     * 设置可视化资源版本号
     */
    public void setVersion(Integer version) {
        this.version = version;
    }

    /**
     * 获取内容版本标识
     */
    public String getContentId() {
        return contentId;
    }

    /**
     * 获取内容校验版本标识
     */
    public String getCheckVersion() {
        return checkVersion;
    }

    /**
     * 设置内容校验版本标识
     */
    public void setCheckVersion(String checkVersion) {
        this.checkVersion = checkVersion;
    }

    /**
     * 返回可视化资源实体的调试字符串
     */
    @Override
    public String toString() {
        return "DataVisualizationInfo{" +
        "id = " + id +
        ", name = " + name +
        ", pid = " + pid +
        ", orgId = " + orgId +
        ", level = " + level +
        ", nodeType = " + nodeType +
        ", type = " + type +
        ", canvasStyleData = " + canvasStyleData +
        ", componentData = " + componentData +
        ", mobileLayout = " + mobileLayout +
        ", status = " + status +
        ", selfWatermarkStatus = " + selfWatermarkStatus +
        ", sort = " + sort +
        ", createTime = " + createTime +
        ", createBy = " + createBy +
        ", updateTime = " + updateTime +
        ", updateBy = " + updateBy +
        ", remark = " + remark +
        ", source = " + source +
        ", deleteFlag = " + deleteFlag +
        ", deleteTime = " + deleteTime +
        ", deleteBy = " + deleteBy +
        ", version = " + version +
        ", contentId = " + contentId +
        ", checkVersion = " + checkVersion +
        "}";
    }
}
