package io.crest.template.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 可视化模板扩展数据实体。
 */
@TableName("core_template_view_data")
public class VisualizationTemplateExtendData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模板扩展数据主键
     */
    private Long id;

    /**
     * 关联的仪表板 ID
     */
    private Long dvId;

    /**
     * 关联的图表 ID
     */
    private Long viewId;

    /**
     * 图表扩展详情数据
     */
    private String viewDetails;

    /**
     * 复制来源类型或标识
     */
    private String copyFrom;

    /**
     * 复制来源记录 ID
     */
    private String copyId;

    /**
     * 获取模板扩展数据主键
     *
     * @return 模板扩展数据主键
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置模板扩展数据主键
     *
     * @param id 模板扩展数据主键
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取关联的仪表板 ID
     *
     * @return 关联的仪表板 ID
     */
    public Long getDvId() {
        return dvId;
    }

    /**
     * 设置关联的仪表板 ID
     *
     * @param dvId 关联的仪表板 ID
     */
    public void setDvId(Long dvId) {
        this.dvId = dvId;
    }

    /**
     * 获取关联的图表 ID
     *
     * @return 关联的图表 ID
     */
    public Long getViewId() {
        return viewId;
    }

    /**
     * 设置关联的图表 ID
     *
     * @param viewId 关联的图表 ID
     */
    public void setViewId(Long viewId) {
        this.viewId = viewId;
    }

    /**
     * 获取图表扩展详情数据
     *
     * @return 图表扩展详情数据
     */
    public String getViewDetails() {
        return viewDetails;
    }

    /**
     * 设置图表扩展详情数据
     *
     * @param viewDetails 图表扩展详情数据
     */
    public void setViewDetails(String viewDetails) {
        this.viewDetails = viewDetails;
    }

    /**
     * 获取复制来源类型或标识
     *
     * @return 复制来源类型或标识
     */
    public String getCopyFrom() {
        return copyFrom;
    }

    /**
     * 设置复制来源类型或标识
     *
     * @param copyFrom 复制来源类型或标识
     */
    public void setCopyFrom(String copyFrom) {
        this.copyFrom = copyFrom;
    }

    /**
     * 获取复制来源记录 ID
     *
     * @return 复制来源记录 ID
     */
    public String getCopyId() {
        return copyId;
    }

    /**
     * 设置复制来源记录 ID
     *
     * @param copyId 复制来源记录 ID
     */
    public void setCopyId(String copyId) {
        this.copyId = copyId;
    }

    /**
     * 返回模板扩展数据的调试字符串
     *
     * @return 模板扩展数据的字符串表示
     */
    @Override
    public String toString() {
        return "VisualizationTemplateExtendData{" +
        "id = " + id +
        ", dvId = " + dvId +
        ", viewId = " + viewId +
        ", viewDetails = " + viewDetails +
        ", copyFrom = " + copyFrom +
        ", copyId = " + copyId +
        "}";
    }
}
