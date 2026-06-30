package io.crest.api.visualization.dto;

import io.crest.api.visualization.vo.VisualizationOuterParamsVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Data
// 定义接口请求或返回数据的传输结构
public class VisualizationOuterParamsDTO extends VisualizationOuterParamsVO {

    private List<String> targetInfoList;

    private List<VisualizationOuterParamsInfoDTO> outerParamsInfoArray = new ArrayList<>();

    private Map<String,VisualizationOuterParamsInfoDTO> mapOuterParamsInfoArray = new HashMap<>();

}
