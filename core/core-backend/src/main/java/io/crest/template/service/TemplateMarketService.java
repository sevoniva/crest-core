package io.crest.template.service;

import io.crest.api.template.TemplateMarketApi;
import io.crest.api.template.response.MarketBaseResponse;
import io.crest.api.template.response.MarketPreviewBaseResponse;
import io.crest.api.template.vo.MarketMetaDataVO;
import io.crest.template.manage.TemplateCenterManage;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对外提供模板市场查询接口，封装模板中心的数据读取能力。
 */
@RestController
@RequestMapping("/template-market")
@ConditionalOnProperty(prefix = "crest.feature.template-market", name = "enabled", havingValue = "true")
public class TemplateMarketService implements TemplateMarketApi {

    /**
     * 模板中心管理器，承接模板市场的聚合查询逻辑
     */
    @Resource
    private TemplateCenterManage templateCenterManage;

    /**
     * 查询模板市场完整列表
     */
    @Override
    public MarketBaseResponse searchTemplate() {
        return templateCenterManage.searchTemplate();
    }

    /**
     * 查询模板市场推荐列表
     */
    @Override
    public MarketBaseResponse searchTemplateRecommend() {
        return templateCenterManage.searchTemplateRecommend();
    }

    /**
     * 查询模板市场预览分组数据
     */
    @Override
    public MarketPreviewBaseResponse searchTemplatePreview() {
        return templateCenterManage.searchTemplatePreview();
    }

    /**
     * 查询模板分类名称列表
     */
    @Override
    public List<String> categories() {
        return templateCenterManage.getCategories();
    }

    /**
     * 查询模板分类对象列表
     */
    @Override
    public List<MarketMetaDataVO> categoriesObject() {
        return templateCenterManage.getCategoriesObject();
    }
}
