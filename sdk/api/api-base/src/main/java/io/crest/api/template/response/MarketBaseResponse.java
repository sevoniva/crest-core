package io.crest.api.template.response;

import io.crest.api.template.dto.TemplateMarketDTO;
import io.crest.api.template.vo.MarketMetaDataVO;
import lombok.Data;

import java.util.List;

/**
 * 模板市场列表响应。
 */
@Data
public class MarketBaseResponse {
    private String baseUrl;

    List<MarketMetaDataVO> categories;

    private List<TemplateMarketDTO> contents;

    public MarketBaseResponse() {
    }

    public MarketBaseResponse(String baseUrl, List<MarketMetaDataVO> categories, List<TemplateMarketDTO> contents) {
        this.baseUrl = baseUrl;
        this.categories = categories;
        this.contents = contents;
    }
}
