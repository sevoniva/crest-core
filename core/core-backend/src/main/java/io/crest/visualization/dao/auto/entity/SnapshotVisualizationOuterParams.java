package io.crest.visualization.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 外部参数主配置快照实体。
 */
@TableName("core_visualization_parameter_snapshot")
public class SnapshotVisualizationOuterParams implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 外部参数配置主键
     */
    @TableId("params_id")
    private String paramsId;

    /**
     * 可视化资源 ID
     */
    private String visualizationId;

    /**
     * 是否启用外部参数配置
     */
    private Boolean checked;

    /**
     * 外部参数配置备注
     */
    private String remark;

    /**
     * 复制来源类型或标识
     */
    private String copyFrom;

    /**
     * 复制来源记录 ID
     */
    private String copyId;

    /**
     * 获取外部参数配置主键
     *
     * @return 外部参数配置主键
     */
    public String getParamsId() {
        return paramsId;
    }

    /**
     * 设置外部参数配置主键
     *
     * @param paramsId 外部参数配置主键
     */
    public void setParamsId(String paramsId) {
        this.paramsId = paramsId;
    }

    /**
     * 获取可视化资源 ID
     *
     * @return 可视化资源 ID
     */
    public String getVisualizationId() {
        return visualizationId;
    }

    /**
     * 设置可视化资源 ID
     *
     * @param visualizationId 可视化资源 ID
     */
    public void setVisualizationId(String visualizationId) {
        this.visualizationId = visualizationId;
    }

    /**
     * 获取是否启用外部参数配置
     *
     * @return 是否启用外部参数配置
     */
    public Boolean getChecked() {
        return checked;
    }

    /**
     * 设置是否启用外部参数配置
     *
     * @param checked 是否启用外部参数配置
     */
    public void setChecked(Boolean checked) {
        this.checked = checked;
    }

    /**
     * 获取外部参数配置备注
     *
     * @return 外部参数配置备注
     */
    public String getRemark() {
        return remark;
    }

    /**
     * 设置外部参数配置备注
     *
     * @param remark 外部参数配置备注
     */
    public void setRemark(String remark) {
        this.remark = remark;
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
     * 返回外部参数配置快照的调试字符串
     *
     * @return 外部参数配置快照的字符串表示
     */
    @Override
    public String toString() {
        return "SnapshotVisualizationOuterParams{" +
        "paramsId = " + paramsId +
        ", visualizationId = " + visualizationId +
        ", checked = " + checked +
        ", remark = " + remark +
        ", copyFrom = " + copyFrom +
        ", copyId = " + copyId +
        "}";
    }
}
