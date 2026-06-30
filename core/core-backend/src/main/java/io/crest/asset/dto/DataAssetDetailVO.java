package io.crest.asset.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class DataAssetDetailVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 231231728336903503L;

    private DataAssetVO asset;
    private List<DataAssetImpactItemVO> upstream = new ArrayList<>();
    private List<DataAssetImpactItemVO> downstream = new ArrayList<>();
    private DataAssetImpactVO impact;
}
