package io.crest.api.free.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
// 定义接口请求或返回数据的传输结构
public class FreeBatchSyncRequest extends FreeSyncRequest {
    @Serial
    private static final long serialVersionUID = 7068735824164921251L;

    private List<Long> idList;

    private int rtId;
}
