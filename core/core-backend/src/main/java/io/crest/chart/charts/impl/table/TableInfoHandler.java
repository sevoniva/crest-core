package io.crest.chart.charts.impl.table;

import com.fasterxml.jackson.core.type.TypeReference;
import io.crest.api.chart.dto.PageInfo;
import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.chart.charts.impl.DefaultChartHandler;
import io.crest.constant.FieldTypeConstants;
import io.crest.engine.constant.ExtFieldConstant;
import io.crest.engine.sql.SQLProvider;
import io.crest.engine.trans.Dimension2SQLObj;
import io.crest.engine.trans.Quota2SQLObj;
import io.crest.engine.utils.Utils;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasourceRequest;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import io.crest.extensions.datasource.model.SQLMeta;
import io.crest.extensions.datasource.provider.Provider;
import io.crest.extensions.view.dto.*;
import io.crest.extensions.view.util.ChartDataUtil;
import io.crest.extensions.view.util.FieldUtil;
import io.crest.utils.BeanUtils;
import io.crest.utils.IDUtils;
import io.crest.utils.JsonUtil;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Component
@SuppressWarnings("unchecked")
/**
 * 明细表图表处理器，负责分页、明细查询和自定义汇总
 */
public class TableInfoHandler extends DefaultChartHandler {
    /**
     * 当前处理器对应的图表类型
     */
    @Getter
    private String type = "table-info";

    /**
     * 明细表仅保留维度轴，指标轴在格式化阶段置空
     */
    @Override
    public AxisFormatResult formatAxis(ChartViewDTO view) {
        var result = super.formatAxis(view);
        result.getAxisMap().put(ChartAxis.yAxis, new ArrayList<>());
        return result;
    }

    /**
     * 根据表格分页配置调整查询页码和页大小
     */
    @Override
    public <T extends CustomFilterResult> T customFilter(ChartViewDTO view, List<ChartExtFilterDTO> filterList, AxisFormatResult formatResult) {
        var chartExtRequest = view.getChartExtRequest();
        Map<String, Object> mapAttr = view.getCustomAttr();
        Map<String, Object> mapSize = (Map<String, Object>) mapAttr.get("basicStyle");
        var tablePageMode = (String) mapSize.get("tablePageMode");
        formatResult.getContext().put("tablePageMode", tablePageMode);
        if (Strings.CI.equals(tablePageMode, "page")) {
            if (chartExtRequest.getGoPage() == null) {
                chartExtRequest.setGoPage(1L);
            }
            if (chartExtRequest.getPageSize() == null) {
                int pageSize = (int) mapSize.get("tablePageSize");
                if (Strings.CI.equals(view.getResultMode(), "custom")) {
                    chartExtRequest.setPageSize(Math.min(pageSize, view.getResultCount().longValue()));
                } else {
                    chartExtRequest.setPageSize((long) pageSize);
                }
            }
        } else {
            if (Strings.CI.equals(view.getResultMode(), "custom")) {
                chartExtRequest.setGoPage(1L);
                chartExtRequest.setPageSize(view.getResultCount().longValue());
            } else if (!view.getIsExcelExport()) {
                chartExtRequest.setGoPage(null);
                chartExtRequest.setPageSize(null);
            }
        }
        return (T) new CustomFilterResult(filterList, formatResult.getContext());
    }

