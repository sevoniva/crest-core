package io.crest.api.template;

import io.crest.api.template.response.MarketBaseResponse;
import io.crest.api.template.response.MarketPreviewBaseResponse;
import io.crest.api.template.vo.MarketMetaDataVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 模板市场查询接口。
 */
@Tag(name = "模版中心:基础")
public interface TemplateMarketApi {

    @GetMapping("/search")
    @Operation(summary = "查询")
    MarketBaseResponse searchTemplate();
    @GetMapping("/recommendations/search")
    @Operation(summary = "查询基础信息")
    MarketBaseResponse searchTemplateRecommend();

    @GetMapping("/previews/search")
    @Operation(summary = "预览")
    MarketPreviewBaseResponse searchTemplatePreview();

    @GetMapping("/categories")
    @Operation(summary = "分类")
    List<String> categories();

    @GetMapping("/categories/object")
    @Operation(summary = "分类明细")
    List<MarketMetaDataVO> categoriesObject() ;

}
