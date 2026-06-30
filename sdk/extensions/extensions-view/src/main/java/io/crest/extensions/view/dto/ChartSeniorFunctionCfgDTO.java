package io.crest.extensions.view.dto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class ChartSeniorFunctionCfgDTO {
    private String emptyDataStrategy;
    private String emptyDataCustomValue;
    private List<String> emptyDataFieldCtrl = new ArrayList<>();
}
