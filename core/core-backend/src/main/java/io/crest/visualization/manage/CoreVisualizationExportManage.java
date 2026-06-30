package io.crest.visualization.manage;

import com.fasterxml.jackson.core.type.TypeReference;
import io.crest.api.visualization.vo.DataVisualizationVO;
import io.crest.chart.constant.ChartConstants;
import io.crest.chart.manage.ChartDataManage;
import io.crest.chart.manage.ChartViewManege;
import io.crest.constant.CommonConstants;
import io.crest.constant.FieldTypeConstants;
import io.crest.dataset.server.DatasetFieldServer;
import io.crest.exception.CrestException;
import io.crest.exportCenter.util.ExportCenterUtils;
import io.crest.chart.server.ChartDataServer;
import io.crest.extensions.view.dto.ChartExtFilterDTO;
import io.crest.extensions.view.dto.ChartExtRequest;
import io.crest.extensions.view.dto.ChartViewDTO;
import io.crest.extensions.view.dto.ChartViewFieldDTO;
import io.crest.utils.AuthUtils;
import io.crest.utils.JsonUtil;
import io.crest.visualization.bo.ExcelSheetModel;
import io.crest.visualization.dao.ext.mapper.ExtDataVisualizationMapper;
import io.crest.visualization.template.FilterBuildTemplate;
import io.crest.visualization.utils.VisualizationExcelUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 负责核心可视化资源的 Excel 导出编排
 */
@Component
@SuppressWarnings("unchecked")
public class CoreVisualizationExportManage {
    @Resource
    private ExtDataVisualizationMapper extDataVisualizationMapper;

    @Resource
    private ChartViewManege chartViewManege;

    @Resource
    private ChartDataManage chartDataManage;

    @Resource
    private VisualizationTemplateExtendDataManage extendDataManage;

    @Resource
    private DatasetFieldServer datasetFieldServer;

    /**
     * 根据可视化资源标识查询导出资源名称
     */
    public String getResourceName(Long dvId, String busiFlag) {
        DataVisualizationVO visualization = extDataVisualizationMapper.findDvInfo(dvId, busiFlag, "core");
        if (ObjectUtils.isEmpty(visualization)) CrestException.throwException("资源不存在或已经被删除...");
        return visualization.getName();
    }

    /**
     * 导出指定可视化资源下的图表数据到 Excel 文件
     */
    public File exportExcel(Long dvId, String busiFlag, List<Long> viewIdList, boolean onlyDisplay, String filterJson) throws Exception {
        DataVisualizationVO visualization = extDataVisualizationMapper.findDvInfo(dvId, busiFlag, "core");
        if (ObjectUtils.isEmpty(visualization)) CrestException.throwException("资源不存在或已经被删除...");
        List<ChartViewDTO> chartViewDTOS = chartViewManege.listBySceneId(dvId, CommonConstants.RESOURCE_TABLE.CORE);

        String componentsJson = visualization.getComponentData();
        List<Map<String, Object>> components = JsonUtil.parseList(componentsJson, tokenType);
        components = components.stream().flatMap(item -> {
            if (ObjectUtils.isNotEmpty(item.get("innerType")) && Strings.CI.equals(item.get("innerType").toString(), "Tabs")) {
                if (ObjectUtils.isNotEmpty(item.get("propValue"))) {
                    List<Map<String, Object>> tabs = (List<Map<String, Object>>) item.get("propValue");
                    return tabs.stream().flatMap(tab -> ((List<Map<String, Object>>) tab.get("componentData")).stream());
                }
            }
            return Stream.of(item);
        }).toList();
        List<Long> idList = components.stream().filter(c -> ObjectUtils.isNotEmpty(c.get("id"))).map(component -> Long.parseLong(component.get("id").toString())).toList();

        if (CollectionUtils.isNotEmpty(viewIdList)) {
            chartViewDTOS = chartViewDTOS.stream().filter(item -> idList.contains(item.getId()) && viewIdList.contains(item.getId())).collect(Collectors.toList());
        }
        if (CollectionUtils.isEmpty(chartViewDTOS)) return null;
        Map<Long, ChartExtRequest> chartExtRequestMap = buildViewRequest(filterJson);
        List<ExcelSheetModel> sheets = new ArrayList<>();
        for (int i = 0; i < chartViewDTOS.size(); i++) {
            ChartViewDTO view = chartViewDTOS.get(i);
            ChartExtRequest extRequest = chartExtRequestMap.get(view.getId());
            if (ObjectUtils.isNotEmpty(extRequest)) {
                view.setChartExtRequest(extRequest);
            } else {
                view.setChartExtRequest(buildDefaultRequest());
            }
            view.getChartExtRequest().setUser(AuthUtils.getUser().getUserId());
            view.setTitle((i + 1) + "-" + view.getTitle());
            sheets.addAll(exportViewData(view));
        }

        return VisualizationExcelUtils.exportExcel(sheets, visualization.getName(), visualization.getId().toString());
    }

