package io.crest.api.dataset.union;

import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import lombok.Data;

import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class UnionItemDTO implements Serializable {
    private DatasetTableFieldDTO parentField;
    private DatasetTableFieldDTO currentField;
}
