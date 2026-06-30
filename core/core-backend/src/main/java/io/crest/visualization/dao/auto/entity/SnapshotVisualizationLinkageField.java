package io.crest.visualization.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 组件联动字段快照实体。
 */
@TableName("core_visualization_linkage_field_snapshot")
public class SnapshotVisualizationLinkageField implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 联动字段快照记录主键
     */
    private Long id;

    /**
     * 所属联动配置 ID
     */
    private Long linkageId;

    /**
     * 来源图表字段 ID
     */
    private Long sourceField;

    /**
     * 目标图表字段 ID
     */
    private Long targetField;

    /**
     * 记录更新时间
     */
    private Long updateTime;

    /**
     * 复制来源类型或标识
     */
    private Long copyFrom;

    /**
     * 复制来源记录 ID
     */
    private Long copyId;

    /**
     * 获取联动字段快照记录主键
     *
     * @return 联动字段快照记录主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置联动字段快照记录主键
     *
     * @param id 联动字段快照记录主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取所属联动配置 ID
     *
     * @return 所属联动配置 ID
     */
    public Long getLinkageId() {
        return linkageId;
    }

    /**
     * 设置所属联动配置 ID
     *
     * @param linkageId 所属联动配置 ID
     */
    public void setLinkageId(Long linkageId) {
        this.linkageId = linkageId;
    }

    /**
     * 获取来源图表字段 ID
     *
     * @return 来源图表字段 ID
     */
    public Long getSourceField() {
        return sourceField;
    }

    /**
     * 设置来源图表字段 ID
     *
     * @param sourceField 来源图表字段 ID
     */
    public void setSourceField(Long sourceField) {
        this.sourceField = sourceField;
    }

    /**
     * 获取目标图表字段 ID
     *
     * @return 目标图表字段 ID
     */
    public Long getTargetField() {
        return targetField;
    }

    /**
     * 设置目标图表字段 ID
     *
     * @param targetField 目标图表字段 ID
     */
    public void setTargetField(Long targetField) {
        this.targetField = targetField;
    }

    /**
     * 获取记录更新时间
     *
     * @return 记录更新时间
     */
    public Long getUpdateTime() {
        return updateTime;
    }

    /**
     * 设置记录更新时间
     *
     * @param updateTime 记录更新时间
     */
    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * 获取复制来源类型或标识
     *
     * @return 复制来源类型或标识
     */
    public Long getCopyFrom() {
        return copyFrom;
    }

    /**
     * 设置复制来源类型或标识
     *
     * @param copyFrom 复制来源类型或标识
     */
    public void setCopyFrom(Long copyFrom) {
        this.copyFrom = copyFrom;
    }

    /**
     * 获取复制来源记录 ID
     *
     * @return 复制来源记录 ID
     */
    public Long getCopyId() {
        return copyId;
    }

    /**
     * 设置复制来源记录 ID
     *
     * @param copyId 复制来源记录 ID
     */
    public void setCopyId(Long copyId) {
        this.copyId = copyId;
    }

    /**
     * 返回联动字段快照记录的调试字符串
     *
     * @return 联动字段快照记录的字符串表示
     */
    @Override
    public String toString() {
        return "SnapshotVisualizationLinkageField{" +
        "id = " + id +
        ", linkageId = " + linkageId +
        ", sourceField = " + sourceField +
        ", targetField = " + targetField +
        ", updateTime = " + updateTime +
        ", copyFrom = " + copyFrom +
        ", copyId = " + copyId +
        "}";
    }
}
