package io.crest.visualization.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 组件跳转快照实体。
 */
@TableName("core_visualization_jump_snapshot")
public class SnapshotVisualizationLinkJump implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 跳转快照记录主键
     */
    private Long id;

    /**
     * 来源仪表板 ID
     */
    private Long sourceDvId;

    /**
     * 来源图表 ID
     */
    private Long sourceViewId;

    /**
     * 图表跳转配置详情
     */
    private String linkJumpInfo;

    /**
     * 是否启用该跳转配置
     */
    private Boolean checked;

    /**
     * 复制来源类型或标识
     */
    private Long copyFrom;

    /**
     * 复制来源记录 ID
     */
    private Long copyId;

    /**
     * 获取跳转快照记录主键
     *
     * @return 跳转快照记录主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置跳转快照记录主键
     *
     * @param id 跳转快照记录主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取来源仪表板 ID
     *
     * @return 来源仪表板 ID
     */
    public Long getSourceDvId() {
        return sourceDvId;
    }

    /**
     * 设置来源仪表板 ID
     *
     * @param sourceDvId 来源仪表板 ID
     */
    public void setSourceDvId(Long sourceDvId) {
        this.sourceDvId = sourceDvId;
    }

    /**
     * 获取来源图表 ID
     *
     * @return 来源图表 ID
     */
    public Long getSourceViewId() {
        return sourceViewId;
    }

    /**
     * 设置来源图表 ID
     *
     * @param sourceViewId 来源图表 ID
     */
    public void setSourceViewId(Long sourceViewId) {
        this.sourceViewId = sourceViewId;
    }

    /**
     * 获取图表跳转配置详情
     *
     * @return 图表跳转配置详情
     */
    public String getLinkJumpInfo() {
        return linkJumpInfo;
    }

    /**
     * 设置图表跳转配置详情
     *
     * @param linkJumpInfo 图表跳转配置详情
     */
    public void setLinkJumpInfo(String linkJumpInfo) {
        this.linkJumpInfo = linkJumpInfo;
    }

    /**
     * 获取是否启用该跳转配置
     *
     * @return 是否启用该跳转配置
     */
    public Boolean getChecked() {
        return checked;
    }

    /**
     * 设置是否启用该跳转配置
     *
     * @param checked 是否启用该跳转配置
     */
    public void setChecked(Boolean checked) {
        this.checked = checked;
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
     * 返回跳转快照记录的调试字符串
     *
     * @return 跳转快照记录的字符串表示
     */
    @Override
    public String toString() {
        return "SnapshotVisualizationLinkJump{" +
        "id = " + id +
        ", sourceDvId = " + sourceDvId +
        ", sourceViewId = " + sourceViewId +
        ", linkJumpInfo = " + linkJumpInfo +
        ", checked = " + checked +
        ", copyFrom = " + copyFrom +
        ", copyId = " + copyId +
        "}";
    }
}
