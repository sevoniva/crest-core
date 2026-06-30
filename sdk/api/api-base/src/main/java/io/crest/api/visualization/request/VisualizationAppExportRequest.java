package io.crest.api.visualization.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class VisualizationAppExportRequest {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dvId;

    @JsonSerialize(using = ToStringSerializer.class)
    private List<Long> viewIds;

    @JsonSerialize(using = ToStringSerializer.class)
    private List<Long> dsIds;
}
