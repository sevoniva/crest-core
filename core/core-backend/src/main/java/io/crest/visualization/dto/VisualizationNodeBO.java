package io.crest.visualization.dto;

import io.crest.model.TreeBaseModel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;

@Data
@AllArgsConstructor
@NoArgsConstructor
// 定义接口请求或返回数据的传输结构
public class VisualizationNodeBO implements TreeBaseModel {
    @Serial
    private static final long serialVersionUID = -4998292096597683628L;

    private Long id;
    private String name;
    private Boolean leaf;
    private Integer weight = 3;
    private Long pid;
    private Integer extraFlag;
    private Integer extraFlag1;


}
