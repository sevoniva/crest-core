package io.crest.visualization.dao.ext.po;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;


@Data
public class VisualizationNodePO implements Serializable {

    private Long id;
    private String name;
    private Long pid;
    private String nodeType;
    private String type;
    private String createBy;
    private Long orgId;
    @Schema(description = "额外标识")
    private int extraFlag;
    @Schema(description = "额外标识1")
    private int extraFlag1;

}
