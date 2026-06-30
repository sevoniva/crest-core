package io.crest.chart.charts.impl;

import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.chart.charts.ChartHandlerManager;
import io.crest.chart.constant.ChartConstants;
import io.crest.chart.manage.ChartDataManage;
import io.crest.chart.manage.ChartViewManege;
import io.crest.chart.utils.ChartDataBuild;
import io.crest.constant.SQLConstants;
import io.crest.dataset.manage.DatasetTableFieldManage;
import io.crest.engine.sql.SQLProvider;
import io.crest.engine.trans.Dimension2SQLObj;
import io.crest.engine.trans.Quota2SQLObj;
import io.crest.engine.utils.Utils;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.api.PluginManageApi;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasourceRequest;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import io.crest.extensions.datasource.dto.TableFieldWithValue;
import io.crest.extensions.datasource.model.SQLMeta;
import io.crest.extensions.datasource.provider.Provider;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import io.crest.extensions.datasource.vo.PluginDatasourceVO;
import io.crest.extensions.view.dto.*;
import io.crest.extensions.view.plugin.AbstractChartPlugin;
import io.crest.extensions.view.util.ChartDataUtil;
import io.crest.extensions.view.util.FieldUtil;
import io.crest.utils.BeanUtils;
import io.crest.utils.JsonUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
@SuppressWarnings("unchecked")
// 默认图表处理器，负责通用图表字段格式化、SQL 构建、结果组装和快速计算
public class DefaultChartHandler extends AbstractChartPlugin {
    public static Logger logger = LoggerFactory.getLogger(ChartDataManage.class);
    @Resource
    protected ChartHandlerManager chartHandlerManager;
    @Resource
    protected DatasetTableFieldManage datasetTableFieldManage;
    @Resource
    protected ChartViewManege chartViewManege;
    @Getter
    private String render = "antv";
    @Getter
    private String type = "*";
    @Autowired(required = false)
    public PluginManageApi pluginManage;

    // 将默认处理器注册为 antv 下的兜底图表处理器
    @PostConstruct
    public void init() {
        chartHandlerManager.registerChartHandler(this.getRender(), this.getType(), this);
    }

    // 格式化默认图表轴字段，基础图表只保留 X 轴、Y 轴和下钻字段
    @Override
    public AxisFormatResult formatAxis(ChartViewDTO view) {
        var axisMap = new HashMap<ChartAxis, List<ChartViewFieldDTO>>();
        var context = new HashMap<String, Object>();
        var result = new AxisFormatResult(axisMap, context);
        axisMap.put(ChartAxis.xAxis, new ArrayList<>(view.getXAxis()));
        axisMap.put(ChartAxis.yAxis, new ArrayList<>(view.getYAxis()));
        axisMap.put(ChartAxis.drill, new ArrayList<>(view.getDrillFields()));
        return result;
    }

    // 应用自定义过滤上下文，字段脱敏时同步移除不可见字段
    @Override
    public <T extends CustomFilterResult> T customFilter(ChartViewDTO view, List<ChartExtFilterDTO> filterList, AxisFormatResult formatResult) {
        var desensitizationList = (Map<String, ColumnPermissionItem>) formatResult.getContext().get("desensitizationList");
        if (MapUtils.isNotEmpty(desensitizationList)) {
            formatResult.getAxisMap().forEach((axis, fields) -> {
                fields.removeIf(f -> desensitizationList.containsKey(f.getEngineFieldName()));
            });
        }
        return (T) new CustomFilterResult(filterList, formatResult.getContext());
    }

    // 填充数据源查询请求，包含跨数据源标记、数据源清单和字段值上下文
    protected void fillDatasourceRequest(DatasourceRequest datasourceRequest, boolean crossDs, Map<Long, DatasourceSchemaDTO> dsMap, Map<String, Object> sqlMap) {
        datasourceRequest.setIsCross(crossDs);
        datasourceRequest.setDsList(dsMap);
        List<TableFieldWithValue> tableFieldWithValues = getTableFieldWithValues(sqlMap);
        if (CollectionUtils.isNotEmpty(tableFieldWithValues)) {
            datasourceRequest.setTableFieldWithValues(tableFieldWithValues.stream().map(TableFieldWithValue::copy).toList());
        }
    }

    // 从 SQL 上下文中读取表字段和值信息
    protected List<TableFieldWithValue> getTableFieldWithValues(Map<String, Object> sqlMap) {
        return (List<TableFieldWithValue>) sqlMap.get("tableFieldWithValues");
    }

