package io.crest.api.threshold.dto;

import io.crest.constant.CommonConstants;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class ThresholdGridRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -2729126999572515149L;

    private String keyword;

    private String resourceTable = CommonConstants.RESOURCE_TABLE.CORE;

    private List<String> resourceTypeList;

    private List<Integer> statusList;

    private List<Integer> enableList;

    private List<Long> timeList;

    private Long chartId;

}
