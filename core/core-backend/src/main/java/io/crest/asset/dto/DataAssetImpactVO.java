package io.crest.asset.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
// 定义接口请求或返回数据的传输结构
public class DataAssetImpactVO implements Serializable {
    @Serial
    private static final long serialVersionUID = -8767593887447127604L;

    private String assetType;
    private String assetId;
    private Map<String, Long> summary = new LinkedHashMap<>();
    private List<DataAssetImpactItemVO> items = new ArrayList<>();
}
