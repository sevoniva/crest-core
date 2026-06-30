package io.crest.api.template.response;

import io.crest.api.template.vo.TemplateCategoryVO;
import lombok.Data;

import java.util.List;

@Data
// 定义接口请求或返回数据的传输结构
public class MarketCategoryBaseResponse {
    private List<TemplateCategoryVO> data;
}
