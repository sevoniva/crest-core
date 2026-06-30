package io.crest.template.manage;

import io.crest.api.template.dto.TemplateManageDTO;
import io.crest.api.template.dto.TemplateManageFileDTO;
import io.crest.api.template.dto.TemplateMarketDTO;
import io.crest.api.template.dto.TemplateMarketPreviewInfoDTO;
import io.crest.api.template.response.*;
import io.crest.api.template.vo.MarketApplicationMetaDataVO;
import io.crest.api.template.vo.MarketApplicationSpecVO;
import io.crest.api.template.vo.MarketLatestReleaseVO;
import io.crest.api.template.vo.MarketMetaDataVO;
import io.crest.constant.CommonConstants;
import io.crest.exception.CrestException;
import io.crest.i18n.Translator;
import io.crest.operation.manage.CoreOptRecentManage;
import io.crest.system.manage.SysParameterManage;
import io.crest.template.dao.ext.ExtVisualizationTemplateMapper;
import io.crest.utils.HttpClientConfig;
import io.crest.utils.HttpClientUtil;
import io.crest.utils.JsonUtil;
import io.crest.utils.LogUtil;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模板中心管理服务，负责模板市场查询、本地模板管理和分类聚合
 */
@Service
public class TemplateCenterManage {
    private final static String POSTS_API_V2 = "/apis/api.store.halo.run/v1alpha1/applications?keyword=&priceMode=&sort=latestReleaseTimestamp%2Cdesc&type=THEME&platformVersion=V2&templateType=&label=&page=1&size=2000";
    private final static String TEMPLATE_META_DATA_URL = "/upload/meta_data.json";
    private final static String TEMPLATE_BASE_INFO_URL = "/apis/api.store.halo.run/v1alpha1/applications/";
    @Resource
    private SysParameterManage sysParameterManage;

    @Resource
    private CoreOptRecentManage coreOptRecentManage;

    @Resource
    private ExtVisualizationTemplateMapper templateManageMapper;

    /**
     * 根据模板文件地址从模板市场拉取模板内容
     */
    public TemplateManageFileDTO getTemplateFromMarket(String templateUrl) {
        String sufUrl = templateBaseUrl();
        if (StringUtils.isNotEmpty(sufUrl) && StringUtils.isNotEmpty(templateUrl)) {
            String templateName = templateUrl.substring(templateUrl.lastIndexOf("/") + 1, templateUrl.length());
            templateUrl = templateUrl.replace(templateName, URLEncoder.encode(templateName, StandardCharsets.UTF_8).replace("+", "%20"));
            String templateInfo = HttpClientUtil.get(sufUrl + templateUrl, null);
            return JsonUtil.parseObject(templateInfo, TemplateManageFileDTO.class);
        } else {
            return null;
        }
    }

    /**
     * 根据模板应用名称从新版模板市场拉取模板内容
     */
    public TemplateManageFileDTO getTemplateFromMarketV2(String templateName) {
        String sufUrl = templateBaseUrl();
        if (StringUtils.isNotEmpty(sufUrl) && StringUtils.isNotEmpty(templateName)) {
            String templateBaseInfo = HttpClientUtil.get(sufUrl + TEMPLATE_BASE_INFO_URL + templateName, null);
            MarketTemplateV2ItemResult baseItemInfo = JsonUtil.parseObject(templateBaseInfo, MarketTemplateV2ItemResult.class);
            String templateUrl = "";
            if (baseItemInfo.getLatestRelease() != null) {
                templateUrl = sufUrl + "/store/apps/" + templateName +
                        "/releases/download/" + baseItemInfo.getLatestRelease().getRelease().getMetadata().getName()
                        + "/assets/" + baseItemInfo.getLatestRelease().getAssets().get(0).getMetadata().getName();
            } else {
                templateUrl = sufUrl + baseItemInfo.getApplication().getSpec().getLinks().get(0).getUrl();
            }

            String templateInfo = HttpClientUtil.get(templateUrl, null);
            return JsonUtil.parseObject(templateInfo, TemplateManageFileDTO.class);
        } else {
            return null;
        }
    }

    /**
     * 调用模板市场接口并附加访问凭据
     */
    public String marketGet(String url, String accessKey) {
        HttpClientConfig config = new HttpClientConfig();
        config.addHeader("API-Authorization", accessKey);
        config.setConnectTimeout(5000);
        config.setSocketTimeout(10000);
        config.setConnectionRequestTimeout(5000);
        return HttpClientUtil.
                get(url, config);
    }

