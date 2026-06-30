package io.crest.api.permissions.dataset.bo;

import io.crest.api.permissions.dataset.vo.ColPermissionInfo;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
// 定义模块接口契约和数据传输结构
public class ColPermissionBo implements Serializable {

    private boolean enable;

    private List<ColPermissionInfo> columns;
}
