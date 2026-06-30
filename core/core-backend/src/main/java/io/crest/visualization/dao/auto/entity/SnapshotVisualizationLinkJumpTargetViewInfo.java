package io.crest.visualization.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 组件跳转目标字段快照实体。
 */
@TableName("core_visualization_jump_target_snapshot")
public class SnapshotVisualizationLinkJumpTargetViewInfo implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 跳转目标快照配置主键
     */
    @TableId("target_id")
    private Long targetId;

    /**
     * 跳转规则明细编号
     */
    private Long linkJumpInfoId;

    /**
     * 触发跳转时参与匹配的源字段编号
     */
    private Long sourceFieldActiveId;

    /**
     * 目标图表编号
     */
    private String targetViewId;

    /**
     * 目标字段编号
     */
    private String targetFieldId;

    /**
     * 复制来源资源编号
     */
    private Long copyFrom;

    /**
     * 复制来源记录编号
     */
    private Long copyId;

    /**
     * 跳转目标类型，区分图表、过滤组件和外部参数
     */
    private String targetType;

    /**
     * 获取跳转目标快照配置主键
     */
    public Long getTargetId() {
        return targetId;
    }

    /**
     * 设置跳转目标快照配置主键
     */
    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    /**
     * 获取跳转规则明细编号
     */
    public Long getLinkJumpInfoId() {
        return linkJumpInfoId;
    }

    /**
     * 设置跳转规则明细编号
     */
    public void setLinkJumpInfoId(Long linkJumpInfoId) {
        this.linkJumpInfoId = linkJumpInfoId;
    }

    /**
     * 获取触发跳转时参与匹配的源字段编号
     */
    public Long getSourceFieldActiveId() {
        return sourceFieldActiveId;
    }

    /**
     * 设置触发跳转时参与匹配的源字段编号
     */
    public void setSourceFieldActiveId(Long sourceFieldActiveId) {
        this.sourceFieldActiveId = sourceFieldActiveId;
    }

    /**
     * 获取目标图表编号
     */
    public String getTargetViewId() {
        return targetViewId;
    }

    /**
     * 设置目标图表编号
     */
    public void setTargetViewId(String targetViewId) {
        this.targetViewId = targetViewId;
    }

    /**
     * 获取目标字段编号
     */
    public String getTargetFieldId() {
        return targetFieldId;
    }

    /**
     * 设置目标字段编号
     */
    public void setTargetFieldId(String targetFieldId) {
        this.targetFieldId = targetFieldId;
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
     * 获取跳转目标类型
     */
    public String getTargetType() {
        return targetType;
    }

    /**
     * 设置跳转目标类型
     */
    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    /**
     * 返回跳转目标字段快照实体的调试字符串
     */
    @Override
    public String toString() {
        return "SnapshotVisualizationLinkJumpTargetViewInfo{" +
        "targetId = " + targetId +
        ", linkJumpInfoId = " + linkJumpInfoId +
        ", sourceFieldActiveId = " + sourceFieldActiveId +
        ", targetViewId = " + targetViewId +
        ", targetFieldId = " + targetFieldId +
        ", copyFrom = " + copyFrom +
        ", copyId = " + copyId +
        ", targetType = " + targetType +
        "}";
    }
}
