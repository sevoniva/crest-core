package io.crest.chart.manage;

import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.api.permissions.auth.dto.BusiPerCheckDTO;
import io.crest.api.permissions.dataset.dto.DataSetRowPermissionsTreeDTO;
import io.crest.chart.charts.ChartHandlerManager;
import io.crest.chart.constant.ChartConstants;
import io.crest.constant.AuthEnum;
import io.crest.dataset.manage.DatasetGroupManage;
import io.crest.dataset.manage.DatasetSQLManage;
import io.crest.dataset.manage.DatasetTableFieldManage;
import io.crest.dataset.manage.PermissionManage;
import io.crest.dataset.sync.DatasetSyncQueryManage;
import io.crest.dataset.utils.DatasetParameterFieldId;
import io.crest.dataset.utils.DatasetUtils;
import io.crest.engine.sql.SQLProvider;
import io.crest.engine.trans.*;
import io.crest.engine.utils.Utils;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.api.PluginManageApi;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasourceRequest;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import io.crest.extensions.datasource.dto.TableFieldWithValue;
import io.crest.extensions.datasource.factory.ProviderFactory;
import io.crest.extensions.datasource.model.SQLMeta;
import io.crest.extensions.datasource.provider.Provider;
import io.crest.extensions.view.dto.*;
import io.crest.extensions.view.factory.PluginsChartFactory;
import io.crest.extensions.view.filter.FilterTreeObj;
import io.crest.extensions.view.plugin.AbstractChartPlugin;
import io.crest.extensions.view.util.FieldUtil;
import io.crest.i18n.Translator;
import io.crest.portal.DataPortalPermissionManage;
import io.crest.result.ResultCode;
import io.crest.system.manage.CorePermissionManage;
import io.crest.utils.AuthUtils;
import io.crest.utils.BeanUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图表数据计算与字段枚举查询管理器
 */
@Component
@SuppressWarnings("unchecked")
public class ChartDataManage {
    /**
     * 数据集字段管理服务
     */
    @Resource
    private DatasetTableFieldManage datasetTableFieldManage;
    /**
     * 数据集分组管理服务
     */
    @Resource
    private DatasetGroupManage datasetGroupManage;
    /**
     * 数据集 SQL 管理服务
     */
    @Resource
    private DatasetSQLManage datasetSQLManage;
    /**
     * 数据集同步查询路由服务
     */
    @Resource
    private DatasetSyncQueryManage datasetSyncQueryManage;
    /**
     * 图表视图管理服务
     */
    @Resource
    private ChartViewManege chartViewManege;
    /**
     * 数据集权限管理服务
     */
    @Resource
    private PermissionManage permissionManage;
    /**
     * 图表过滤树解析服务
     */
    @Resource
    private ChartFilterTreeService chartFilterTreeService;
    /**
     * 图表处理器管理器
     */
    @Resource
    private ChartHandlerManager chartHandlerManager;

    /**
     * 核心权限校验服务
     */
    @Resource
    private CorePermissionManage corePermissionManage;
    /**
     * 数据门户发布阅读权限服务
     */
    @Resource
    private DataPortalPermissionManage dataPortalPermissionManage;
    /**
     * 可选的数据源插件管理服务
     */
    @Autowired(required = false)
    private PluginManageApi pluginManage;

    /**
     * 数据集参数起止值拆分标记
     */
    public static final String START_END_SEPARATOR = "_START_END_SPLIT";

    /**
     * 图表数据计算日志记录器
     */
    private static final Logger logger = LoggerFactory.getLogger(ChartDataManage.class);

