package io.crest.api.dataset.union;

import io.crest.extensions.datasource.dto.DatasetTableDTO;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class UnionDTO implements Serializable {
    private DatasetTableDTO currentDs;
    private List<Long> currentDsField;
    private List<DatasetTableFieldDTO> currentDsFields;
    private List<UnionDTO> childrenDs;
    private UnionParamDTO unionToParent;
    private int allChildCount;
}