    /**
     * 将单个图表结果转换为 Excel 工作表模型
     */
    private ExcelSheetModel exportSingleData(Map<String, Object> chart, String title) {
        ExcelSheetModel result = new ExcelSheetModel();
        Object objectFields = chart.get("fields");
        List<ChartViewFieldDTO> fields = (List<ChartViewFieldDTO>) objectFields;
        List<String> heads = new ArrayList<>();
        List<String> headKeys = new ArrayList<>();
        List<Integer> fieldTypes = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(fields)) {
            fields.forEach(field -> {
                Object name = field.getName();
                Object engineFieldName = field.getEngineFieldName();
                Object fieldType = field.getFieldType();
                if (ObjectUtils.isNotEmpty(name) && ObjectUtils.isNotEmpty(engineFieldName)) {
                    heads.add(name.toString());
                    headKeys.add(engineFieldName.toString());
                    if (fieldType == null) {
                        field.setFieldType(FieldTypeConstants.STRING);
                        fieldType = FieldTypeConstants.STRING;
                    }
                    fieldTypes.add((int) fieldType);
                }
            });
        }
        Object objectTableRow = chart.get("tableRow");
        if (objectTableRow == null) {
            objectTableRow = chart.get("sourceData");
        }
        List<Map<String, Object>> tableRow = (List<Map<String, Object>>) objectTableRow;

