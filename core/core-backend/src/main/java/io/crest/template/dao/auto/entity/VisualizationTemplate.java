package io.crest.template.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 可视化模板实体。
 */
@TableName("core_template")
public class VisualizationTemplate implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 可视化模板主键
     */
    private String id;

    /**
     * 模板名称
     */
    private String name;

    /**
     * 父级模板目录编号
     */
    private String pid;

    /**
     * 模板节点层级
     */
    private Integer level;

    /**
     * 模板所属画布类型，区分大屏和仪表板
     */
    private String dvType;

    /**
     * 节点类型，区分应用和模板
     */
    private String nodeType;

    /**
     * 创建人标识
     */
    private String createBy;

    /**
     * 创建时间戳
     */
    private Long createTime;

    /**
     * 模板缩略图地址
     */
    private String snapshot;

    /**
     * 模板来源类型，区分系统内置和用户自建
     */
    private String templateType;

    /**
     * 模板样式配置
     */
    private String templateStyle;

    /**
     * 模板画布数据
     */
    private String templateData;

    /**
     * 模板预置动态数据
     */
    private String dynamicData;

    /**
     * 应用模板数据
     */
    private String appData;

    /**
     * 模板使用次数
     */
    private Integer useCount;

    /**
     * 模板版本号
     */
    private Integer version;

    /**
     * 获取可视化模板主键
     */
    public String getId() {
        return id;
    }

    /**
     * 设置可视化模板主键
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取模板名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置模板名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取父级模板目录编号
     */
    public String getPid() {
        return pid;
    }

    /**
     * 设置父级模板目录编号
     */
    public void setPid(String pid) {
        this.pid = pid;
    }

    /**
     * 获取模板节点层级
     */
    public Integer getLevel() {
        return level;
    }

    /**
     * 设置模板节点层级
     */
    public void setLevel(Integer level) {
        this.level = level;
    }

    /**
     * 获取模板所属画布类型
     */
    public String getDvType() {
        return dvType;
    }

    /**
     * 设置模板所属画布类型
     */
    public void setDvType(String dvType) {
        this.dvType = dvType;
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
     * 获取模板缩略图地址
     */
    public String getSnapshot() {
        return snapshot;
    }

    /**
     * 设置模板缩略图地址
     */
    public void setSnapshot(String snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * 获取模板来源类型
     */
    public String getTemplateType() {
        return templateType;
    }

    /**
     * 设置模板来源类型
     */
    public void setTemplateType(String templateType) {
        this.templateType = templateType;
    }

    /**
     * 获取模板样式配置
     */
    public String getTemplateStyle() {
        return templateStyle;
    }

    /**
     * 设置模板样式配置
     */
    public void setTemplateStyle(String templateStyle) {
        this.templateStyle = templateStyle;
    }

    /**
     * 获取模板画布数据
     */
    public String getTemplateData() {
        return templateData;
    }

    /**
     * 设置模板画布数据
     */
    public void setTemplateData(String templateData) {
        this.templateData = templateData;
    }

    /**
     * 获取模板预置动态数据
     */
    public String getDynamicData() {
        return dynamicData;
    }

    /**
     * 设置模板预置动态数据
     */
    public void setDynamicData(String dynamicData) {
        this.dynamicData = dynamicData;
    }

    /**
     * 获取应用模板数据
     */
    public String getAppData() {
        return appData;
    }

    /**
     * 设置应用模板数据
     */
    public void setAppData(String appData) {
        this.appData = appData;
    }

    /**
     * 获取模板使用次数
     */
    public Integer getUseCount() {
        return useCount;
    }

    /**
     * 设置模板使用次数
     */
    public void setUseCount(Integer useCount) {
        this.useCount = useCount;
    }

    /**
     * 获取模板版本号
     */
    public Integer getVersion() {
        return version;
    }

    /**
     * 设置模板版本号
     */
    public void setVersion(Integer version) {
        this.version = version;
    }

    /**
     * 返回可视化模板实体的调试字符串
     */
    @Override
    public String toString() {
        return "VisualizationTemplate{" +
        "id = " + id +
        ", name = " + name +
        ", pid = " + pid +
        ", level = " + level +
        ", dvType = " + dvType +
        ", nodeType = " + nodeType +
        ", createBy = " + createBy +
        ", createTime = " + createTime +
        ", snapshot = " + snapshot +
        ", templateType = " + templateType +
        ", templateStyle = " + templateStyle +
        ", templateData = " + templateData +
        ", dynamicData = " + dynamicData +
        ", appData = " + appData +
        ", useCount = " + useCount +
        ", version = " + version +
        "}";
    }
}