    // 将查询结果转换为图表通用数据结构
    public Map<String, Object> buildResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, List<String[]> data) {
        boolean isDrill = filterResult
                .getFilterList()
                .stream()
                .anyMatch(ele -> ele.getFilterType() == 1);
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
        Map<String, Object> result = ChartDataBuild.transChartData(xAxis, yAxis, view, data, isDrill);
        return result;
    }

    // 计算图表数据，完成 SQL 构建、数据查询、排序、快速计算和结果封装
    @Override
    public <T extends ChartCalcDataResult> T calcChartResult(ChartViewDTO view, AxisFormatResult formatResult, CustomFilterResult filterResult, Map<String, Object> sqlMap, SQLMeta sqlMeta, Provider provider) {
        var dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean needOrder = Utils.isNeedOrder(dsList);
        boolean crossDs = ((DatasetGroupInfoDTO) formatResult.getContext().get("dataset")).getIsCross();
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        fillDatasourceRequest(datasourceRequest, crossDs, dsMap, sqlMap);
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
        var allFields = (List<ChartViewFieldDTO>) filterResult.getContext().get("allFields");
        Dimension2SQLObj.dimension2sqlObj(sqlMeta, xAxis, FieldUtil.transFields(allFields), crossDs, dsMap, Utils.getParams(FieldUtil.transFields(allFields)), view.getCalParams(), pluginManage);
        Quota2SQLObj.quota2sqlObj(sqlMeta, yAxis, FieldUtil.transFields(allFields), crossDs, dsMap, Utils.getParams(FieldUtil.transFields(allFields)), view.getCalParams(), pluginManage);
        String querySql = SQLProvider.createQuerySQL(sqlMeta, true, needOrder, view);
        querySql = provider.rebuildSQL(querySql, sqlMeta, crossDs, dsMap);
        datasourceRequest.setQuery(querySql);
        logger.debug("calcite chart sql: " + querySql);
        List<String[]> data = (List<String[]>) provider.fetchResultField(datasourceRequest).get("data");
        // 查询结果按图表配置执行自定义排序
        data = ChartDataUtil.resultCustomSort(xAxis, yAxis, view.getSortPriority(), data);
        // 对指标字段执行占比、累加和同环比等快速计算
        var extStack = formatResult.getAxisMap().get(ChartAxis.extStack);
        var xAxisExt = formatResult.getAxisMap().get(ChartAxis.xAxisExt);
        quickCalc(xAxis, yAxis, xAxisExt, extStack, view.getType(), data);
        // 数据重组逻辑允许具体图表处理器覆写
        var result = this.buildResult(view, formatResult, filterResult, data);
        T calcResult = (T) new ChartCalcDataResult();
        calcResult.setData(result);
        calcResult.setContext(filterResult.getContext());
        calcResult.setQuerySql(querySql);
        calcResult.setOriginData(data);
        return calcResult;
    }

    // 将计算结果写回图表对象，表格导出和普通渲染使用不同数据结构
    @Override
    public ChartViewDTO buildChart(ChartViewDTO view, ChartCalcDataResult calcResult, AxisFormatResult formatResult, CustomFilterResult filterResult) {
        var desensitizationList = (Map<String, ColumnPermissionItem>) filterResult.getContext().get("desensitizationList");
        var allFields = (List<ChartViewFieldDTO>) filterResult.getContext().get("allFields");
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
        // 表格导出场景只需要表格源数据，避免继续构建渲染数据
        var extStack = formatResult.getAxisMap().get(ChartAxis.extStack);
        if (CollectionUtils.isNotEmpty(extStack) && xAxis.size() > extStack.size()) {
            xAxis = xAxis.subList(0, xAxis.size() - extStack.size());
        }
        if (view.getIsExcelExport()) {
            Map<String, Object> sourceInfo = ChartDataBuild.transTableNormal(xAxis, yAxis, view, calcResult.getOriginData(), extStack, desensitizationList);
            sourceInfo.put("sourceData", calcResult.getOriginData());
            // 将汇总计算结果传递到导出数据中
            if (calcResult.getData() != null && calcResult.getData().get("customSumResult") != null) {
                sourceInfo.put("customSumResult", calcResult.getData().get("customSumResult"));
            }
            view.setData(sourceInfo);
            return view;
        }

        Map<String, Object> mapTableNormal = ChartDataBuild.transTableNormal(xAxis, yAxis, view, calcResult.getOriginData(), extStack, desensitizationList);
        var drillFilters = filterResult.getFilterList().stream().filter(f -> f.getFilterType() == 1).collect(Collectors.toList());
        // 日期下钻展示时恢复原始值，避免前端拿到时间戳范围
        drillFilters.forEach(f -> {
            if (CollectionUtils.isNotEmpty(f.getOriginValue())) {
                f.setValue(f.getOriginValue());
            }
        });
        var isDrill = CollectionUtils.isNotEmpty(drillFilters);
        // 合并图表数据、表格源数据和动态辅助线
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.putAll(calcResult.getData());
        dataMap.putAll(mapTableNormal);
        dataMap.put("sourceFields", allFields);
        List<ChartSeniorAssistDTO> chartSeniorAssistDTOS = mergeAssistField(calcResult.getDynamicAssistFields(), calcResult.getAssistData(), calcResult.getDynamicAssistFieldsOriginList(), calcResult.getAssistDataOriginList());
        dataMap.put("dynamicAssistLines", chartSeniorAssistDTOS);
        view.setData(dataMap);
        view.setSql(null);
        view.setDrill(isDrill);
        view.setDrillFilters(drillFilters);
        return view;
    }

    // 合并动态辅助线字段查询结果和原始明细查询结果
    protected List<ChartSeniorAssistDTO> mergeAssistField(List<ChartSeniorAssistDTO> dynamicAssistFields, List<String[]> assistData, List<ChartSeniorAssistDTO> dynamicAssistFieldsOriginList, List<String[]> assistDataOriginList) {
        List<ChartSeniorAssistDTO> list = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(assistData)) {
            String[] strings = assistData.get(0);
            for (int i = 0; i < dynamicAssistFields.size(); i++) {
                if (i < strings.length) {
                    ChartSeniorAssistDTO chartSeniorAssistDTO = dynamicAssistFields.get(i);
                    chartSeniorAssistDTO.setValue(strings[i]);
                    list.add(chartSeniorAssistDTO);
                }
            }
        }

        if (ObjectUtils.isNotEmpty(assistDataOriginList)) {
            // 原始明细查询当前只取最后一行，后续如需其他聚合逻辑可扩展此处
            String[] stringsOriginList = assistDataOriginList.get(assistDataOriginList.size() - 1);
            for (int i = 0; i < dynamicAssistFieldsOriginList.size(); i++) {
                if (i < stringsOriginList.length) {
                    ChartSeniorAssistDTO chartSeniorAssistDTO = dynamicAssistFieldsOriginList.get(i);
                    chartSeniorAssistDTO.setValue(stringsOriginList[i]);
                    list.add(chartSeniorAssistDTO);
                }
            }
        }
        return list;
    }

    // 从高级配置中提取启用的动态辅助线字段
    protected List<ChartSeniorAssistDTO> getDynamicAssistFields(ChartViewDTO view) {
        List<ChartSeniorAssistDTO> list = new ArrayList<>();

        Map<String, Object> senior = view.getSenior();
        if (ObjectUtils.isEmpty(senior)) {
            return list;
        }

        ChartSeniorAssistCfgDTO assistLineCfg = JsonUtil.parseObject((String) JsonUtil.toJSONString(senior.get("assistLineCfg")), ChartSeniorAssistCfgDTO.class);
        if (null == assistLineCfg || !assistLineCfg.isEnable()) {
            return list;
        }
        List<ChartSeniorAssistDTO> assistLines = assistLineCfg.getAssistLine();

        if (ObjectUtils.isEmpty(assistLines)) {
            return list;
        }

        for (ChartSeniorAssistDTO dto : assistLines) {
            if (Strings.CI.equals(dto.getField(), "0")) {
                continue;
            }
            Long fieldId = dto.getFieldId();
            String summary = dto.getSummary();
            if (ObjectUtils.isEmpty(fieldId) || StringUtils.isEmpty(summary)) {
                continue;
            }

            DatasetTableFieldDTO datasetTableFieldDTO = datasetTableFieldManage.selectById(fieldId);

            if (ObjectUtils.isEmpty(datasetTableFieldDTO)) {
                continue;
            }
            list.add(dto);
        }
        return list;
    }

    // 根据 Y 轴字段生成辅助线查询字段
    protected List<ChartViewFieldDTO> getAssistFields(List<ChartSeniorAssistDTO> list, List<ChartViewFieldDTO> axis) {
        return getAssistFields(list, axis, SQLConstants.FIELD_ALIAS_Y_PREFIX);
    }

    // 根据轴字段和别名前缀生成辅助线查询字段
    protected List<ChartViewFieldDTO> getAssistFields(List<ChartSeniorAssistDTO> list, List<ChartViewFieldDTO> axis, String prefix) {
        List<ChartViewFieldDTO> res = new ArrayList<>();
        for (ChartSeniorAssistDTO dto : list) {
            DatasetTableFieldDTO curField = dto.getCurField();
            ChartViewFieldDTO targetField = null;
            String alias = "";
            for (int i = 0; i < axis.size(); i++) {
                ChartViewFieldDTO field = axis.get(i);
                if (Objects.equals(field.getId(), curField.getId())) {
                    targetField = field;
                    alias = String.format(prefix, i);
                    break;
                }
            }
            if (ObjectUtils.isEmpty(targetField)) {
                continue;
            }

            ChartViewFieldDTO chartViewFieldDTO = new ChartViewFieldDTO();
            BeanUtils.copyBean(chartViewFieldDTO, curField);
            chartViewFieldDTO.setSummary(dto.getSummary());
            // 使用轴字段别名作为辅助线 SQL 的查找字段名
            chartViewFieldDTO.setOriginName(alias);
            res.add(chartViewFieldDTO);
        }
        return res;
    }

    // 在 Y 轴和 X 轴中查找辅助线字段，并生成对应查询字段
    protected List<ChartViewFieldDTO> getAssistFields(List<ChartSeniorAssistDTO> list, List<ChartViewFieldDTO> yAxis, List<ChartViewFieldDTO> xAxis) {
        List<ChartViewFieldDTO> res = new ArrayList<>();
        for (ChartSeniorAssistDTO dto : list) {
            DatasetTableFieldDTO curField = dto.getCurField();
            ChartViewFieldDTO field = null;
            String alias = "";
            for (int i = 0; i < yAxis.size(); i++) {
                ChartViewFieldDTO yField = yAxis.get(i);
                if (Objects.equals(yField.getId(), curField.getId())) {
                    field = yField;
                    alias = String.format(SQLConstants.FIELD_ALIAS_Y_PREFIX, i);
                    break;
                }
            }
            if (ObjectUtils.isEmpty(field) && CollectionUtils.isNotEmpty(xAxis)) {
                for (int i = 0; i < xAxis.size(); i++) {
                    ChartViewFieldDTO xField = xAxis.get(i);
                    if (Strings.CI.equals(String.valueOf(xField.getId()), String.valueOf(curField.getId()))) {
                        field = xField;
                        alias = String.format(SQLConstants.FIELD_ALIAS_X_PREFIX, i);
                        break;
                    }
                }
            }
            if (ObjectUtils.isEmpty(field)) {
                continue;
            }

            ChartViewFieldDTO chartViewFieldDTO = new ChartViewFieldDTO();
            BeanUtils.copyBean(chartViewFieldDTO, curField);
            chartViewFieldDTO.setSummary(dto.getSummary());
            // 使用轴字段别名作为辅助线 SQL 的查找字段名
            chartViewFieldDTO.setOriginName(alias);
            res.add(chartViewFieldDTO);
        }
        return res;
    }

    // 从表格阈值动态条件中提取需要额外查询的字段
    public List<ChartSeniorAssistDTO> getDynamicThresholdFields(ChartViewDTO view) {
        List<ChartSeniorAssistDTO> list = new ArrayList<>();
        Map<String, Object> senior = view.getSenior();
        if (ObjectUtils.isEmpty(senior)) {
            return list;
        }
        ChartSeniorThresholdCfgDTO thresholdCfg = JsonUtil.parseObject((String) JsonUtil.toJSONString(senior.get("threshold")), ChartSeniorThresholdCfgDTO.class);

        if (null == thresholdCfg || !thresholdCfg.isEnable()) {
            return list;
        }
        List<TableThresholdDTO> tableThreshold = thresholdCfg.getTableThreshold();

        if (ObjectUtils.isEmpty(tableThreshold)) {
            return list;
        }

        List<ChartSeniorThresholdDTO> conditionsList = tableThreshold.stream()
                .filter(item -> !ObjectUtils.isEmpty(item))
                .map(TableThresholdDTO::getConditions)
                .flatMap(List::stream)
                .filter(condition -> Strings.CI.equalsAny(condition.getType(), "dynamic"))
                .toList();

        List<ChartSeniorAssistDTO> assistDTOs = conditionsList.stream()
                .flatMap(condition -> getConditionFields(condition).stream())
                .filter(this::solveThresholdCondition)
                .toList();

        list.addAll(assistDTOs);

        return list;
    }

    // 校验阈值动态字段是否存在，并补齐字段详情和汇总方式
    private boolean solveThresholdCondition(ChartSeniorAssistDTO fieldDTO) {
        Long fieldId = fieldDTO.getFieldId();
        String summary = fieldDTO.getValue();
        if (ObjectUtils.isEmpty(fieldId) || StringUtils.isEmpty(summary)) {
            return false;
        }

        DatasetTableFieldDTO datasetTableFieldDTO = datasetTableFieldManage.selectById(fieldId);
        if (ObjectUtils.isEmpty(datasetTableFieldDTO)) {
            return false;
        }
        ChartViewFieldDTO datasetTableField = new ChartViewFieldDTO();
        BeanUtils.copyBean(datasetTableField, datasetTableFieldDTO);
        fieldDTO.setCurField(datasetTableField);
        fieldDTO.setSummary(summary);
        return true;
    }

    // 从单个阈值条件中提取动态字段引用
    private List<ChartSeniorAssistDTO> getConditionFields(ChartSeniorThresholdDTO condition) {
        List<ChartSeniorAssistDTO> list = new ArrayList<>();
        if ("between".equals(condition.getTerm())) {
            if (!Strings.CI.equals(condition.getDynamicMaxField().getSummary(), "value")) {
                list.add(of(condition.getDynamicMaxField()));
            }
            if (!Strings.CI.equals(condition.getDynamicMinField().getSummary(), "value")) {
                list.add(of(condition.getDynamicMinField()));
            }
        } else {
            if (!Strings.CI.equals(condition.getDynamicField().getSummary(), "value")) {
                list.add(of(condition.getDynamicField()));
            }
        }

        return list;
    }

    // 将动态阈值字段转换为辅助字段描述
    private ChartSeniorAssistDTO of(ThresholdDynamicFieldDTO dynamicField) {
        ChartSeniorAssistDTO conditionField = new ChartSeniorAssistDTO();
        conditionField.setFieldId(Long.parseLong(dynamicField.getFieldId()));
        conditionField.setValue(dynamicField.getSummary());
        return conditionField;
    }

    // 构建动态辅助线聚合 SQL
    protected String assistSQL(String sql, List<ChartViewFieldDTO> assistFields, Map<Long, DatasourceSchemaDTO> dsMap, boolean crossDs) {
        // 根据数据源类型补齐字段引用前后缀
        String dsType = dsMap.entrySet().iterator().next().getValue().getType();
        String prefix = "";
        String suffix = "";
        if (Arrays.stream(DatasourceConfiguration.DatasourceType.values()).map(DatasourceConfiguration.DatasourceType::getType).toList().contains(dsType)) {
            DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(dsType);
            prefix = datasourceType.getPrefix();
            suffix = datasourceType.getSuffix();
        } else {
            List<PluginDatasourceVO> pluginDatasourceList = pluginManage.queryPluginDs();
            List<PluginDatasourceVO> list = pluginDatasourceList.stream().filter(ele -> Strings.CS.equals(ele.getType(), dsType)).toList();
            if (ObjectUtils.isNotEmpty(list)) {
                PluginDatasourceVO first = list.get(0);
                prefix = first.getPrefix();
                suffix = first.getSuffix();
            } else {
                CrestException.throwException("当前数据源插件不存在");
            }
        }

        List<String> fieldList = new ArrayList<>();
        for (int i = 0; i < assistFields.size(); i++) {
            ChartViewFieldDTO dto = assistFields.get(i);
            if (Strings.CI.equals(dto.getSummary(), "last_item")) {
                continue;
            }
            if (crossDs) {
                fieldList.add(dto.getSummary() + "(" + dto.getOriginName() + ")");
            } else {
                fieldList.add(dto.getSummary() + "(" + prefix + dto.getOriginName() + suffix + ")");
            }
        }
        return "SELECT " + String.join(",", fieldList) + " FROM (" + sql + ") tmp";
    }

    // 构建 last_item 辅助线原始明细 SQL
    protected String assistSQLOriginList(String sql, List<ChartViewFieldDTO> assistFields, Map<Long, DatasourceSchemaDTO> dsMap, boolean crossDs) {
        // 根据数据源类型补齐字段引用前后缀
        String dsType = dsMap.entrySet().iterator().next().getValue().getType();
        String prefix = "";
        String suffix = "";
        if (Arrays.stream(DatasourceConfiguration.DatasourceType.values()).map(DatasourceConfiguration.DatasourceType::getType).toList().contains(dsType)) {
            DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(dsType);
            prefix = datasourceType.getPrefix();
            suffix = datasourceType.getSuffix();
        } else {
            List<PluginDatasourceVO> pluginDatasourceList = pluginManage.queryPluginDs();
            List<PluginDatasourceVO> list = pluginDatasourceList.stream().filter(ele -> Strings.CS.equals(ele.getType(), dsType)).toList();
            if (ObjectUtils.isNotEmpty(list)) {
                PluginDatasourceVO first = list.get(0);
                prefix = first.getPrefix();
                suffix = first.getSuffix();
            } else {
                CrestException.throwException("当前数据源插件不存在");
            }
        }

        List<String> fieldList = new ArrayList<>();
        for (int i = 0; i < assistFields.size(); i++) {
            ChartViewFieldDTO dto = assistFields.get(i);
            if (Strings.CI.equals(dto.getSummary(), "last_item")) {
                if (crossDs) {
                    fieldList.add(dto.getOriginName());
                } else {
                    fieldList.add(prefix + dto.getOriginName() + suffix);
                }
            }
        }
        return "SELECT " + String.join(",", fieldList) + " FROM (" + sql + ") tmp";
    }

    // 合并扩展 X 轴和堆叠字段 ID，用于累加计算分组
    protected List<String> mergeIds(List<ChartViewFieldDTO> xAxisExt, List<ChartViewFieldDTO> extStack) {
        Set<String> idSet = new HashSet<>();
        if (xAxisExt != null) {
            xAxisExt.forEach(field -> idSet.add(String.valueOf(field.getId())));
        }
        if (extStack != null) {
            extStack.forEach(field -> idSet.add(String.valueOf(field.getId())));
        }
        return new ArrayList<>(idSet);
    }

    // 对指标结果执行占比、累加、同比和环比等快速计算
    protected void quickCalc(List<ChartViewFieldDTO> xAxis, List<ChartViewFieldDTO> yAxis
            , List<ChartViewFieldDTO> xAxisExt, List<ChartViewFieldDTO> extStack, String chartType, List<String[]> data) {
        for (int i = 0; i < yAxis.size(); i++) {
            ChartViewFieldDTO chartViewFieldDTO = yAxis.get(i);
            ChartFieldCompareDTO compareCalc = chartViewFieldDTO.getCompareCalc();
            if (ObjectUtils.isEmpty(compareCalc)) {
                continue;
            }
            if (StringUtils.isNotEmpty(compareCalc.getType())
                    && !Strings.CI.equals(compareCalc.getType(), "none")) {
                // 同环比计算使用用户选中的时间字段作为对比基准
                Long compareFieldId = compareCalc.getField();
                // 当前指标在查询结果中的字段下标
                int dataIndex = xAxis.size() + i;
                if (Arrays.asList(ChartConstants.M_Y).contains(compareCalc.getType())) {
                    // 结果模式决定输出上一期值、差值或变化率
                    String resultData = compareCalc.getResultData();
                    // 获取对比时间字段及其在维度中的下标
                    List<ChartViewFieldDTO> checkedField = new ArrayList<>(xAxis);
                    int timeIndex = 0;
                    ChartViewFieldDTO timeField = null;
                    for (int j = 0; j < checkedField.size(); j++) {
                        if (Objects.equals(checkedField.get(j).getId(), compareFieldId)) {
                            timeIndex = j;
                            timeField = checkedField.get(j);
                        }
                    }
                    // 对比时间字段缺失或日期粒度不支持时，当前指标无法计算同环比
                    if (ObjectUtils.isEmpty(timeField) || !checkCalcType(timeField.getDateStyle(), compareCalc.getType())) {
                        // 将无法计算的指标值置空
                        for (String[] item : data) {
                            item[dataIndex] = null;
                        }
                    } else {
                        // 同环比计算分三步：缓存当期数据、计算上一期时间、按配置输出差值或比例
                        Map<String, String> currentMap = new LinkedHashMap<>();
                        for (String[] item : data) {
                            String[] dimension = Arrays.copyOfRange(item, 0, checkedField.size());
                            currentMap.put(StringUtils.join(dimension, "-"), item[dataIndex]);
                        }

                        for (int index = 0; index < data.size(); index++) {
                            String[] item = data.get(index);
                            String cTime = item[timeIndex];
                            String cValue = item[dataIndex];

                            // 用上一期时间替换当前时间维度，并拼接其它维度定位上一期值
                            String lastTime = calcLastTime(cTime, compareCalc.getType(), timeField.getDateStyle(), timeField.getDatePattern());
                            String[] dimension = Arrays.copyOfRange(item, 0, checkedField.size());
                            dimension[timeIndex] = lastTime;

                            String lastValue = currentMap.get(StringUtils.join(dimension, "-"));
                            if (StringUtils.isEmpty(cValue) || StringUtils.isEmpty(lastValue)) {
                                item[dataIndex] = null;
                            } else {
                                if (Strings.CI.equals(resultData, "sub")) {
                                    item[dataIndex] = new BigDecimal(cValue).subtract(new BigDecimal(lastValue)).toString();
                                } else if (Strings.CI.equals(resultData, "percent")) {
                                    if (new BigDecimal(lastValue).compareTo(BigDecimal.ZERO) == 0) {
                                        item[dataIndex] = null;
                                    } else {
                                        item[dataIndex] = new BigDecimal(cValue)
                                                .divide(new BigDecimal(lastValue).abs(), 8, RoundingMode.HALF_UP)
                                                .subtract(new BigDecimal(1))
                                                .setScale(8, RoundingMode.HALF_UP)
                                                .toString();
                                    }
                                } else if (Strings.CI.equals(resultData, "pre")) {
                                    item[dataIndex] = new BigDecimal(lastValue).toString();
                                }
                            }
                        }
                    }
                } else if (Strings.CI.equals(compareCalc.getType(), "percent")) {
                    // 先计算当前指标总和
                    BigDecimal sum = new BigDecimal(0);
                    for (int index = 0; index < data.size(); index++) {
                        String[] item = data.get(index);
                        String cValue = item[dataIndex];
                        if (StringUtils.isEmpty(cValue)) {
                            continue;
                        }
                        sum = sum.add(new BigDecimal(cValue));
                    }
                    // 再将每行指标值转换为总量占比
                    for (int index = 0; index < data.size(); index++) {
                        String[] item = data.get(index);
                        String cValue = item[dataIndex];
                        if (StringUtils.isEmpty(cValue)) {
                            continue;
                        }
                        item[dataIndex] = new BigDecimal(cValue)
                                .divide(sum, 8, RoundingMode.HALF_UP)
                                .toString();
                    }
                } else if (Strings.CI.equals(compareCalc.getType(), "accumulate")) {
                    // 对指标值执行累计求和
                    if (CollectionUtils.isEmpty(data)) {
                        break;
                    }
                    if (Objects.isNull(extStack)) {
                        extStack = Arrays.asList();
                    }
                    if (Objects.isNull(xAxisExt)) {
                        xAxisExt = Arrays.asList();
                    }
                    boolean isStack = Strings.CI.contains(chartType, "stack") && CollectionUtils.isNotEmpty(extStack);
                    boolean isGroup = Strings.CI.contains(chartType, "group")
                            || (CollectionUtils.isNotEmpty(xAxisExt));
                    if (isStack || isGroup) {
                        if (CollectionUtils.isEmpty(xAxis)) {
                            break;
                        }
                        final Map<String, Integer> mainIndexMap = new HashMap<>();
                        final List<List<String[]>> mainMatrix = new ArrayList<>();
                        // 累加主分组排除分组和堆叠字段
                        List<String> groupStackAxisIds = mergeIds(xAxisExt, extStack);
                        List<ChartViewFieldDTO> xAxisBase = xAxis.stream().filter(ele -> !groupStackAxisIds.contains(String.valueOf(ele.getId()))).collect(Collectors.toList());
                        if (CollectionUtils.isEmpty(xAxisBase) && CollectionUtils.isNotEmpty(xAxis)) {
                            xAxisBase.add(xAxis.get(0));
                        }
                        data.forEach(item -> {
                            String[] mainAxisArr = Arrays.copyOfRange(item, 0, xAxisBase.size());
                            String mainAxis = StringUtils.join(mainAxisArr, '-');
                            Integer index = mainIndexMap.get(mainAxis);
                            if (index == null) {
                                mainIndexMap.put(mainAxis, mainMatrix.size());
                                List<String[]> tmp = new ArrayList<>();
                                tmp.add(item);
                                mainMatrix.add(tmp);
                            } else {
                                List<String[]> tmp = mainMatrix.get(index);
                                tmp.add(item);
                            }
                        });
                        int finalDataIndex = dataIndex;
                        int subEndIndex = xAxisBase.size();
                        if (CollectionUtils.isNotEmpty(xAxisExt)
                                || Strings.CI.contains(chartType, "group")
                                || Strings.CI.contains(chartType, "-mix")) {
                            subEndIndex += xAxisExt.size();
                        }
                        if (Strings.CI.contains(chartType, "stack")) {
                            subEndIndex += extStack.size();
                        }
                        int finalSubEndIndex = subEndIndex;
                        // 缓存上一组中各分组堆叠组合的累计值
                        Map<String, BigDecimal> preDataMap = new HashMap<>();
                        // 按主分组顺序进行滑动累加
                        for (int k = 1; k < mainMatrix.size(); k++) {
                            List<String[]> preDataItems = mainMatrix.get(k - 1);
                            List<String[]> curDataItems = mainMatrix.get(k);
                            preDataItems.forEach(preDataItem -> {
                                String[] groupStackAxisArr = Arrays.copyOfRange(preDataItem, xAxisBase.size(), finalSubEndIndex);
                                String groupStackAxis = StringUtils.join(groupStackAxisArr, '-');
                                String preVal = preDataItem[finalDataIndex];
                                if (StringUtils.isBlank(preVal)) {
                                    preVal = "0";
                                }
                                preDataMap.put(groupStackAxis, new BigDecimal(preVal));
                            });
                            curDataItems.forEach(curDataItem -> {
                                String[] groupStackAxisArr = Arrays.copyOfRange(curDataItem, xAxisBase.size(), finalSubEndIndex);
                                String groupStackAxis = StringUtils.join(groupStackAxisArr, '-');
                                BigDecimal preValue = preDataMap.get(groupStackAxis);
                                if (preValue != null) {
                                    curDataItem[finalDataIndex] = new BigDecimal(curDataItem[finalDataIndex])
                                            .add(preValue)
                                            .toString();
                                } else {
                                    if (preDataMap.containsKey(groupStackAxis)) {
                                        curDataItem[finalDataIndex] = new BigDecimal(curDataItem[finalDataIndex])
                                                .add(preDataMap.get(groupStackAxis))
                                                .toString();
                                    }
                                }
                            });
                        }
                    } else {
                        final int index = dataIndex;
                        final AtomicReference<BigDecimal> accumValue = new AtomicReference<>(new BigDecimal(0));
                        data.forEach(item -> {
                            String val = item[index];
                            BigDecimal curAccumValue = accumValue.get();
                            if (!StringUtils.isBlank(val)) {
                                BigDecimal curVal = new BigDecimal(val);
                                curAccumValue = curAccumValue.add(curVal);
                                accumValue.set(curAccumValue);
                            }
                            item[index] = curAccumValue.toString();
                        });
                    }
                }
            }
        }
    }

    // 根据同环比类型计算上一期时间字符串
    private String calcLastTime(String cTime, String type, String dateStyle, String datePattern) {
        try {
            String lastTime = null;
            Calendar calendar = Calendar.getInstance();
            if (Strings.CI.equals(type, ChartConstants.YEAR_MOM)) {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy");
                Date date = simpleDateFormat.parse(cTime);
                calendar.setTime(date);
                calendar.add(Calendar.YEAR, -1);
                lastTime = simpleDateFormat.format(calendar.getTime());
            } else if (Strings.CI.equals(type, ChartConstants.MONTH_MOM)) {
                SimpleDateFormat simpleDateFormat = null;
                if (Strings.CI.equals(datePattern, "date_split")) {
                    simpleDateFormat = new SimpleDateFormat("yyyy/MM");
                } else {
                    simpleDateFormat = new SimpleDateFormat("yyyy-MM");
                }
                Date date = simpleDateFormat.parse(cTime);
                calendar.setTime(date);
                calendar.add(Calendar.MONTH, -1);
                lastTime = simpleDateFormat.format(calendar.getTime());
            } else if (Strings.CI.equals(type, ChartConstants.YEAR_YOY)) {
                SimpleDateFormat simpleDateFormat = null;
                if (Strings.CI.equals(dateStyle, "y_M")) {
                    if (Strings.CI.equals(datePattern, "date_split")) {
                        simpleDateFormat = new SimpleDateFormat("yyyy/MM");
                    } else {
                        simpleDateFormat = new SimpleDateFormat("yyyy-MM");
                    }
                } else if (Strings.CI.equals(dateStyle, "y_M_d")) {
                    if (Strings.CI.equals(datePattern, "date_split")) {
                        simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");
                    } else {
                        simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    }
                }
                Date date = simpleDateFormat.parse(cTime);
                calendar.setTime(date);
                calendar.add(Calendar.YEAR, -1);
                lastTime = simpleDateFormat.format(calendar.getTime());
            } else if (Strings.CI.equals(type, ChartConstants.DAY_MOM)) {
                SimpleDateFormat simpleDateFormat = null;
                if (Strings.CI.equals(datePattern, "date_split")) {
                    simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");
                } else {
                    simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                }
                Date date = simpleDateFormat.parse(cTime);
                calendar.setTime(date);
                calendar.add(Calendar.DAY_OF_MONTH, -1);
                lastTime = simpleDateFormat.format(calendar.getTime());
            } else if (Strings.CI.equals(type, ChartConstants.MONTH_YOY)) {
                SimpleDateFormat simpleDateFormat = null;
                if (Strings.CI.equals(dateStyle, "y_M")) {
                    if (Strings.CI.equals(datePattern, "date_split")) {
                        simpleDateFormat = new SimpleDateFormat("yyyy/MM");
                    } else {
                        simpleDateFormat = new SimpleDateFormat("yyyy-MM");
                    }
                } else if (Strings.CI.equals(dateStyle, "y_M_d")) {
                    if (Strings.CI.equals(datePattern, "date_split")) {
                        simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");
                    } else {
                        simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    }
                }
                Date date = simpleDateFormat.parse(cTime);
                calendar.setTime(date);
                calendar.add(Calendar.MONTH, -1);
                lastTime = simpleDateFormat.format(calendar.getTime());
            }
            return lastTime;
        } catch (Exception e) {
            return cTime;
        }
    }

    // 校验日期粒度是否支持指定同环比计算类型
    private boolean checkCalcType(String dateStyle, String calcType) {
        switch (dateStyle) {
            case "y":
                return Strings.CI.equals(calcType, "year_mom");
            case "y_M":
                return Strings.CI.equals(calcType, "month_mom")
                        || Strings.CI.equals(calcType, "year_yoy");
            case "y_M_d":
                return Strings.CI.equals(calcType, "day_mom")
                        || Strings.CI.equals(calcType, "month_yoy")
                        || Strings.CI.equals(calcType, "year_yoy");
        }
        return false;
    }

    // 对同环比筛选条件做上一期时间平移，并返回是否发生变更
    protected boolean checkYoyFilter(List<ChartExtFilterDTO> filter, List<ChartViewFieldDTO> yoyAxis) {
        boolean flag = false;
        for (ChartExtFilterDTO filterDTO : filter) {
            for (ChartViewFieldDTO chartViewFieldDTO : yoyAxis) {
                ChartFieldCompareDTO compareCalc = chartViewFieldDTO.getCompareCalc();
                if (ObjectUtils.isEmpty(compareCalc)) {
                    continue;
                }
                if (StringUtils.isNotEmpty(compareCalc.getType())
                        && !Strings.CI.equals(compareCalc.getType(), "none")) {
                    if (Arrays.asList(ChartConstants.M_Y).contains(compareCalc.getType())) {
                        if (Strings.CI.equals(compareCalc.getField() + "", filterDTO.getFieldId())
                                && (filterDTO.getFilterType() == 0 || filterDTO.getFilterType() == 2)) {
                            // 将筛选时间回退一年以匹配同比查询
                            try {
                                Calendar calendar = Calendar.getInstance();
                                calendar.setTime(new Date(Long.parseLong(filterDTO.getValue().get(0))));
                                calendar.add(Calendar.YEAR, -1);
                                filterDTO.getValue().set(0, String.valueOf(calendar.getTime().getTime()));
                                flag = true;
                            } catch (Exception e) {
                            }
                        }
                    }
                }
            }
        }
        return flag;
    }

    ;

    // 处理分组或堆叠图的下钻字段和过滤条件
    protected void groupStackDrill(List<ChartViewFieldDTO> xAxis,
                                   List<ChartExtFilterDTO> filterList,
                                   List<ChartViewFieldDTO> fieldsToFilter,
                                   List<ChartViewFieldDTO> drillFields,
                                   List<ChartDrillRequest> drillRequestList) {
        var fields = xAxis.stream().map(ChartViewFieldDTO::getId).collect(Collectors.toSet());
        ChartDrillRequest head = drillRequestList.get(0);
        Map<Long, String> dimValMap = new HashMap<>();
        head.getDimensionList().forEach(item -> dimValMap.put(item.getId(), item.getValue()));
        Map<Long, ChartViewFieldDTO> fieldMap = xAxis.stream().collect(Collectors.toMap(ChartViewFieldDTO::getId, o -> o, ((p, n) -> p)));
        for (int i = 0; i < drillRequestList.size(); i++) {
            ChartDrillRequest request = drillRequestList.get(i);
            ChartViewFieldDTO chartViewFieldDTO = drillFields.get(i);
            for (ChartDimensionDTO requestDimension : request.getDimensionList()) {
                // 将钻取值作为过滤条件，并把当前和下一层钻取字段补入 X 轴
                if (Objects.equals(requestDimension.getId(), chartViewFieldDTO.getId())) {
                    fieldsToFilter.add(chartViewFieldDTO);
                    dimValMap.put(requestDimension.getId(), requestDimension.getValue());
                    if (!fields.contains(requestDimension.getId())) {
                        fieldMap.put(chartViewFieldDTO.getId(), chartViewFieldDTO);
                        chartViewFieldDTO.setSource(FieldSource.DRILL);
                        xAxis.add(chartViewFieldDTO);
                        fields.add(requestDimension.getId());
                    }
                    if (i == drillRequestList.size() - 1) {
                        ChartViewFieldDTO nextDrillField = drillFields.get(i + 1);
                        if (!fields.contains(nextDrillField.getId())) {
                            // 下一层钻取字段沿用首层钻取字段排序
                            nextDrillField.setSort(getDrillSort(xAxis, drillFields.get(0)));
                            nextDrillField.setSource(FieldSource.DRILL);
                            xAxis.add(nextDrillField);
                            fields.add(nextDrillField.getId());
                        }
                    }
                }
            }
        }
        for (int i = 0; i < fieldsToFilter.size(); i++) {
            ChartViewFieldDTO tmpField = fieldsToFilter.get(i);
            ChartExtFilterDTO tmpFilter = new ChartExtFilterDTO();
            DatasetTableFieldDTO datasetTableField = datasetTableFieldManage.selectById(tmpField.getId());
            tmpFilter.setDatasetTableField(datasetTableField);
            tmpFilter.setDateStyle(fieldMap.get(tmpField.getId()).getDateStyle());
            tmpFilter.setDatePattern(fieldMap.get(tmpField.getId()).getDatePattern());
            tmpFilter.setFieldId(String.valueOf(tmpField.getId()));
            tmpFilter.setFilterType(1);
            if (datasetTableField.getFieldType() == 1) {
                tmpFilter.setOriginValue(Collections.singletonList(dimValMap.get(tmpField.getId())));
                tmpFilter.setOperator("between");
                // 时间字段钻取值按过滤组件规则转换为起止时间范围
                Map<String, Long> stringLongMap = Utils.parseDateTimeValue(dimValMap.get(tmpField.getId()));
                tmpFilter.setValue(Arrays.asList(String.valueOf(stringLongMap.get("startTime")), String.valueOf(stringLongMap.get("endTime"))));
            } else {
                tmpFilter.setOperator("in");
                tmpFilter.setValue(Collections.singletonList(dimValMap.get(tmpField.getId())));
            }
            filterList.add(tmpFilter);
        }
    }

    // 获取下钻字段在当前 X 轴中的排序方式
    private String getDrillSort(List<ChartViewFieldDTO> xAxis, ChartViewFieldDTO field) {
        String res = "";
        for (ChartViewFieldDTO f : xAxis) {
            if (Objects.equals(f.getId(), field.getId())) {
                if (Strings.CI.equals(f.getSort(), "asc") || Strings.CI.equals(f.getSort(), "desc")) {
                    res = f.getSort();
                    break;
                }
            }
        }
        return res;
    }
}
