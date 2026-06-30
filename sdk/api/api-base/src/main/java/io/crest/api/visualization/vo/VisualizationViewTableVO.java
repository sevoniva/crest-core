package io.crest.api.visualization.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import lombok.Data;

import java.util.List;


/**
 * 可视化视图与数据集表的绑定关系。
 */
@Data
public class VisualizationViewTableVO {

    /**
     * ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String title;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long dvId;

    /**
     * 数据集表ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tableId;

    /**
     * 图表类型
     */
    private String type;

    /**
     * 图表渲染方式
     */
    private String render;


    private List<DatasetTableFieldDTO> tableFields;

}
