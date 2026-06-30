package io.crest.visualization.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 组件跳转规则快照实体。
 */
@TableName("core_visualization_jump_action_snapshot")
public class SnapshotVisualizationLinkJumpInfo implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 跳转规则快照明细主键
     */
    private Long id;

    /**
     * 跳转规则主配置编号
     */
    private Long linkJumpId;

    /**
     * 跳转关联类型，区分内部资源和外部链接
     */
    private String linkType;

    /**
     * 页面打开方式，区分新窗口和当前窗口
     */
    private String jumpType;

    /**
     * 目标大屏或仪表板编号
     */
    private Long targetDvId;

    /**
     * 触发跳转的源字段编号
     */
    private Long sourceFieldId;

    /**
     * 外部链接跳转内容
     */
    private String content;

    /**
     * 当前跳转规则是否启用
     */
    private Boolean checked;

    /**
     * 是否附加点击参数
     */
    private Boolean attachParams;

    /**
     * 复制来源资源编号
     */
    private Long copyFrom;

    /**
     * 复制来源记录编号
     */
    private Long copyId;

    /**
     * 弹窗窗口尺寸
     */
    private String windowSize;

    /**
     * 获取跳转规则快照明细主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置跳转规则快照明细主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取跳转规则主配置编号
     */
    public Long getLinkJumpId() {
        return linkJumpId;
    }

    /**
     * 设置跳转规则主配置编号
     */
    public void setLinkJumpId(Long linkJumpId) {
        this.linkJumpId = linkJumpId;
    }

    /**
     * 获取跳转关联类型
     */
    public String getLinkType() {
        return linkType;
    }

    /**
     * 设置跳转关联类型
     */
    public void setLinkType(String linkType) {
        this.linkType = linkType;
    }

    /**
     * 获取页面打开方式
     */
    public String getJumpType() {
        return jumpType;
    }

    /**
     * 设置页面打开方式
     */
    public void setJumpType(String jumpType) {
        this.jumpType = jumpType;
    }

    /**
     * 获取目标大屏或仪表板编号
     */
    public Long getTargetDvId() {
        return targetDvId;
    }

    /**
     * 设置目标大屏或仪表板编号
     */
    public void setTargetDvId(Long targetDvId) {
        this.targetDvId = targetDvId;
    }

    /**
     * 获取触发跳转的源字段编号
     */
    public Long getSourceFieldId() {
        return sourceFieldId;
    }

    /**
     * 设置触发跳转的源字段编号
     */
    public void setSourceFieldId(Long sourceFieldId) {
        this.sourceFieldId = sourceFieldId;
    }

    /**
     * 获取外部链接跳转内容
     */
    public String getContent() {
        return content;
    }

    /**
     * 设置外部链接跳转内容
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * 获取当前跳转规则启用状态
     */
    public Boolean getChecked() {
        return checked;
    }

    /**
     * 设置当前跳转规则启用状态
     */
    public void setChecked(Boolean checked) {
        this.checked = checked;
    }

    /**
     * 获取是否附加点击参数
     */
    public Boolean getAttachParams() {
        return attachParams;
    }

    /**
     * 设置是否附加点击参数
     */
    public void setAttachParams(Boolean attachParams) {
        this.attachParams = attachParams;
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
     * 获取弹窗窗口尺寸
     */
    public String getWindowSize() {
        return windowSize;
    }

    /**
     * 设置弹窗窗口尺寸
     */
    public void setWindowSize(String windowSize) {
        this.windowSize = windowSize;
    }

    /**
     * 返回跳转规则快照明细实体的调试字符串
     */
    @Override
    public String toString() {
        return "SnapshotVisualizationLinkJumpInfo{" +
        "id = " + id +
        ", linkJumpId = " + linkJumpId +
        ", linkType = " + linkType +
        ", jumpType = " + jumpType +
        ", targetDvId = " + targetDvId +
        ", sourceFieldId = " + sourceFieldId +
        ", content = " + content +
        ", checked = " + checked +
        ", attachParams = " + attachParams +
        ", copyFrom = " + copyFrom +
        ", copyId = " + copyId +
        ", windowSize = " + windowSize +
        "}";
    }
}
