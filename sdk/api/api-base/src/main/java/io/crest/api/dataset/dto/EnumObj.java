package io.crest.api.dataset.dto;

import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class EnumObj {
    private DatasetTableFieldDTO field;
    private DatasetGroupInfoDTO dataset;
}
