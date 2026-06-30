package io.crest.api.visualization.dto;

import java.util.List;

/**
 * 联动关系信息，记录源字段和目标字段集合
 */
public class LinkageInfoDTO {

    /**
     * 发起联动的源字段信息
     */
    private String sourceInfo;

    /**
     * 接收联动的目标字段信息列表
     */
    private List<String> targetInfoList;

    /**
     * 获取发起联动的源字段信息
     */
    public String getSourceInfo() {
        return sourceInfo;
    }

    /**
     * 设置发起联动的源字段信息
     */
    public void setSourceInfo(String sourceInfo) {
        this.sourceInfo = sourceInfo;
    }

    /**
     * 获取接收联动的目标字段信息列表
     */
    public List<String> getTargetInfoList() {
        return targetInfoList;
    }

    /**
     * 设置接收联动的目标字段信息列表
     */
    public void setTargetInfoList(List<String> targetInfoList) {
        this.targetInfoList = targetInfoList;
    }
}
