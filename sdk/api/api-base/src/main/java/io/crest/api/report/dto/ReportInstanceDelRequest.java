package io.crest.api.report.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
// 定义接口请求或返回数据的传输结构
public class ReportInstanceDelRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 3972312402268432597L;

    private Long instanceId;

    private Integer timeFlag;

    private Long taskId;
}
