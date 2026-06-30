package io.crest.visualization.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 外部参数明细实体。
 */
@TableName("core_visualization_parameter_item")
public class VisualizationOuterParamsInfo implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 外部参数明细主键
     */
    @TableId("params_info_id")
    private String paramsInfoId;

    /**
     * 外部参数主配置编号
     */
    private String paramsId;

    /**
     * 外部参数名称
     */
    private String paramName;

    /**
     * 当前参数是否启用
     */
    private Boolean checked;

    /**
     * 复制来源资源编号
     */
    private String copyFrom;

    /**
     * 复制来源记录编号
     */
    private String copyId;

    /**
     * 当前参数是否必填
     */
    private Boolean required;

    /**
     * 参数默认值配置
     */
    private String defaultValue;

    /**
     * 是否启用默认值
     */
    private Boolean enabledDefault;

    /**
     * 获取外部参数明细主键
     */
    public String getParamsInfoId() {
        return paramsInfoId;
    }

    /**
     * 设置外部参数明细主键
     */
    public void setParamsInfoId(String paramsInfoId) {
        this.paramsInfoId = paramsInfoId;
    }

    /**
     * 获取外部参数主配置编号
     */
    public String getParamsId() {
        return paramsId;
    }

    /**
     * 设置外部参数主配置编号
     */
    public void setParamsId(String paramsId) {
        this.paramsId = paramsId;
    }

    /**
     * 获取外部参数名称
     */
    public String getParamName() {
        return paramName;
    }

    /**
     * 设置外部参数名称
     */
    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    /**
     * 获取当前参数启用状态
     */
    public Boolean getChecked() {
        return checked;
    }

    /**
     * 设置当前参数启用状态
     */
    public void setChecked(Boolean checked) {
        this.checked = checked;
    }

    /**
     * 获取复制来源资源编号
     */
    public String getCopyFrom() {
        return copyFrom;
    }

    /**
     * 设置复制来源资源编号
     */
    public void setCopyFrom(String copyFrom) {
        this.copyFrom = copyFrom;
    }

    /**
     * 获取复制来源记录编号
     */
    public String getCopyId() {
        return copyId;
    }

    /**
     * 设置复制来源记录编号
     */
    public void setCopyId(String copyId) {
        this.copyId = copyId;
    }

    /**
     * 获取当前参数是否必填
     */
    public Boolean getRequired() {
        return required;
    }

    /**
     * 设置当前参数是否必填
     */
    public void setRequired(Boolean required) {
        this.required = required;
    }

    /**
     * 获取参数默认值配置
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * 设置参数默认值配置
     */
    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * 获取是否启用默认值
     */
    public Boolean getEnabledDefault() {
        return enabledDefault;
    }

    /**
     * 设置是否启用默认值
     */
    public void setEnabledDefault(Boolean enabledDefault) {
        this.enabledDefault = enabledDefault;
    }

    /**
     * 返回外部参数明细实体的调试字符串
     */
    @Override
    public String toString() {
        return "VisualizationOuterParamsInfo{" +
        "paramsInfoId = " + paramsInfoId +
        ", paramsId = " + paramsId +
        ", paramName = " + paramName +
        ", checked = " + checked +
        ", copyFrom = " + copyFrom +
        ", copyId = " + copyId +
        ", required = " + required +
        ", defaultValue = " + defaultValue +
        ", enabledDefault = " + enabledDefault +
        "}";
    }
}
