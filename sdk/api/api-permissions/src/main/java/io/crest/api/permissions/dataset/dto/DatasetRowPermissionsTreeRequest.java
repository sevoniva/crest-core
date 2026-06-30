package io.crest.api.permissions.dataset.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
// 定义接口请求或返回数据的传输结构
public class DatasetRowPermissionsTreeRequest extends DataSetRowPermissionsTreeDTO {
    public String orderBy;
}
