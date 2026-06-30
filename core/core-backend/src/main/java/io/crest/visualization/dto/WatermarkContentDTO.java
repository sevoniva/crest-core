package io.crest.visualization.dto;

import lombok.Data;

@Data
// 定义接口请求或返回数据的传输结构
public class WatermarkContentDTO {

    private Boolean enable;

    private Boolean excelEnable = false;

    private Boolean enablePanelCustom;

    private String type;

    private String content;

    private String watermark_color;

    private Integer watermark_x_space;

    private Integer watermark_y_space;

    private Integer watermark_fontsize;

}