    /**
     * 查询新版模板市场的模板应用列表
     */
    private MarketTemplateV2BaseResponse templateQuery(Map<String, String> templateParams) {
        String templateUrl = templateBaseUrl(templateParams);
        if (StringUtils.isBlank(templateUrl)) {
            return null;
        }
        try {
            String result = marketGet(templateUrl + POSTS_API_V2, null);
            MarketTemplateV2BaseResponse postsResult = JsonUtil.parseObject(result, MarketTemplateV2BaseResponse.class);
            return postsResult;
        } catch (Exception e) {
            LogUtil.error(e);
            return null;
        }
    }

    /**
     * 合并模板市场和本地模板管理数据
     */
    public MarketBaseResponse searchTemplate() {
        try {
            Map<String, String> templateParams = sysParameterManage.groupVal("template.");
            return baseResponseV2Trans(templateQuery(templateParams), searchTemplateFromManage(), templateBaseUrl(templateParams));
        } catch (Exception e) {
            LogUtil.error(e);
            io.crest.utils.LogUtil.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 查询本地模板管理中的模板并补齐分类名称
     */
    private List<TemplateMarketDTO> searchTemplateFromManage() {
        try {
            List<TemplateManageDTO> manageResult = templateManageMapper.findBaseTemplateList();
            List<TemplateManageDTO> categories = templateManageMapper.findCategories(null);
            Map<String, String> categoryNameById = categories.stream()
                    .collect(Collectors.toMap(TemplateManageDTO::getId, TemplateManageDTO::getName));
            return baseManage2MarketTrans(manageResult, categoryNameById);
        } catch (Exception e) {
            CrestException.throwException(e);
        }
        return null;
    }

    /**
     * 将本地模板管理 DTO 转换为模板市场展示 DTO
     */
    private List<TemplateMarketDTO> baseManage2MarketTrans(List<TemplateManageDTO> manageResult, Map<String, String> categoryNameById) {
        List<TemplateMarketDTO> result = new ArrayList<>();
        manageResult.stream().forEach(templateManageDTO -> {
            templateManageDTO.setCategoryName(categoryNameById.get(templateManageDTO.getPid()));
            List<String> categories = templateManageDTO.getCategories();
            if (!CollectionUtils.isEmpty(categories)) {
                List<String> categoryNames = categories.stream().map(categoryNameById::get).collect(Collectors.toList());
                templateManageDTO.setCategoryNames(categoryNames);
                result.add(new TemplateMarketDTO(templateManageDTO));
            }
        });
        return result;
    }


    /**
     * 查询推荐模板并用本地模板补足推荐数量
     */
    public MarketBaseResponse searchTemplateRecommend() {
        MarketTemplateV2BaseResponse v2BaseResponse = null;
        Map<String, String> templateParams = sysParameterManage.groupVal("template.");
        // 模版市场推荐
        try {
            v2BaseResponse = templateQuery(templateParams);
        } catch (Exception e) {
            CrestException.throwException(e);
        }
        // 模版管理使用次数推荐
        List<TemplateMarketDTO> manage = searchTemplateFromManage();
        return baseResponseV2TransRecommend(v2BaseResponse, manage, templateBaseUrl(templateParams));
    }

    /**
     * 查询模板预览页所需的分类和模板分组数据
     */
    public MarketPreviewBaseResponse searchTemplatePreview() {
        try {
            MarketBaseResponse baseContentRsp = searchTemplate();
            List<MarketMetaDataVO> categories = baseContentRsp.getCategories().stream().filter(category -> !Translator.get("i18n_template_recent").equals(category.getLabel())).toList();
            List<TemplateMarketDTO> contents = baseContentRsp.getContents();
            List<TemplateMarketPreviewInfoDTO> previewContents = new ArrayList<>();
            categories.forEach(category -> {
                if (Translator.get("i18n_template_recommend").equals(category.getLabel())) {
                    previewContents.add(new TemplateMarketPreviewInfoDTO(category, contents.stream().filter(template -> "Y".equals(template.getSuggest())).collect(Collectors.toList())));
                } else {
                    previewContents.add(new TemplateMarketPreviewInfoDTO(category, contents.stream().filter(template -> checkCategoryMatch(template, category.getLabel())).collect(Collectors.toList())));
                }
            });
            return new MarketPreviewBaseResponse(baseContentRsp.getBaseUrl(), categories.stream().map(MarketMetaDataVO::getLabel)
                    .collect(Collectors.toList()), previewContents);
        } catch (Exception e) {
            LogUtil.error(e);
        }
        return null;
    }

    /**
     * 判断模板是否归属于指定分类名称
     */
    private Boolean checkCategoryMatch(TemplateMarketDTO template, String categoryNameMatch) {
        try {
            return template.getCategories().stream()
                    .anyMatch(category -> categoryNameMatch.equals(category.getName()));
        } catch (Exception e) {
            io.crest.utils.LogUtil.error(e.getMessage(), e);
            return false;
        }
    }

    /**
     * 将新版市场推荐数据与本地推荐模板合并为统一响应
     */
    private MarketBaseResponse baseResponseV2TransRecommend(MarketTemplateV2BaseResponse v2BaseResponse, List<TemplateMarketDTO> templateManages, String url) {
        Map<String, Long> useTime = coreOptRecentManage.findTemplateRecentUseTime();
        List<MarketMetaDataVO> categoryVO = getCategoriesV2().stream().filter(node -> !"全部".equalsIgnoreCase(node.getLabel())).collect(Collectors.toList());
        Map<String, String> categoriesMap = categoryVO.stream()
                .collect(Collectors.toMap(MarketMetaDataVO::getSlug, MarketMetaDataVO::getLabel));
        List<TemplateMarketDTO> contents = new ArrayList<>();
        if (v2BaseResponse != null) {
            v2BaseResponse.getItems().stream().forEach(marketTemplateV2ItemResult -> {
                MarketApplicationSpecVO spec = marketTemplateV2ItemResult.getApplication().getSpec();
                MarketApplicationMetaDataVO metadata = marketTemplateV2ItemResult.getApplication().getMetadata();
                if ("Y".equalsIgnoreCase(spec.getSuggest())) {
                    contents.add(new TemplateMarketDTO(metadata.getName(), spec.getDisplayName(), spec.getScreenshots().get(0).getUrl(), spec.getLinks().get(0).getUrl(), categoriesMap.get(spec.getLabel()), spec.getTemplateType(), useTime.get(spec.getReadmeName()), "Y", spec.getTemplateClassification()));
                }
            });
        }
        // 最近使用排序
        Collections.sort(contents);
        Long countDataV = contents.stream().filter(item -> "PANEL".equals(item.getTemplateType())).count();
        Long countDashboard = contents.stream().filter(item -> "SCREEN".equals(item.getTemplateType())).count();
        List<TemplateMarketDTO> templateDataV = templateManages.stream().filter(item -> "PANEL".equals(item.getTemplateType())).collect(Collectors.toList());
        List<TemplateMarketDTO> templateDashboard = templateManages.stream().filter(item -> "SCREEN".equals(item.getTemplateType())).collect(Collectors.toList());
        if (countDataV < 10) {
            Long addItemCount = 10 - countDataV;
            Long addIndex = templateDataV.size() < addItemCount ? templateDataV.size() : addItemCount;
            contents.addAll(templateDataV.subList(0, addIndex.intValue()));
        }

        if (countDashboard < 10) {
            Long addItemCount = 10 - countDashboard;
            Long addIndex = templateDashboard.size() < addItemCount ? templateDashboard.size() : addItemCount;
            contents.addAll(templateDashboard.subList(0, addIndex.intValue()));
        }

        return new MarketBaseResponse(url, categoryVO, contents);
    }

    /**
     * 将新版市场列表和本地模板列表转换为统一市场响应
     */
    private MarketBaseResponse baseResponseV2Trans(MarketTemplateV2BaseResponse v2BaseResponse, List<TemplateMarketDTO> contents, String url) {
        Map<String, Long> useTime = coreOptRecentManage.findTemplateRecentUseTime();
        List<MarketMetaDataVO> categoryVO = getCategoriesObject().stream().filter(node -> !"全部".equalsIgnoreCase(node.getLabel())).collect(Collectors.toList());
        Map<String, String> categoriesMap = categoryVO.stream()
                .collect(Collectors.toMap(MarketMetaDataVO::getValue, MarketMetaDataVO::getLabel));
        List<String> activeCategoriesName = new ArrayList<>(Arrays.asList(Translator.get("i18n_template_recent"), Translator.get("i18n_template_recommend")));
        contents.stream().forEach(templateMarketDTO -> {
            Long recentUseTime = useTime.get(templateMarketDTO.getId());
            templateMarketDTO.setRecentUseTime(recentUseTime == null ? 0 : recentUseTime);
            activeCategoriesName.addAll(templateMarketDTO.getCategoryNames());
        });
        if (v2BaseResponse != null) {
            v2BaseResponse.getItems().stream().forEach(marketTemplateV2ItemResult -> {
                MarketApplicationSpecVO spec = marketTemplateV2ItemResult.getApplication().getSpec();
                MarketApplicationMetaDataVO metadata = marketTemplateV2ItemResult.getApplication().getMetadata();
                contents.add(new TemplateMarketDTO(metadata.getName(), spec.getDisplayName(), spec.getScreenshots().get(0).getUrl(), spec.getLinks().get(0).getUrl(), categoriesMap.get(spec.getLabel()), spec.getTemplateType(), useTime.get(spec.getReadmeName()), spec.getSuggest(), spec.getTemplateClassification()));
                if (categoriesMap.get(spec.getLabel()) != null) {
                    activeCategoriesName.add(categoriesMap.get(spec.getLabel()));
                }
            });
        }
        // 最近使用排序
        Collections.sort(contents);
        return new MarketBaseResponse(url, categoryVO.stream().filter(node -> activeCategoriesName.contains(node.getLabel())).collect(Collectors.toList()), contents);
    }


    /**
     * 查询模板分类名称列表
     */
    public List<String> getCategories() {
        return getCategoriesV2().stream().map(MarketMetaDataVO::getLabel)
                .collect(Collectors.toList());
    }

    /**
     * 查询模板分类对象并补充最近使用分类
     */
    public List<MarketMetaDataVO> getCategoriesObject() {
        List<MarketMetaDataVO> result = getCategoriesV2();
        result.add(0, new MarketMetaDataVO("recent", Translator.get("i18n_template_recent"), CommonConstants.TEMPLATE_SOURCE.PUBLIC));
        return result;
    }

    /**
     * 查询新版模板分类的 slug 到名称映射
     */
    public Map<String, String> getCategoriesBaseV2() {
        Map<String, String> categories = getCategoriesV2().stream()
                .collect(Collectors.toMap(MarketMetaDataVO::getSlug, MarketMetaDataVO::getLabel));
        return categories;
    }

    /**
     * 查询新版模板市场分类并与本地分类去重合并
     */
    public List<MarketMetaDataVO> getCategoriesV2() {
        List<MarketMetaDataVO> allCategories = new ArrayList<>();
        List<TemplateManageDTO> manageCategories = templateManageMapper.findCategories(null);
        List<MarketMetaDataVO> manageCategoriesTrans = manageCategories.stream()
                .map(templateCategory -> new MarketMetaDataVO(templateCategory.getId(), templateCategory.getName(), CommonConstants.TEMPLATE_SOURCE.MANAGE))
                .collect(Collectors.toList());
        Map<String, String> templateParams = sysParameterManage.groupVal("template.");
        String templateUrl = templateBaseUrl(templateParams);
        if (StringUtils.isBlank(templateUrl)) {
            return mergeAndDistinctByLabel(allCategories, manageCategoriesTrans);
        }
        try {
            String resultStr = marketGet(templateUrl + TEMPLATE_META_DATA_URL, null);
            MarketMetaDataBaseResponse metaData = JsonUtil.parseObject(resultStr, MarketMetaDataBaseResponse.class);
            allCategories.addAll(metaData.getLabels());
            allCategories.add(0, new MarketMetaDataVO("suggest", Translator.get("i18n_template_recommend"), CommonConstants.TEMPLATE_SOURCE.PUBLIC));
        } catch (Exception e) {
            LogUtil.error("模板市场分类获取错误", e);
        }

        return mergeAndDistinctByLabel(allCategories, manageCategoriesTrans);

    }

    /**
     * 从系统参数读取模板市场基础地址
     */
    private String templateBaseUrl() {
        return templateBaseUrl(sysParameterManage.groupVal("template."));
    }

    /**
     * 从指定参数集合中读取模板市场基础地址
     */
    private String templateBaseUrl(Map<String, String> templateParams) {
        if (templateParams == null) {
            return "";
        }
        return StringUtils.trimToEmpty(templateParams.get("template.url"));
    }

    /**
     * 按分类名称合并市场分类和本地分类并保持顺序
     */
    private List<MarketMetaDataVO> mergeAndDistinctByLabel(List<MarketMetaDataVO> list1, List<MarketMetaDataVO> list2) {
        List<MarketMetaDataVO> mergedList = new ArrayList<>(list1);
        mergedList.addAll(list2);
        Map<String, MarketMetaDataVO> marketMetaDataMap = mergedList.stream()
                .collect(Collectors.toMap(
                        MarketMetaDataVO::getLabel,
                        Function.identity(),
                        (existing, replacement) -> {
                            existing.setSource(CommonConstants.TEMPLATE_SOURCE.PUBLIC);
                            return existing;
                        },
                        LinkedHashMap::new
                ));
        return new ArrayList<>(marketMetaDataMap.values());
    }
}