    /**
     * 明细表结果由计算阶段组装，这里保留可重载入口
     */
    @Override
    public Map<String, Object> buildResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, List<String[]> data) {
        return new HashMap<>();
    }

    /**
     * 计算明细表查询结果，并处理分页总数、动态阈值和自定义汇总
     */
    @Override
    public <T extends ChartCalcDataResult> T calcChartResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, Map<String, Object> sqlMap, SQLMeta sqlMeta, Provider provider) {
        var chartExtRequest = view.getChartExtRequest();
        var dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean crossDs = ((DatasetGroupInfoDTO) formatResult.getContext().get("dataset")).getIsCross();
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        fillDatasourceRequest(datasourceRequest, crossDs, dsMap, sqlMap);
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var allFields = (List<ChartViewFieldDTO>) filterResult.getContext().get("allFields");
        PageInfo pageInfo = new PageInfo();
        pageInfo.setGoPage(chartExtRequest.getGoPage());
        if (Strings.CI.equals(view.getResultMode(), "custom")) {
            pageInfo.setPageSize(Math.min(view.getResultCount() - (chartExtRequest.getGoPage() - 1) * chartExtRequest.getPageSize(), chartExtRequest.getPageSize()));
        } else {
            pageInfo.setPageSize(chartExtRequest.getPageSize());
        }
        Dimension2SQLObj.dimension2sqlObj(sqlMeta, xAxis, FieldUtil.transFields(allFields), crossDs, dsMap, Utils.getParams(FieldUtil.transFields(allFields)), view.getCalParams(), pluginManage);
        if (view.getExportDatasetOriginData()) {
            for (int i = 0; i < xAxis.size(); i++) {
                ChartViewFieldDTO fieldDTO = null;
                for (ChartViewFieldDTO allField : allFields) {
                    if (allField.getId().equals(xAxis.get(i).getId())) {
                        fieldDTO = allField;
                    }
                }
                if (fieldDTO != null && fieldDTO.isAgg()) {
                    sqlMeta.getXFields().get(i).setFieldName("'-'");
                }
            }
        }

        String originSql = SQLProvider.createQuerySQL(sqlMeta, false, !Strings.CI.equals(dsMap.entrySet().iterator().next().getValue().getType(), "es"), view);// 明细表强制加排序
        String limit = ((pageInfo.getGoPage() != null && pageInfo.getPageSize() != null) ? " LIMIT " + pageInfo.getPageSize() + " OFFSET " + (pageInfo.getGoPage() - 1) * chartExtRequest.getPageSize() : "");
        var querySql = originSql + limit;

        var tablePageMode = (String) filterResult.getContext().get("tablePageMode");
        var totalPageSql = "SELECT COUNT(*) FROM (" + SQLProvider.createQuerySQLNoSort(sqlMeta, false, view) + ") COUNT_TEMP";
        if (StringUtils.isNotEmpty(totalPageSql) && Strings.CI.equals(tablePageMode, "page")) {
            totalPageSql = provider.rebuildSQL(totalPageSql, sqlMeta, crossDs, dsMap);
            datasourceRequest.setQuery(totalPageSql);
            datasourceRequest.setTotalPageFlag(true);
            logger.info("calcite total sql: " + totalPageSql);
            List<String[]> tmpData = (List<String[]>) provider.fetchResultField(datasourceRequest).get("data");
            var totalItems = ObjectUtils.isEmpty(tmpData) ? 0 : Long.valueOf(tmpData.get(0)[0]);
            if (Strings.CI.equals(view.getResultMode(), "custom")) {
                totalItems = totalItems <= view.getResultCount() ? totalItems : view.getResultCount();
            }
            var totalPage = (totalItems / pageInfo.getPageSize()) + (totalItems % pageInfo.getPageSize() > 0 ? 1 : 0);
            view.setTotalItems(totalItems);
            view.setTotalPage(totalPage);
        }

        querySql = provider.rebuildSQL(querySql, sqlMeta, crossDs, dsMap);
        datasourceRequest.setQuery(querySql);
        logger.info("calcite chart sql: " + querySql);
        List<String[]> data = (List<String[]>) provider.fetchResultField(datasourceRequest).get("data");
        //自定义排序
        data = ChartDataUtil.resultCustomSort(xAxis, Collections.emptyList(), view.getSortPriority(), data);
        //数据重组逻辑可重载
        var result = this.buildResult(view, formatResult, filterResult, data);
        T calcResult = (T) new ChartCalcDataResult();
        calcResult.setData(result);
        calcResult.setContext(filterResult.getContext());
        calcResult.setQuerySql(querySql);
        calcResult.setOriginData(data);
        try {
            var dynamicAssistFields = getDynamicThresholdFields(view);
            Set<Long> fieldIds = xAxis.stream().map(ChartViewFieldDTO::getId).collect(Collectors.toSet());
            List<ChartViewFieldDTO> finalXAxis = xAxis;
            dynamicAssistFields.forEach(i -> {
                if (!fieldIds.contains(i.getFieldId())) {
                    ChartViewFieldDTO fieldDTO = new ChartViewFieldDTO();
                    BeanUtils.copyBean(fieldDTO, i.getCurField());
                    finalXAxis.add(fieldDTO);
                }
            });
            var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
            var assistFields = getAssistFields(dynamicAssistFields, yAxis, xAxis);
            if (CollectionUtils.isNotEmpty(assistFields)) {
                var req = new DatasourceRequest();
                req.setDsList(dsMap);

                List<ChartSeniorAssistDTO> assists = dynamicAssistFields.stream().filter(ele -> !Strings.CI.equals(ele.getSummary(), "last_item")).toList();
                if (ObjectUtils.isNotEmpty(assists)) {
                    var assistSql = assistSQL(originSql, assistFields, dsMap, crossDs);
                    var tmpSql = provider.rebuildSQL(assistSql, sqlMeta, crossDs, dsMap);
                    req.setQuery(tmpSql);
                    logger.debug("calcite assistSql sql: " + tmpSql);
                    var assistData = (List<String[]>) provider.fetchResultField(req).get("data");
                    calcResult.setAssistData(assistData);
                    calcResult.setDynamicAssistFields(assists);
                }

                List<ChartSeniorAssistDTO> assistsOriginList = dynamicAssistFields.stream().filter(ele -> Strings.CI.equals(ele.getSummary(), "last_item")).toList();
                if (ObjectUtils.isNotEmpty(assistsOriginList)) {
                    var assistSqlOriginList = assistSQLOriginList(originSql, assistFields, dsMap, crossDs);
                    var tmpSql = provider.rebuildSQL(assistSqlOriginList, sqlMeta, crossDs, dsMap);
                    req.setQuery(tmpSql);
                    logger.debug("calcite assistSql sql origin list: " + tmpSql);
                    var assistDataOriginList = (List<String[]>) provider.fetchResultField(req).get("data");
                    calcResult.setAssistDataOriginList(assistDataOriginList);
                    calcResult.setDynamicAssistFieldsOriginList(assistsOriginList);
                }
            }
        } catch (Exception e) {
            io.crest.utils.LogUtil.error(e.getMessage(), e);
        }
        // 自定义汇总
        var basicStyle = (Map<String, Object>) view.getCustomAttr().get("basicStyle");
        var showSummary = BooleanUtils.isTrue((Boolean) basicStyle.get("showSummary"));
        if (showSummary) {
            var fieldList = (List) basicStyle.get("seriesSummary");
            if (CollectionUtils.isNotEmpty(fieldList)) {
                var customCalcFields = new ArrayList<ChartViewFieldDTO>();
                var seriesList = JsonUtil.parseList(JsonUtil.toJSONString(fieldList).toString(), new TypeReference<List<ChartViewFieldDTO>>(){});
                var quotaIds = allFields.stream().map(DatasetTableFieldDTO::getEngineFieldName).collect(Collectors.toSet());
                seriesList.forEach(field -> {
                    if (!BooleanUtils.isTrue(field.getShow()) || !"custom".equalsIgnoreCase(field.getSummary())) {
                        return;
                    }
                    if (StringUtils.isBlank(field.getOriginName())) {
                        return;
                    }
                    if (!quotaIds.contains(field.getField())) {
                        return;
                    }
                    field.setSummary("");
                    field.setFieldType(FieldTypeConstants.FLOAT);
                    field.setId(IDUtils.snowID());
                    field.setExtField(ExtFieldConstant.EXT_CALC);
                    customCalcFields.add(field);
                });
                if (!customCalcFields.isEmpty()) {
                    var xFields = sqlMeta.getXFields();
                    var xOrder = sqlMeta.getXOrders();
                    // 清空维度值，获取完结果再设置回去
                    sqlMeta.setXFields(Collections.emptyList());
                    sqlMeta.setXOrders(Collections.emptyList());
                    List<DatasetTableFieldDTO> tmpList = FieldUtil.transFields(allFields);
                    tmpList.addAll(customCalcFields);
                    Quota2SQLObj.quota2sqlObj(sqlMeta, customCalcFields, tmpList, crossDs, dsMap, Utils.getParams(FieldUtil.transFields(allFields)), view.getCalParams(), pluginManage);
                    String customSumSql = SQLProvider.createQuerySQL(sqlMeta, false, !Strings.CI.equals(dsMap.values().iterator().next().getType(), "es"), view);
                    customSumSql = provider.rebuildSQL(customSumSql, sqlMeta, crossDs, dsMap);
                    var customSumReq = new DatasourceRequest();
                    fillDatasourceRequest(customSumReq, crossDs, dsMap, sqlMap);
                    customSumReq.setQuery(customSumSql);
                    var customSumData = (List<String[]>) provider.fetchResultField(customSumReq).get("data");
                    if (CollectionUtils.isNotEmpty(customSumData)) {
                        var customSumResult = new HashMap<String, BigDecimal>();
                        // 只取第一行结果
                        var customSumArr = customSumData.get(0);
                        for (int i = 0; i < customSumArr.length; i++) {
                            if (customCalcFields.get(i) != null && customSumArr[i] != null) {
                                try {
                                    customSumResult.put(customCalcFields.get(i).getField(), new BigDecimal(customSumArr[i]));
                                } catch (Exception e) {
                                    customSumResult.put(customCalcFields.get(i).getField(), new BigDecimal(0));
                                }
                            }
                        }
                        result.put("customSumResult", customSumResult);
                    }
                    sqlMeta.setXFields(xFields);
                    sqlMeta.setXOrders(xOrder);
                }
            }
        }
        return calcResult;
    }
}
