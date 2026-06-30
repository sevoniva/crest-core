package io.crest.api.template.request;

import io.crest.api.template.vo.VisualizationTemplateVO;
import lombok.Data;

import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class TemplateManageBatchRequest {

    private String optType;

    private List<String> templateIds;

    private List<String> categories;

}
