package io.crest.api.template.dto;


import io.crest.api.template.vo.VisualizationTemplateVO;
import lombok.Data;

import java.util.List;


@Data
// 定义接口请求或返回数据的传输结构
public class TemplateManageDTO extends VisualizationTemplateVO {

    private String label;

    private Integer childrenCount;

    private String categoryName;

    private Long recentUseTime;

    private Boolean checked = false;

    private List<TemplateManageDTO> children;

    private List<String> categories;

    private List<String> categoryNames;


}
