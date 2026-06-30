package io.crest.extensions.view.dto;

import lombok.Data;

import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class ChartFieldCompareCustomDTO {
    private String field;
    private String calcType = "0";
    private String timeType = "0";
    private String currentTime;
    private String compareTime;
    private List<String> currentTimeRange;
    private List<String> compareTimeRange;

}
