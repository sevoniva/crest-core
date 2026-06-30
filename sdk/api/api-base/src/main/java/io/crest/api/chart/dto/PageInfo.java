package io.crest.api.chart.dto;

import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class PageInfo {
    private Long goPage;
    private Long pageSize;
    private String dsVersion;
}