        List<List<String>> details = tableRow.stream().map(row -> {
            List<String> tempList = new ArrayList<>();
            for (int i = 0; i < headKeys.size(); i++) {
                String key = headKeys.get(i);
                Object val = row.get(key);
                if (ObjectUtils.isEmpty(val)) {
                    tempList.add(StringUtils.EMPTY);
                } else if (fieldTypes.get(i) == 3) {
                    tempList.add(filterInvalidDecimal(val.toString()));
                } else {
                    tempList.add(val.toString());
                }
            }
            return tempList;
        }).collect(Collectors.toList());
        result.setHeads(heads);
        result.setData(details);
        result.setFiledTypes(fieldTypes);
        result.setSheetName(title);
        return result;
    }

    /**
     * 计算图表数据并转换为一个或多个导出工作表
     */
    private List<ExcelSheetModel> exportViewData(ChartViewDTO request) {

        ChartViewDTO chartViewDTO = null;
        request.setIsExcelExport(true);
        String type = request.getType();
        if (Strings.CI.equalsAny(type, "table-info", "table-normal")) {
            request.setResultCount(Math.toIntExact(ExportCenterUtils.getExportLimit("view")));
            request.setResultMode(ChartConstants.VIEW_RESULT_MODE.ALL);
        }
        if (CommonConstants.VIEW_DATA_FROM.TEMPLATE.equalsIgnoreCase(request.getDataFrom())) {
            chartViewDTO = extendDataManage.getChartDataInfo(request.getId(), request);
        } else {
            try {
                chartViewDTO = chartDataManage.calcData(request);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        String title = chartViewDTO.getTitle();
        Map<String, Object> chart = chartViewDTO.getData();
        List<ExcelSheetModel> resultList = new ArrayList<>();
        boolean leftExist = ObjectUtils.isNotEmpty(chart.get("left"));
        boolean rightExist = ObjectUtils.isNotEmpty(chart.get("right"));
        if (!leftExist && !rightExist) {
            ExcelSheetModel sheetModel = exportSingleData(chart, title);
            appendSummaryToSheet(sheetModel, chartViewDTO, chart);
            resultList.add(sheetModel);
            return resultList;
        }
        if (leftExist) {
            ExcelSheetModel sheetModel = exportSingleData((Map<String, Object>) chart.get("left"), title + "_left");
            resultList.add(sheetModel);
        }
        if (rightExist) {
            ExcelSheetModel sheetModel = exportSingleData((Map<String, Object>) chart.get("right"), title + "_right");
            resultList.add(sheetModel);
        }
        return resultList;
    }

    /**
     * 清理数值字符串末尾无效的小数零
     */
    private String filterInvalidDecimal(String sourceNumberStr) {
        if (StringUtils.isNotBlank(sourceNumberStr) && Strings.CS.contains(sourceNumberStr, ".")) {
            sourceNumberStr = sourceNumberStr.replaceAll("0+?$", "");
            sourceNumberStr = sourceNumberStr.replaceAll("[.]$", "");
        }
        return sourceNumberStr;
    }

    /**
     * 将图表汇总行追加到导出工作表末尾
     */
    @SuppressWarnings("unchecked")
    private void appendSummaryToSheet(ExcelSheetModel sheetModel, ChartViewDTO chartViewDTO, Map<String, Object> chart) {
        if (!ChartDataServer.isSummaryEnabled(chartViewDTO)) return;
        ChartDataServer.SummaryConfig config = ChartDataServer.parseSummaryConfig(chartViewDTO);
        List<ChartViewFieldDTO> allColumns = ChartDataServer.getAllExportColumns(chartViewDTO);
        ChartDataServer.SummaryAccumulator acc = new ChartDataServer.SummaryAccumulator();

        Object objectTableRow = chart.get("tableRow");
        if (objectTableRow == null) objectTableRow = chart.get("sourceData");
        if (objectTableRow == null) return;

        List<Map<String, Object>> tableRow = (List<Map<String, Object>>) objectTableRow;
        for (Map<String, Object> row : tableRow) {
            acc.totalCount++;
            for (int j = 0; j < allColumns.size(); j++) {
                ChartViewFieldDTO field = allColumns.get(j);
                String fName = field.getEngineFieldName();
                if (!config.summaryShowMap.containsKey(fName) || !config.summaryShowMap.get(fName)) continue;
                String sType = config.summaryTypeMap.get(fName);
                if (sType == null || "custom".equals(sType)) continue;
                Object valObj = row.get(fName);
                if (valObj == null || StringUtils.isBlank(valObj.toString())) continue;
                try {
                    BigDecimal val = new BigDecimal(valObj.toString());
                    switch (sType) {
                        case "max":
                            BigDecimal curMax = acc.maxMap.get(fName);
                            if (curMax == null || val.compareTo(curMax) > 0) acc.maxMap.put(fName, val);
                            break;
                        case "min":
                            BigDecimal curMin = acc.minMap.get(fName);
                            if (curMin == null || val.compareTo(curMin) < 0) acc.minMap.put(fName, val);
                            break;
                        default:
                            acc.sumMap.merge(fName, val, BigDecimal::add);
                            acc.countMap.merge(fName, 1L, Long::sum);
                            if ("var_pop".equals(sType) || "stddev_pop".equals(sType)) {
                                acc.sumOfSquaresMap.merge(fName, val.multiply(val), BigDecimal::add);
                            }
                            break;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (acc.totalCount == 0) return;

        Map<String, BigDecimal> customSumResult = chart.get("customSumResult") != null
                ? (Map<String, BigDecimal>) chart.get("customSumResult") : null;

        Object[] totalRowArr = ChartDataServer.buildSummaryRow(allColumns, config, acc, customSumResult);

        List<String> headKeys = new ArrayList<>();
        for (ChartViewFieldDTO field : allColumns) {
            headKeys.add(field.getEngineFieldName());
        }

        List<String> summaryRow = new ArrayList<>();
        for (int i = 0; i < headKeys.size(); i++) {
            if (i < totalRowArr.length && totalRowArr[i] != null) {
                summaryRow.add(totalRowArr[i].toString());
            } else {
                summaryRow.add(StringUtils.EMPTY);
            }
        }
        sheetModel.getData().add(summaryRow);
    }

    private final TypeReference<List<Map<String, Object>>> tokenType = new TypeReference<List<Map<String, Object>>>() {
    };

    /**
     * 将前端传入的筛选条件 JSON 转换为图表请求映射
     */
    private Map<Long, ChartExtRequest> buildViewRequest(String filterJson) {
        if (StringUtils.isBlank(filterJson)) {
            return new HashMap<>();
        }
        Map<Long, ChartExtRequest> extRequestMap = JsonUtil.parseObject(filterJson, new TypeReference<Map<Long, ChartExtRequest>>() {
        });
        extRequestMap.forEach((key, chartExtRequest) -> {
            chartExtRequest.setQueryFrom("panel");
            chartExtRequest.setResultCount(Math.toIntExact(ExportCenterUtils.getExportLimit("view")));
            chartExtRequest.setResultMode(ChartConstants.VIEW_RESULT_MODE.ALL);
            chartExtRequest.setPageSize(ExportCenterUtils.getExportLimit("view"));
        });
        return extRequestMap;
    }

    /**
     * 构建无筛选条件时的默认图表导出请求
     */
    private ChartExtRequest buildDefaultRequest() {
        ChartExtRequest chartExtRequest = new ChartExtRequest();
        chartExtRequest.setQueryFrom("panel");
        chartExtRequest.setFilter(new ArrayList<>());
        chartExtRequest.setResultCount(Math.toIntExact(ExportCenterUtils.getExportLimit("view")));
        chartExtRequest.setResultMode(ChartConstants.VIEW_RESULT_MODE.ALL);
        chartExtRequest.setPageSize(ExportCenterUtils.getExportLimit("view"));
        return chartExtRequest;
    }

    /**
     * 根据可视化面板组件配置构建图表筛选请求
     */
    private Map<String, ChartExtRequest> buildViewRequest(DataVisualizationVO panelDto, Boolean justView) {
        String componentsJson = panelDto.getComponentData();
        List<Map<String, Object>> components = JsonUtil.parseList(componentsJson, tokenType);
        Map<String, ChartExtRequest> result = new HashMap<>();
        Map<String, List<ChartExtFilterDTO>> panelFilters = FilterBuildTemplate.buildEmpty(components);
        for (Map.Entry<String, List<ChartExtFilterDTO>> entry : panelFilters.entrySet()) {
            List<ChartExtFilterDTO> chartExtFilterRequests = entry.getValue();
            ChartExtRequest chartExtRequest = new ChartExtRequest();
            chartExtRequest.setQueryFrom("panel");
            chartExtRequest.setFilter(chartExtFilterRequests);
            chartExtRequest.setResultCount(Math.toIntExact(ExportCenterUtils.getExportLimit("view")));
            chartExtRequest.setResultMode(ChartConstants.VIEW_RESULT_MODE.ALL);
            chartExtRequest.setPageSize(ExportCenterUtils.getExportLimit("view"));
            result.put(entry.getKey(), chartExtRequest);
        }
        return result;
    }

}
