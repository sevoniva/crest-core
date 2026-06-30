package io.crest.template.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 可视化模板分类实体。
 */
@TableName("core_template_category")
public class VisualizationTemplateCategory implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 模板分类主键
     */
    private String id;

    /**
     * 模板分类名称
     */
    private String name;

    /**
     * 父级分类编号
     */
    private String pid;

    /**
     * 分类层级
     */
    private Integer level;

    /**
     * 模板所属画布类型，区分大屏和仪表板
     */
    private String dvType;

    /**
     * 节点类型，区分文件夹和模板面板
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
     * 模板业务类型
     */
    private String templateType;

    /**
     * 获取模板分类主键
     */
    public String getId() {
        return id;
    }

    /**
     * 设置模板分类主键
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取模板分类名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置模板分类名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取父级分类编号
     */
    public String getPid() {
        return pid;
    }

    /**
     * 设置父级分类编号
     */
    public void setPid(String pid) {
        this.pid = pid;
    }

    /**
     * 获取分类层级
     */
    public Integer getLevel() {
        return level;
    }

    /**
     * 设置分类层级
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
     * 获取模板业务类型
     */
    public String getTemplateType() {
        return templateType;
    }

    /**
     * 设置模板业务类型
     */
    public void setTemplateType(String templateType) {
        this.templateType = templateType;
    }

    /**
     * 返回模板分类实体的调试字符串
     */
    @Override
    public String toString() {
        return "VisualizationTemplateCategory{" +
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
        "}";
    }
}
