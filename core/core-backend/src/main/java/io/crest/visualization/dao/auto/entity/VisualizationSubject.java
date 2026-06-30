package io.crest.visualization.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 可视化主题实体。
 */
@TableName("core_visualization_theme")
public class VisualizationSubject implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 主题主键
     */
    private String id;

    /**
     * 主题名称
     */
    private String name;

    /**
     * 主题类型，system 表示系统主题，self 表示自定义主题
     */
    private String type;

    /**
     * 主题内容配置
     */
    private String details;

    /**
     * 删除标记
     */
    private Boolean deleteFlag;

    /**
     * 封面资源地址
     */
    private String coverUrl;

    /**
     * 主题创建次数统计
     */
    private Integer createNum;

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
     * 删除时间戳
     */
    private Long deleteTime;

    /**
     * 删除人用户编号
     */
    private Long deleteBy;

    /**
     * 获取主题主键
     */
    public String getId() {
        return id;
    }

    /**
     * 设置主题主键
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取主题名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置主题名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取主题类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置主题类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取主题内容配置
     */
    public String getDetails() {
        return details;
    }

    /**
     * 设置主题内容配置
     */
    public void setDetails(String details) {
        this.details = details;
    }

    /**
     * 获取删除标记
     */
    public Boolean getDeleteFlag() {
        return deleteFlag;
    }

    /**
     * 设置删除标记
     */
    public void setDeleteFlag(Boolean deleteFlag) {
        this.deleteFlag = deleteFlag;
    }

    /**
     * 获取封面资源地址
     */
    public String getCoverUrl() {
        return coverUrl;
    }

    /**
     * 设置封面资源地址
     */
    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    /**
     * 获取主题创建次数统计
     */
    public Integer getCreateNum() {
        return createNum;
    }

    /**
     * 设置主题创建次数统计
     */
    public void setCreateNum(Integer createNum) {
        this.createNum = createNum;
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
     * 获取删除人用户编号
     */
    public Long getDeleteBy() {
        return deleteBy;
    }

    /**
     * 设置删除人用户编号
     */
    public void setDeleteBy(Long deleteBy) {
        this.deleteBy = deleteBy;
    }

    /**
     * 返回可视化主题实体的调试字符串
     */
    @Override
    public String toString() {
        return "VisualizationSubject{" +
        "id = " + id +
        ", name = " + name +
        ", type = " + type +
        ", details = " + details +
        ", deleteFlag = " + deleteFlag +
        ", coverUrl = " + coverUrl +
        ", createNum = " + createNum +
        ", createTime = " + createTime +
        ", createBy = " + createBy +
        ", updateTime = " + updateTime +
        ", updateBy = " + updateBy +
        ", deleteTime = " + deleteTime +
        ", deleteBy = " + deleteBy +
        "}";
    }
}