    /**
     * 根据图表配置、权限和过滤条件计算最终图表数据
     *
     * @param view 图表视图配置
     * @return 已填充数据的图表视图配置
     * @throws Exception 数据集、权限或图表处理器计算异常
     */
    public ChartViewDTO calcData(ChartViewDTO view) throws Exception {
        ChartExtRequest chartExtRequest = view.getChartExtRequest();
        if (chartExtRequest == null) {
            chartExtRequest = new ChartExtRequest();
        }

        if (ObjectUtils.isEmpty(view)) {
            CrestException.throwException(ResultCode.DATA_IS_WRONG.code(), Translator.get("i18n_chart_delete"));
        }
        if (ObjectUtils.isNotEmpty(AuthUtils.getUser())) {
            chartExtRequest.setUser(AuthUtils.getUser().getUserId());
        }
        if (view.getChartExtRequest() == null) {
            view.setChartExtRequest(chartExtRequest);
        }

        //excel导出，如果是从仪表板获取图表数据，则仪表板的查询模式，查询结果的数量，覆盖图表对应的属性
        if (view.getIsExcelExport()) {
            view.setResultMode(ChartConstants.VIEW_RESULT_MODE.CUSTOM);
        } else if (ChartConstants.VIEW_RESULT_MODE.CUSTOM.equals(chartExtRequest.getResultMode())) {
            view.setResultMode(chartExtRequest.getResultMode());
            view.setResultCount(chartExtRequest.getResultCount());
        }

        AbstractChartPlugin chartHandler;
        if (BooleanUtils.isTrue(view.getIsPlugin())) {
            chartHandler = PluginsChartFactory.getInstance(view.getRender(), view.getType());
        } else {
            chartHandler = chartHandlerManager.getChartHandler(view.getRender(), view.getType());
        }
        if (chartHandler == null) {
            CrestException.throwException(ResultCode.DATA_IS_WRONG.code(), Translator.get("i18n_chart_not_handler") + ": " + view.getRender() + "," + view.getType());
        }

        var drillAxis = new ArrayList<ChartViewFieldDTO>();
        boolean portalVisualizationRead = dataPortalPermissionManage.canReadPublishedVisualization(view.getSceneId(), null);
        DatasetGroupInfoDTO table = datasetGroupManage.datasetGroupInfoDTOForVisualizationRead(
                view.getTableId(), null, view.getSceneId());
        if (table == null) {
            CrestException.throwException(ResultCode.DATA_IS_WRONG.code(), Translator.get("i18n_no_ds"));
        }
        // 校验数据集读取权限
        BusiPerCheckDTO dto = new BusiPerCheckDTO();
        dto.setId(table.getId());
        dto.setAuthEnum(AuthEnum.READ);
        boolean checked = portalVisualizationRead || corePermissionManage.checkAuth(dto);
        if (!checked) {
            CrestException.throwException(Translator.get("i18n_no_dataset_permission"));
        }

        List<ChartViewFieldDTO> allFields = getAllChartFields(view);
        // 处理列级权限
        Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
        List<DatasetTableFieldDTO> columnPermissionFields = permissionManage.filterColumnPermissions(transFields(allFields), desensitizationList, table.getId(), chartExtRequest.getUser());
        // 处理行级权限
        List<DataSetRowPermissionsTreeDTO> rowPermissionsTree = permissionManage.getRowPermissionsTree(table.getId(), chartExtRequest.getUser());
        //将没有权限的列删掉
        List<String> engineFieldNames = columnPermissionFields.stream().map(DatasetTableFieldDTO::getEngineFieldName).collect(Collectors.toList());
        //计数字段
        engineFieldNames.add("*");
        AxisFormatResult formatResult = chartHandler.formatAxis(view);
        formatResult.getContext().put("dataset", table);
        formatResult.getContext().put("desensitizationList", desensitizationList);
        var xAxis = formatResult.getAxisMap().get(ChartAxis.xAxis);
        var yAxis = formatResult.getAxisMap().get(ChartAxis.yAxis);
        formatResult.getContext().put("allFields", allFields);
        var axisMap = formatResult.getAxisMap();
        axisMap.forEach((axis, fields) -> fields.removeIf(fieldDTO -> !engineFieldNames.contains(fieldDTO.getEngineFieldName())));

        // 过滤来自仪表板的条件
        List<ChartExtFilterDTO> extFilterList = new ArrayList<>();
        //组件过滤条件
        List<SqlVariableDetails> sqlVariables = datasetGroupManage.sqlParamsForVisualizationRead(
                Collections.singletonList(view.getTableId()), view.getSceneId());
        if (ObjectUtils.isNotEmpty(chartExtRequest.getFilter())) {
            for (ChartExtFilterDTO request : chartExtRequest.getFilter()) {
                // 解析多个fieldId,fieldId是一个逗号分隔的字符串
                String fieldId = request.getFieldId();
                if (request.getIsTree() == null) {
                    request.setIsTree(false);
                }

                boolean hasParameters = false;
                if (CollectionUtils.isNotEmpty(sqlVariables)) {
                    if (DatasetParameterFieldId.matches(fieldId)) {
                        hasParameters = true;
                    } else {
                        for (SqlVariableDetails parameter : Optional.ofNullable(request.getParameters()).orElse(new ArrayList<>())) {
                            String parameterId = Strings.CS.endsWith(parameter.getId(), START_END_SEPARATOR) ? parameter.getId().split(START_END_SEPARATOR)[0] : parameter.getId();
                            if (sqlVariables.stream().map(SqlVariableDetails::getId).collect(Collectors.toList()).contains(parameterId)) {
                                hasParameters = true;
                            }
                        }
                    }

                }

                if (hasParameters) {
                    continue;
                }

                if (StringUtils.isNotEmpty(fieldId)) {
                    List<Long> fieldIds = Arrays.stream(fieldId.split(",")).map(Long::valueOf).collect(Collectors.toList());

                    if (request.getIsTree()) {
                        ChartExtFilterDTO filterRequest = new ChartExtFilterDTO();
                        BeanUtils.copyBean(filterRequest, request);
                        filterRequest.setFilterType(0);
                        filterRequest.setDatasetTableFieldList(new ArrayList<>());
                        for (Long fId : fieldIds) {
                            DatasetTableFieldDTO datasetTableField = datasetTableFieldManage.selectById(fId);
                            if (datasetTableField == null) {
                                continue;
                            }
                            if (Objects.equals(datasetTableField.getDatasetGroupId(), view.getTableId())) {
                                if (ObjectUtils.isNotEmpty(filterRequest.getViewIds())) {
                                    if (filterRequest.getViewIds().contains(view.getId())) {
                                        filterRequest.getDatasetTableFieldList().add(datasetTableField);
                                    }
                                } else {
                                    filterRequest.getDatasetTableFieldList().add(datasetTableField);
                                }
                            }
                        }
                        if (ObjectUtils.isNotEmpty(filterRequest.getDatasetTableFieldList())) {
                            extFilterList.add(filterRequest);
                        }
                    } else {
                        for (Long fId : fieldIds) {
                            ChartExtFilterDTO filterRequest = new ChartExtFilterDTO();
                            BeanUtils.copyBean(filterRequest, request);
                            filterRequest.setFilterType(0);
                            filterRequest.setFieldId(fId + "");

                            DatasetTableFieldDTO datasetTableField = datasetTableFieldManage.selectById(fId);
                            if (datasetTableField == null) {
                                continue;
                            }
                            filterRequest.setDatasetTableField(datasetTableField);
                            if (Objects.equals(datasetTableField.getDatasetGroupId(), view.getTableId())) {
                                if (ObjectUtils.isNotEmpty(filterRequest.getViewIds())) {
                                    if (filterRequest.getViewIds().contains(view.getId())) {
                                        extFilterList.add(filterRequest);
                                    }
                                } else {
                                    extFilterList.add(filterRequest);
                                }
                            }
                        }
                    }
                }
            }
        }

        List<ChartExtFilterDTO> filters = new ArrayList<>();
        FilterTreeObj customLinkageFilter = null;
        // 联动条件
        if (ObjectUtils.isNotEmpty(chartExtRequest.getLinkageFilters())) {
            for (ChartExtFilterDTO linkageFilter : chartExtRequest.getLinkageFilters()) {
                if(linkageFilter != null){
                    if (3 == linkageFilter.getFilterType()) {
                        customLinkageFilter = linkageFilter.getCustomFilter();
                    } else {
                        filters.add(linkageFilter);
                    }
                }
            }
        }
        // 外部参数条件
        if (ObjectUtils.isNotEmpty(chartExtRequest.getOuterParamsFilters())) {
            filters.addAll(chartExtRequest.getOuterParamsFilters());
        }

        // web参数条件
        if (ObjectUtils.isNotEmpty(chartExtRequest.getWebParamsFilters())) {
            filters.addAll(chartExtRequest.getWebParamsFilters());
        }


        //联动过滤条件和外部参数过滤条件全部加上
        if (ObjectUtils.isNotEmpty(filters)) {
            for (ChartExtFilterDTO request : filters) {
                // 数据集参数字段需要补充原始参数定义。
                if (DatasetParameterFieldId.matches(request.getFieldId())) {
                    // 组装sql 参数原始数据
                    if (CollectionUtils.isNotEmpty(sqlVariables)) {
                        for (SqlVariableDetails sourceVariables : sqlVariables) {
                            if (sourceVariables.getId().equals(request.getFieldId())) {
                                if (CollectionUtils.isEmpty(request.getParameters())) {
                                    request.setParameters(new ArrayList<>());
                                }
                                request.getParameters().add(sourceVariables);
                            }
                        }

                    }
                } else {
                    DatasetTableFieldDTO datasetTableField = datasetTableFieldManage.selectById(Long.valueOf(request.getFieldId()));
                    request.setDatasetTableField(datasetTableField);
                    request.setFilterType(2);
                    // 相同数据集
                    if (Objects.equals(datasetTableField.getDatasetGroupId(), view.getTableId())) {
                        if (ObjectUtils.isNotEmpty(request.getViewIds())) {
                            if (request.getViewIds().contains(view.getId())) {
                                extFilterList.add(request);
                            }
                        } else {
                            extFilterList.add(request);
                        }
                    }

                }
            }
        }

        // 下钻
        List<ChartDrillRequest> drillRequestList = chartExtRequest.getDrill();
        var drill = formatResult.getAxisMap().get(ChartAxis.drill);
        if (ObjectUtils.isNotEmpty(drillRequestList) && (drill.size() > drillRequestList.size())) {
            var fields = xAxis.stream().map(ChartViewFieldDTO::getId).collect(Collectors.toSet());
            for (int i = 0; i < drillRequestList.size(); i++) {
                ChartDrillRequest request = drillRequestList.get(i);
                for (ChartDimensionDTO dim : request.getDimensionList()) {
                    ChartViewFieldDTO viewField = drill.get(i);
                    // 将钻取值作为条件传递，将所有钻取字段作为xAxis并加上下一个钻取字段
                    if (Objects.equals(dim.getId(), viewField.getId())) {
                        DatasetTableFieldDTO datasetTableField = datasetTableFieldManage.selectById(dim.getId());
                        ChartViewFieldDTO d = new ChartViewFieldDTO();
                        BeanUtils.copyBean(d, datasetTableField);

                        ChartExtFilterDTO drillFilter = new ChartExtFilterDTO();
                        drillFilter.setFieldId(String.valueOf(dim.getId()));
                        drillFilter.setDatasetTableField(datasetTableField);
                        drillFilter.setDatePattern(viewField.getDatePattern());
                        drillFilter.setDateStyle(viewField.getDateStyle());
                        drillFilter.setFilterType(1);
                        if (datasetTableField.getFieldType() == 1) {
                            drillFilter.setOperator("between");
                            drillFilter.setOriginValue(Collections.singletonList(dim.getValue()));
                            // 把value类似过滤组件处理，获得start time和end time
                            Map<String, Long> stringLongMap = Utils.parseDateTimeValue(dim.getValue());
                            drillFilter.setValue(Arrays.asList(String.valueOf(stringLongMap.get("startTime")), String.valueOf(stringLongMap.get("endTime"))));
                        } else {
                            drillFilter.setOperator("in");
                            drillFilter.setValue(Collections.singletonList(dim.getValue()));
                        }
                        extFilterList.add(drillFilter);

                        if (!fields.contains(dim.getId())) {
                            viewField.setSource(FieldSource.DRILL);
                            xAxis.add(viewField);
                            drillAxis.add(viewField);
                            fields.add(dim.getId());
                        }
                        if (i == drillRequestList.size() - 1) {
                            ChartViewFieldDTO nextDrillField = drill.get(i + 1);
                            if (!fields.contains(nextDrillField.getId())) {
                                nextDrillField.setSource(FieldSource.DRILL);
                                xAxis.add(nextDrillField);
                                drillAxis.add(nextDrillField);
                                fields.add(nextDrillField.getId());
                            } else {
                                Optional<ChartViewFieldDTO> axis = xAxis.stream().filter(x -> Objects.equals(x.getId(), nextDrillField.getId())).findFirst();
                                axis.ifPresent(field -> {
                                    field.setSort(nextDrillField.getSort());
                                    field.setCustomSort(nextDrillField.getCustomSort());
                                });
                                drillAxis.add(nextDrillField);
                            }
                        }
                    }
                }
            }
        }

        formatResult.getContext().put("drillAxis", drillAxis);

        // 保存原始值，不再在此处预转义，由后面的 sanitizeSqlLiteral 统一负责转义
        extFilterList.forEach(ele -> {
            if (ObjectUtils.isNotEmpty(ele.getValue())) {
                if (CollectionUtils.isEmpty(ele.getOriginValue())) {
                    ele.setOriginValue(ele.getValue());
                }
            }
        });
        // 视图自定义过滤逻辑
        CustomFilterResult filterResult = chartHandler.customFilter(view, extFilterList, formatResult);

        if (ObjectUtils.isEmpty(xAxis) && ObjectUtils.isEmpty(yAxis)) {
            return emptyChartViewDTO(view);
        }
        // 字段过滤器
        FilterTreeObj fieldCustomFilter = view.getCustomFilter();
        // 指标表联动时 使用的CustomFilter
        if (customLinkageFilter != null) {
            fieldCustomFilter = customLinkageFilter;
        }
        chartFilterTreeService.searchFieldAndSet(fieldCustomFilter);
        // 获取dsMap,union sql
        Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(table, chartExtRequest);
        sqlMap = datasetSyncQueryManage.routeIfSynced(table, sqlMap);
        String sql = (String) sqlMap.get("sql");
        Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        boolean crossDs = table.getIsCross();
        if (!crossDs) {
            sql = Utils.replaceSchemaAlias(sql, dsMap);
        }

        if (ObjectUtils.isEmpty(dsMap)) {
            CrestException.throwException(ResultCode.DATA_IS_WRONG.code(), Translator.get("i18n_datasource_delete"));
        }
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            DatasourceSchemaDTO ds = next.getValue();
            if (StringUtils.isNotEmpty(ds.getStatus()) && "Error".equalsIgnoreCase(ds.getStatus())) {
                CrestException.throwException(ResultCode.DATA_IS_WRONG.code(), Translator.get("i18n_invalid_ds"));
            }
        }

