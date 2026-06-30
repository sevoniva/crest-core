package io.crest.api.free.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class FreeBatchDelRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -296464652281011661L;

    private List<Long> idList;

    private int rtId;
}
