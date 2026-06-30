package io.crest.visualization.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 组件联动主配置实体。
 */
@TableName("core_visualization_linkage")
public class VisualizationLinkage implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 联动配置主键
     */
    private Long id;

    /**
     * 所属大屏或仪表板编号
     */
    private Long dvId;

    /**
     * 发起联动的源图表编号
     */
    private Long sourceViewId;

    /**
     * 接收联动的目标图表编号
     */
    private Long targetViewId;

    /**
     * 更新时间戳
     */
    private Long updateTime;

    /**
     * 更新人标识
     */
    private String updatePeople;

    /**
     * 是否启用当前联动关系
     */
    private Boolean linkageActive;

    /**
     * 扩展字段一
     */
    private String ext1;

    /**
     * 扩展字段二
     */
    private String ext2;

    /**
     * 复制来源资源编号
     */
    private Long copyFrom;

    /**
     * 复制来源记录编号
     */
    private Long copyId;

    /**
     * 获取联动配置主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置联动配置主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取所属大屏或仪表板编号
     */
    public Long getDvId() {
        return dvId;
    }

    /**
     * 设置所属大屏或仪表板编号
     */
    public void setDvId(Long dvId) {
        this.dvId = dvId;
    }

    /**
     * 获取发起联动的源图表编号
     */
    public Long getSourceViewId() {
        return sourceViewId;
    }

    /**
     * 设置发起联动的源图表编号
     */
    public void setSourceViewId(Long sourceViewId) {
        this.sourceViewId = sourceViewId;
    }

    /**
     * 获取接收联动的目标图表编号
     */
    public Long getTargetViewId() {
        return targetViewId;
    }

    /**
     * 设置接收联动的目标图表编号
     */
    public void setTargetViewId(Long targetViewId) {
        this.targetViewId = targetViewId;
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
    public String getUpdatePeople() {
        return updatePeople;
    }

    /**
     * 设置更新人标识
     */
    public void setUpdatePeople(String updatePeople) {
        this.updatePeople = updatePeople;
    }

    /**
     * 获取当前联动关系启用状态
     */
    public Boolean getLinkageActive() {
        return linkageActive;
    }

    /**
     * 设置当前联动关系启用状态
     */
    public void setLinkageActive(Boolean linkageActive) {
        this.linkageActive = linkageActive;
    }

    /**
     * 获取扩展字段一
     */
    public String getExt1() {
        return ext1;
    }

    /**
     * 设置扩展字段一
     */
    public void setExt1(String ext1) {
        this.ext1 = ext1;
    }

    /**
     * 获取扩展字段二
     */
    public String getExt2() {
        return ext2;
    }

    /**
     * 设置扩展字段二
     */
    public void setExt2(String ext2) {
        this.ext2 = ext2;
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
     * 获取复制来源记录编号
     */
    public Long getCopyId() {
        return copyId;
    }

    /**
     * 设置复制来源记录编号
     */
    public void setCopyId(Long copyId) {
        this.copyId = copyId;
    }

    /**
     * 返回联动配置实体的调试字符串
     */
    @Override
    public String toString() {
        return "VisualizationLinkage{" +
        "id = " + id +
        ", dvId = " + dvId +
        ", sourceViewId = " + sourceViewId +
        ", targetViewId = " + targetViewId +
        ", updateTime = " + updateTime +
        ", updatePeople = " + updatePeople +
        ", linkageActive = " + linkageActive +
        ", ext1 = " + ext1 +
        ", ext2 = " + ext2 +
        ", copyFrom = " + copyFrom +
        ", copyId = " + copyId +
        "}";
    }
}
