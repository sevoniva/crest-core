package io.crest.api.visualization.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
// 定义接口请求或返回数据的传输结构
public class VisualizationStoreRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 166667337997303748L;

    private Long id;

    private String type;
}
