package io.crest.visualization.dao.auto.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

/**
 * 外部参数目标字段快照实体。
 */
@TableName("core_visualization_parameter_target_snapshot")
public class SnapshotVisualizationOuterParamsTargetViewInfo implements Serializable {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 外部参数目标快照配置主键
     */
    @TableId("target_id")
    private String targetId;

    /**
     * 外部参数明细编号
     */
    private String paramsInfoId;

    /**
     * 目标视图或过滤项编号
     */
    private String targetViewId;

    /**
     * 目标字段编号
     */
    private String targetFieldId;

    /**
     * 复制来源资源编号
     */
    private String copyFrom;

    /**
     * 复制来源记录编号
     */
    private String copyId;

    /**
     * 目标数据集或过滤组件编号
     */
    private String targetDsId;

    /**
     * 参数匹配模式
     */
    private String matchMode;

    /**
     * 获取参数匹配模式
     */
    public String getMatchMode() {
        return matchMode;
    }

    /**
     * 设置参数匹配模式
     */
    public void setMatchMode(String matchMode) {
        this.matchMode = matchMode;
    }

    /**
     * 获取外部参数目标快照配置主键
     */
    public String getTargetId() {
        return targetId;
    }

    /**
     * 设置外部参数目标快照配置主键
     */
    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    /**
     * 获取外部参数明细编号
     */
    public String getParamsInfoId() {
        return paramsInfoId;
    }

    /**
     * 设置外部参数明细编号
     */
    public void setParamsInfoId(String paramsInfoId) {
        this.paramsInfoId = paramsInfoId;
    }

    /**
     * 获取目标视图或过滤项编号
     */
    public String getTargetViewId() {
        return targetViewId;
    }

    /**
     * 设置目标视图或过滤项编号
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
     * 获取目标数据集或过滤组件编号
     */
    public String getTargetDsId() {
        return targetDsId;
    }

    /**
     * 设置目标数据集或过滤组件编号
     */
    public void setTargetDsId(String targetDsId) {
        this.targetDsId = targetDsId;
    }

    /**
     * 返回外部参数目标字段快照实体的调试字符串
     */
    @Override
    public String toString() {
        return "SnapshotVisualizationOuterParamsTargetViewInfo{" +
        "targetId = " + targetId +
        ", paramsInfoId = " + paramsInfoId +
        ", targetViewId = " + targetViewId +
        ", targetFieldId = " + targetFieldId +
        ", copyFrom = " + copyFrom +
        ", copyId = " + copyId +
        ", targetDsId = " + targetDsId +
        "}";
    }
}