        Provider provider;
        if (crossDs) {
            provider = ProviderFactory.getDefaultProvider();
        } else {
            provider = ProviderFactory.getProvider(dsMap.entrySet().iterator().next().getValue().getType());
        }

        if (ObjectUtils.isEmpty(view.getCalParams())) {
            view.setCalParams(Utils.getParams(transFields(allFields)));
        }

        SQLMeta sqlMeta = new SQLMeta();
        Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);
        CustomWhere2Str.customWhere2sqlObj(sqlMeta, fieldCustomFilter, transFields(allFields), crossDs, dsMap, Utils.getParams(transFields(allFields)), view.getCalParams(), pluginManage);
        ExtWhere2Str.extWhere2sqlOjb(sqlMeta, extFilterList, transFields(allFields), crossDs, dsMap, Utils.getParams(transFields(allFields)), view.getCalParams(), pluginManage);
        WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, transFields(allFields), crossDs, dsMap, Utils.getParams(transFields(allFields)), view.getCalParams(), pluginManage);
        // 插件数据源仍复用核心 SQL 组装流程，后续扩展点保持在插件类型判断内。
        if (BooleanUtils.isTrue(view.getIsPlugin())) {
            List<String> dsList = new ArrayList<>();
            for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
                dsList.add(next.getValue().getType());
            }
            boolean needOrder = Utils.isNeedOrder(dsList);
            Dimension2SQLObj.dimension2sqlObj(sqlMeta, xAxis, FieldUtil.transFields(allFields), crossDs, dsMap, Utils.getParams(FieldUtil.transFields(allFields)), view.getCalParams(), pluginManage);
            Quota2SQLObj.quota2sqlObj(sqlMeta, yAxis, FieldUtil.transFields(allFields), crossDs, dsMap, Utils.getParams(FieldUtil.transFields(allFields)), view.getCalParams(), pluginManage);
            String querySql = SQLProvider.createQuerySQL(sqlMeta, true, needOrder, view);
            querySql = provider.rebuildSQL(querySql, sqlMeta, crossDs, dsMap);
            filterResult.getContext().put("querySql", querySql);
        }

        ChartCalcDataResult calcResult = chartHandler.calcChartResult(view, formatResult, filterResult, sqlMap, sqlMeta, provider);
        return chartHandler.buildChart(view, calcResult, formatResult, filterResult);
    }

    /**
     * 获取仪表盘和水波图的动态最大值字段
     *
     * @param view 图表视图配置
     * @return 动态最大值字段列表
     * @throws Exception 动态字段缺失或类型不匹配时抛出异常
     */
    private List<ChartViewFieldDTO> getSizeField(ChartViewDTO view) throws Exception {
        List<ChartViewFieldDTO> list = new ArrayList<>();
        Map<String, Object> customAttr = view.getCustomAttr();

        Map<String, Object> size = (Map<String, Object>) customAttr.get("misc");

        ChartViewFieldDTO gaugeMinViewField = getDynamicField(size, "gaugeMinType", "gaugeMinField");
        if (gaugeMinViewField != null) {
            list.add(gaugeMinViewField);
        }
        ChartViewFieldDTO gaugeMaxViewField = getDynamicField(size, "gaugeMaxType", "gaugeMaxField");
        if (gaugeMaxViewField != null) {
            list.add(gaugeMaxViewField);
        }
        ChartViewFieldDTO liquidMaxViewField = getDynamicField(size, "liquidMaxType", "liquidMaxField");
        if (liquidMaxViewField != null) {
            list.add(liquidMaxViewField);
        }

        return list;
    }

    /**
     * 根据样式配置解析单个动态数值字段
     *
     * @param sizeObj 图表样式中的数值配置
     * @param type 字段类型配置键
     * @param field 字段内容配置键
     * @return 解析到的图表字段，未启用动态字段时返回 null
     */
    private ChartViewFieldDTO getDynamicField(Map<String, Object> sizeObj, String type, String field) {
        String maxType = (String) sizeObj.get(type);
        if (Strings.CI.equals("dynamic", maxType)) {
            Map<String, Object> maxField = (Map<String, Object>) sizeObj.get(field);
            Long id = Long.valueOf((String) maxField.get("id"));
            String summary = (String) maxField.get("summary");
            DatasetTableFieldDTO datasetTableField = datasetTableFieldManage.selectById(id);
            if (ObjectUtils.isNotEmpty(datasetTableField)) {
                if (datasetTableField.getFieldType() == 0 || datasetTableField.getFieldType() == 1 || datasetTableField.getFieldType() == 5) {
                    if (!Strings.CI.contains(summary, "count")) {
                        CrestException.throwException(Translator.get("i18n_gauge_field_change"));
                    }
                }
                ChartViewFieldDTO dto = new ChartViewFieldDTO();
                BeanUtils.copyBean(dto, datasetTableField);
                dto.setSummary(summary);
                return dto;
            } else {
                CrestException.throwException(Translator.get("i18n_gauge_field_delete"));
            }
        }
        return null;
    }

    /**
     * 复制基础视图信息并返回空数据图表
     *
     * @param view 原始图表视图配置
     * @return 空数据图表视图配置
     */
    private ChartViewDTO emptyChartViewDTO(ChartViewDTO view) {
        ChartViewDTO dto = new ChartViewDTO();
        BeanUtils.copyBean(dto, view);
        return dto;
    }

    /**
     * 获取下钻字段在当前维度轴上的排序方式
     *
     * @param xAxis 当前维度轴字段
     * @param field 下钻字段
     * @return 排序方式，未设置时返回空字符串
     */
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

    /**
     * 将图表字段转换为数据集字段结构
     *
     * @param list 图表字段列表
     * @return 数据集字段列表
     */
    private List<DatasetTableFieldDTO> transFields(List<? extends ChartViewFieldBaseDTO> list) {
        return list.stream().map(ele -> {
            DatasetTableFieldDTO dto = new DatasetTableFieldDTO();
            BeanUtils.copyBean(dto, ele);
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 按维度字段的自定义排序配置重排结果集
     *
     * @param xAxis 维度轴字段列表
     * @param data 原始结果集
     * @return 排序后的结果集
     */
    public List<String[]> resultCustomSort(List<ChartViewFieldDTO> xAxis, List<String[]> data) {
        List<String[]> res = new ArrayList<>(data);
        if (xAxis.size() > 0) {
            // 找到对应维度
            for (int i = 0; i < xAxis.size(); i++) {
                ChartViewFieldDTO item = xAxis.get(i);
                if (Strings.CI.equals(item.getSort(), "custom_sort")) {
                    // 获取自定义值与data对应列的结果
                    if (i > 0) {
                        // 首先根据优先级高的字段分类，在每个前置字段相同的组里排序
                        Map<String, List<String[]>> map = new LinkedHashMap<>();
                        for (String[] d : res) {
                            StringBuilder stringBuilder = new StringBuilder();
                            for (int j = 0; j < i; j++) {
                                if (Strings.CI.equals(xAxis.get(j).getSort(), "none")) {
                                    continue;
                                }
                                stringBuilder.append(d[j]);
                            }
                            if (ObjectUtils.isEmpty(map.get(stringBuilder.toString()))) {
                                map.put(stringBuilder.toString(), new ArrayList<>());
                            }
                            map.get(stringBuilder.toString()).add(d);
                        }
                        Iterator<Map.Entry<String, List<String[]>>> iterator = map.entrySet().iterator();
                        List<String[]> list = new ArrayList<>();
                        while (iterator.hasNext()) {
                            Map.Entry<String, List<String[]>> next = iterator.next();
                            list.addAll(customSort(Optional.ofNullable(item.getCustomSort()).orElse(new ArrayList<>()), next.getValue(), i));
                        }
                        res.clear();
                        res.addAll(list);
                    } else {
                        res = customSort(Optional.ofNullable(item.getCustomSort()).orElse(new ArrayList<>()), res, i);
                    }
                }
            }
        }
        return res;
    }

    /**
     * 按指定列的自定义值顺序重排结果集
     *
     * @param custom 自定义排序值列表
     * @param data 原始结果集
     * @param index 参与排序的列下标
     * @return 排序后的结果集
     */
    public List<String[]> customSort(List<String> custom, List<String[]> data, int index) {
        List<String[]> res = new ArrayList<>();

        List<Integer> indexArr = new ArrayList<>();
        List<String[]> joinArr = new ArrayList<>();
        for (int i = 0; i < custom.size(); i++) {
            String ele = custom.get(i);
            for (int j = 0; j < data.size(); j++) {
                String[] d = data.get(j);
                if (Strings.CI.equals(ele, d[index])) {
                    joinArr.add(d);
                    indexArr.add(j);
                }
            }
        }
        // 取得 joinArr 就是两者的交集
        List<Integer> indexArrData = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            indexArrData.add(i);
        }
        List<Integer> indexResult = new ArrayList<>();
        for (int i = 0; i < indexArrData.size(); i++) {
            if (!indexArr.contains(indexArrData.get(i))) {
                indexResult.add(indexArrData.get(i));
            }
        }

        List<String[]> subArr = new ArrayList<>();
        for (int i = 0; i < indexResult.size(); i++) {
            subArr.add(data.get(indexResult.get(i)));
        }
        res.addAll(joinArr);
        res.addAll(subArr);
        return res;
    }

    /**
     * 查询指定图表字段的可选值列表
     *
     * @param view 图表视图配置
     * @param fieldId 字段 ID
     * @param fieldType 字段所在轴类型
     * @return 去重后的字段值列表
     * @throws Exception 字段查询或 SQL 执行异常
     */
    public List<String> getFieldData(ChartViewDTO view, Long fieldId, String fieldType) throws Exception {
        ChartExtRequest requestList = view.getChartExtRequest();
        List<String[]> sqlData = sqlData(view, requestList, fieldId);
        List<ChartViewFieldDTO> fieldList = new ArrayList<>();
        switch (fieldType) {
            case "xAxis" -> fieldList = view.getXAxis();
            case "xAxisExt" -> fieldList = view.getXAxisExt();
            case "extStack" -> fieldList = view.getExtStack();
        }
        DatasetTableFieldDTO field = datasetTableFieldManage.selectById(fieldId);

        List<String> res = new ArrayList<>();
        if (ObjectUtils.isNotEmpty(field) && fieldList.size() > 0) {
            // 找到对应维度
            ChartViewFieldDTO chartViewFieldDTO = null;
            int index = 0;
            int getIndex = 0;
            for (int i = 0; i < fieldList.size(); i++) {
                ChartViewFieldDTO item = fieldList.get(i);
                if (Strings.CI.equals(item.getSort(), "custom_sort")) {// 此处与已有的自定义字段对比
                    chartViewFieldDTO = item;
                    index = i;
                }
                if (Objects.equals(item.getId(), field.getId())) {// 获得当前自定义的字段
                    getIndex = i;
                }
            }
            if (Strings.CI.equals(fieldType, "xAxisExt")) {
                List<ChartViewFieldDTO> xAxis = view.getXAxis();
                index += xAxis.size();
                getIndex += xAxis.size();
            }
            if (Strings.CI.equals(fieldType, "extStack")) {
                int xAxisSize = CollectionUtils.size(view.getXAxis());
                int extSize = CollectionUtils.size(view.getXAxisExt());
                index += xAxisSize + extSize;
                getIndex += xAxisSize + extSize;
            }

            List<String[]> sortResult = resultCustomSort(fieldList, sqlData);
            if (ObjectUtils.isNotEmpty(chartViewFieldDTO) && (getIndex >= index)) {
                // 获取自定义值与data对应列的结果
                List<String[]> strings = customSort(Optional.ofNullable(chartViewFieldDTO.getCustomSort()).orElse(new ArrayList<>()), sortResult, index);
                for (int i = 0; i < strings.size(); i++) {
                    res.add(strings.get(i)[getIndex]);
                }
            } else {
                // 返回请求结果
                for (int i = 0; i < sortResult.size(); i++) {
                    res.add(sortResult.get(i)[getIndex]);
                }
            }
        }
        return res.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 生成字段枚举查询 SQL 并返回原始数据
     *
     * @param view 图表视图配置
     * @param requestList 图表扩展请求
     * @param fieldId 字段 ID
     * @return 字段枚举查询结果
     * @throws Exception 数据集或数据源查询异常
     */
    public List<String[]> sqlData(ChartViewDTO view, ChartExtRequest requestList, Long fieldId) throws Exception {
        if (ObjectUtils.isEmpty(view)) {
            CrestException.throwException(Translator.get("i18n_chart_delete"));
        }

        // 获取图表相关的全部字段
        List<ChartViewFieldDTO> allFields = getAllChartFields(view);

        // 针对分组切换堆叠时会遇到的问题
        if (Strings.CI.equals(view.getType(), "bar-stack") || Strings.CI.equals(view.getType(), "chart-mix-stack")) {
            view.setXAxisExt(new ArrayList<>());
        }

        List<ChartViewFieldDTO> xAxis = new ArrayList<>(view.getXAxis());
        List<ChartViewFieldDTO> xAxisExt = new ArrayList<>(view.getXAxisExt());
        if (Strings.CI.equals(view.getType(), "table-pivot")
                || Strings.CI.contains(view.getType(), "group")
                || ("antv".equalsIgnoreCase(view.getRender()) && "line".equalsIgnoreCase(view.getType()))
                || Strings.CI.equals(view.getType(), "flow-map")
                || Strings.CI.equals(view.getType(), "t-heatmap")
                || Strings.CI.equals(view.getType(), "sankey")
        ) {
            xAxis.addAll(xAxisExt);
        }
        List<ChartViewFieldDTO> yAxis = new ArrayList<>(view.getYAxis());
        if (Strings.CI.contains(view.getType(), "chart-mix")) {
            List<ChartViewFieldDTO> yAxisExt = new ArrayList<>(view.getYAxisExt());
            yAxis.addAll(yAxisExt);
        }
        if (Strings.CI.equals(view.getRender(), "antv") && Strings.CI.equalsAny(view.getType(), "gauge", "liquid")) {
            List<ChartViewFieldDTO> sizeField = getSizeField(view);
            yAxis.addAll(sizeField);
        }
        List<ChartViewFieldDTO> extStack = new ArrayList<>(view.getExtStack());
        List<ChartViewFieldDTO> extBubble = new ArrayList<>(view.getExtBubble());
        FilterTreeObj fieldCustomFilter = view.getCustomFilter();
        List<ChartViewFieldDTO> drill = new ArrayList<>(view.getDrillFields());

        // 获取数据集,需校验权限
        DatasetGroupInfoDTO table = datasetGroupManage.datasetGroupInfoDTOForVisualizationRead(
                view.getTableId(), null, view.getSceneId());
        Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
        List<DataSetRowPermissionsTreeDTO> rowPermissionsTree = permissionManage.getRowPermissionsTree(table.getId(), view.getChartExtRequest().getUser());

        chartFilterTreeService.searchFieldAndSet(fieldCustomFilter);

        if (ObjectUtils.isEmpty(xAxis) && ObjectUtils.isEmpty(yAxis)) {
            return new ArrayList<String[]>();
        }

        switch (view.getType()) {
            case "label":
                yAxis = new ArrayList<>();
                if (ObjectUtils.isEmpty(xAxis)) {
                    return new ArrayList<String[]>();
                }
                break;
            case "indicator":
            case "gauge":
            case "liquid":
                xAxis = new ArrayList<>();
                if (ObjectUtils.isEmpty(yAxis)) {
                    return new ArrayList<String[]>();
                }
                break;
            case "table-info":
                yAxis = new ArrayList<>();
                if (ObjectUtils.isEmpty(xAxis)) {
                    return new ArrayList<String[]>();
                }
                break;
            case "table-normal":
                break;
            case "bar-group":
            case "bar-group-stack":
            case "flow-map":
                break;
            default:
        }

        // 获取dsMap,union sql
        Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(table, null, null, false);
        sqlMap = datasetSyncQueryManage.routeIfSynced(table, sqlMap);
        String sql = (String) sqlMap.get("sql");
        Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
        List<String> dsList = new ArrayList<>();
        for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
            dsList.add(next.getValue().getType());
        }
        boolean needOrder = Utils.isNeedOrder(dsList);
        boolean crossDs = table.getIsCross();
        if (!crossDs) {
            sql = Utils.replaceSchemaAlias(sql, dsMap);
        }

        // 调用数据源的calcite获得data
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDsList(dsMap);
        datasourceRequest.setIsCross(crossDs);
        List<TableFieldWithValue> tableFieldWithValues = (List<TableFieldWithValue>) sqlMap.get("tableFieldWithValues");
        if (CollectionUtils.isNotEmpty(tableFieldWithValues)) {
            datasourceRequest.setTableFieldWithValues(tableFieldWithValues.stream().map(TableFieldWithValue::copy).toList());
        }

        Provider provider;
        if (crossDs) {
            provider = ProviderFactory.getDefaultProvider();
        } else {
            provider = ProviderFactory.getProvider(dsMap.entrySet().iterator().next().getValue().getType());
        }

        List<String[]> data = new ArrayList<>();

        String querySql = null;
        //如果不是插件图表 走原生逻辑
        if (table.getMode() == 0 || table.getMode() == 1) {// 直连或同步缓存
            if (ObjectUtils.isEmpty(dsMap)) {
                CrestException.throwException(Translator.get("i18n_datasource_delete"));
            }
            for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
                DatasourceSchemaDTO ds = next.getValue();
                if (StringUtils.isNotEmpty(ds.getStatus()) && "Error".equalsIgnoreCase(ds.getStatus())) {
                    CrestException.throwException(Translator.get("i18n_invalid_ds"));
                }
            }

            SQLMeta sqlMeta = new SQLMeta();
            Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);
            WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, transFields(allFields), crossDs, dsMap, Utils.getParams(transFields(allFields)), view.getCalParams(), pluginManage);

            if (Strings.CI.equalsAny(view.getType(), "indicator", "gauge", "liquid")) {
                Quota2SQLObj.quota2sqlObj(sqlMeta, yAxis, transFields(allFields), crossDs, dsMap, Utils.getParams(transFields(allFields)), view.getCalParams(), pluginManage);
                querySql = SQLProvider.createQuerySQL(sqlMeta, true, needOrder, view);
            } else if (Strings.CI.contains(view.getType(), "stack")) {
                List<ChartViewFieldDTO> xFields = new ArrayList<>();
                xFields.addAll(xAxis);
                xFields.addAll(extStack);
                Dimension2SQLObj.dimension2sqlObj(sqlMeta, xFields, transFields(allFields), crossDs, dsMap, Utils.getParams(transFields(allFields)), view.getCalParams(), pluginManage);
                Quota2SQLObj.quota2sqlObj(sqlMeta, yAxis, transFields(allFields), crossDs, dsMap, Utils.getParams(transFields(allFields)), view.getCalParams(), pluginManage);
                querySql = SQLProvider.createQuerySQL(sqlMeta, true, needOrder, view);
            } else if (Strings.CI.equals(view.getType(), "multi-scatter")) {
                List<ChartViewFieldDTO> allDimFields = new ArrayList<>();
                allDimFields.addAll(xAxis);
                allDimFields.addAll(yAxis);
                Dimension2SQLObj.dimension2sqlObj(sqlMeta, allDimFields, transFields(allFields), crossDs, dsMap, Utils.getParams(transFields(allFields)), view.getCalParams(), pluginManage);
                querySql = SQLProvider.createQuerySQL(sqlMeta, false, needOrder, view);
            } else if (Strings.CI.contains(view.getType(), "scatter")) {
                List<ChartViewFieldDTO> yFields = new ArrayList<>();
                yFields.addAll(yAxis);
                yFields.addAll(extBubble);
                Dimension2SQLObj.dimension2sqlObj(sqlMeta, xAxis, transFields(allFields), crossDs, dsMap, Utils.getParams(transFields(allFields)), view.getCalParams(), pluginManage);
                Quota2SQLObj.quota2sqlObj(sqlMeta, yFields, transFields(allFields), crossDs, dsMap, Utils.getParams(transFields(allFields)), view.getCalParams(), pluginManage);
                querySql = SQLProvider.createQuerySQL(sqlMeta, true, needOrder, view);
            } else if (Strings.CI.equals("table-info", view.getType())) {
                Dimension2SQLObj.dimension2sqlObj(sqlMeta, xAxis, transFields(allFields), crossDs, dsMap, Utils.getParams(transFields(allFields)), view.getCalParams(), pluginManage);
                querySql = SQLProvider.createQuerySQL(sqlMeta, false, true, view);
            } else {
                Dimension2SQLObj.dimension2sqlObj(sqlMeta, xAxis, transFields(allFields), crossDs, dsMap, Utils.getParams(transFields(allFields)), view.getCalParams(), pluginManage);
                Quota2SQLObj.quota2sqlObj(sqlMeta, yAxis, transFields(allFields), crossDs, dsMap, Utils.getParams(transFields(allFields)), view.getCalParams(), pluginManage);
                querySql = SQLProvider.createQuerySQL(sqlMeta, true, needOrder, view);
            }

            querySql = provider.rebuildSQL(querySql, sqlMeta, crossDs, dsMap);
            datasourceRequest.setQuery(querySql);
            logger.debug("calcite chart get field enum sql: {}", querySql);

            data = (List<String[]>) provider.fetchResultField(datasourceRequest).get("data");
        }
        return data;
    }

    /**
     * 汇总图表使用的数据集维度和指标字段
     *
     * @param view 图表视图配置
     * @return 图表使用的全部有效字段
     */
    private List<ChartViewFieldDTO> getAllChartFields(ChartViewDTO view) {
        // 获取图表相关的全部字段
        Map<String, List<ChartViewFieldDTO>> stringListMap = chartViewManege.listByDQ(view.getTableId(), view.getId(), view);
        List<ChartViewFieldDTO> dimensionList = stringListMap.get("dimensionList");
        List<ChartViewFieldDTO> quotaList = stringListMap.get("quotaList");
        List<ChartViewFieldDTO> allFields = new ArrayList<>();
        allFields.addAll(dimensionList);
        allFields.addAll(quotaList);
        return allFields.stream().filter(ele -> ele.getId() != -1L).collect(Collectors.toList());
    }

    /**
     * 发布仪表板时保存仍被画布引用的图表视图
     *
     * @param checkData 画布引用的图表 ID 串
     * @param sceneId 仪表板场景 ID
     * @param chartViewsInfo 待保存的图表视图映射
     */
    public void saveChartViewFromVisualization(String checkData, Long sceneId, Map<Long, ChartViewDTO> chartViewsInfo) {
        if (!MapUtils.isEmpty(chartViewsInfo)) {
            List<Long> disuseChartIdList = new ArrayList<>();
            chartViewsInfo.forEach((key, chartViewDTO) -> {
                if (checkData.contains(chartViewDTO.getId() + "")) {
                    try {
                        chartViewDTO.setSceneId(sceneId);
                        chartViewManege.save(chartViewDTO);
                    } catch (Exception e) {
                        CrestException.throwException(e);
                    }
                } else {
                    disuseChartIdList.add(chartViewDTO.getId());
                }
            });
            // 阈值告警处理 统一在发布时处理
//            if (CollectionUtils.isNotEmpty(disuseChartIdList)) {
//                chartViewManege.disuse(disuseChartIdList);
//            }
        }
    }

    /**
     * 查询下钻字段的可选值列表
     *
     * @param view 图表视图配置
     * @param fieldId 下钻字段 ID
     * @return 去重后的下钻字段值列表
     * @throws Exception 字段查询或 SQL 执行异常
     */
    public List<String> getDrillFieldData(ChartViewDTO view, Long fieldId) throws Exception {
        List<ChartViewFieldDTO> drillField = view.getDrillFields();
        ChartViewFieldDTO targetField = null;
        for (int i = 0; i < drillField.size(); i++) {
            ChartViewFieldDTO tmp = drillField.get(i);
            if (tmp.getId().equals(fieldId)) {
                targetField = tmp;
                break;
            }
        }
        if (targetField == null) {
            return Collections.emptyList();
        }
        view.setXAxis(Collections.singletonList(targetField));

        List<String[]> sqlData = sqlData(view, view.getChartExtRequest(), fieldId);
        List<String[]> result = customSort(Optional.ofNullable(targetField.getCustomSort()).orElse(new ArrayList<>()), sqlData, 0);
        return result.stream().map(i -> i[0]).distinct().collect(Collectors.toList());
    }

    /**
     * 对图表返回数据中的字段元信息进行编码处理
     *
     * @param chartViewDTO 图表视图配置
     */
    public void encodeData(ChartViewDTO chartViewDTO) {
        if (chartViewDTO.getData() != null) {
            if (chartViewDTO.getType().startsWith("chart-mix")) {
                encodeSourceFields(chartViewDTO.getData().get("left"));
                encodeSourceFields(chartViewDTO.getData().get("right"));
            } else {
                encodeFieldList(chartViewDTO.getData().get("sourceFields"));
            }
        }
    }

    private void encodeSourceFields(Object sourceData) {
        if (sourceData instanceof Map<?, ?> data) {
            encodeFieldList(data.get("sourceFields"));
        }
    }

    private void encodeFieldList(Object sourceFields) {
        if (!(sourceFields instanceof List<?> fields) || CollectionUtils.isEmpty(fields)) {
            return;
        }
        for (Object field : fields) {
            if (!(field instanceof DatasetTableFieldDTO)) {
                return;
            }
        }
        DatasetUtils.listEncode((List<? extends DatasetTableFieldDTO>) fields);
    }
}
