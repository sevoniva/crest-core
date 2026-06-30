package io.crest.api.visualization.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
// 定义页面展示或接口返回的数据结构
public class VisualizationOuterParamsDsInfoVO {

    private String dsName;

    private String dsId;

    private List targetFieldInfo;

    private Map<String,Boolean> viewCheckedInfo;

}
