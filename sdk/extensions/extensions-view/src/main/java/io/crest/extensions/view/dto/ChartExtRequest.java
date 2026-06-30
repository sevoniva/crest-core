package io.crest.extensions.view.dto;


import lombok.Data;

import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class ChartExtRequest {
    private List<ChartExtFilterDTO> filter;

    private List<ChartExtFilterDTO> linkageFilters;

    private List<ChartExtFilterDTO> outerParamsFilters;

    private List<ChartExtFilterDTO> webParamsFilters;

    private List<ChartDrillRequest> drill;

    private String queryFrom;

    private String resultMode;

    private Integer resultCount;

    private boolean cache = true;

    private Long user = null;

    // private PermissionProxy proxy;

    private Long goPage;

    private Long pageSize;

    private Boolean excelExportFlag = false;

}
