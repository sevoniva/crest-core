package io.crest.api.dataset.union;

import lombok.Data;

import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class DatasetTableInfoDTO implements Serializable {
    private String table;
    private String sql;
}
