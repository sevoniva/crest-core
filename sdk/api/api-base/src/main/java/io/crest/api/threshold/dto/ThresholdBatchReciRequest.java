package io.crest.api.threshold.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
// 定义接口请求或返回数据的传输结构
public class ThresholdBatchReciRequest extends BaseReciDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = -2831988863396898760L;

    private List<Long> idList;

}
