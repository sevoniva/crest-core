package io.crest.chart.manage;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.crest.api.chart.vo.ChartBaseVO;
import io.crest.api.chart.vo.ViewSelectorVO;
import io.crest.chart.dao.auto.entity.CoreChartView;
import io.crest.chart.dao.auto.mapper.CoreChartViewMapper;
import io.crest.chart.dao.ext.entity.ChartBasePO;
import io.crest.chart.dao.ext.mapper.ExtChartViewMapper;
import io.crest.constant.CommonConstants;
import io.crest.dataset.dao.auto.entity.CoreDatasetGroup;
import io.crest.dataset.dao.auto.entity.CoreDatasetTableField;
import io.crest.dataset.dao.auto.mapper.CoreDatasetGroupMapper;
import io.crest.dataset.dao.auto.mapper.CoreDatasetTableFieldMapper;
import io.crest.dataset.manage.DatasetTableFieldManage;
import io.crest.dataset.manage.PermissionManage;
import io.crest.dataset.utils.TableUtils;
import io.crest.engine.constant.ExtFieldConstant;
import io.crest.engine.func.FunctionConstant;
import io.crest.engine.utils.Utils;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.api.PluginManageApi;
import io.crest.extensions.datasource.dto.CalParam;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.FieldGroupDTO;
import io.crest.extensions.datasource.model.SQLObj;
import io.crest.extensions.view.dto.*;
import io.crest.extensions.view.filter.FilterTreeObj;
import io.crest.i18n.Lang;
import io.crest.i18n.Translator;
import io.crest.portal.DataPortalPermissionManage;
import io.crest.utils.BeanUtils;
import io.crest.utils.CrestPermissionUtils;
import io.crest.utils.IDUtils;
import io.crest.utils.JsonUtil;
import io.crest.utils.LogUtil;
import io.crest.visualization.dao.auto.entity.DataVisualizationInfo;
import io.crest.visualization.dao.auto.entity.SnapshotCoreChartView;
import io.crest.visualization.dao.auto.mapper.DataVisualizationInfoMapper;
import io.crest.visualization.dao.auto.mapper.SnapshotCoreChartViewMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 图表视图管理服务，负责视图保存、字段转换、权限校验和图表数据查询
 */
@Component
@SuppressWarnings("unchecked")
public class ChartViewManege {
    @Resource
    private CoreChartViewMapper coreChartViewMapper;
    @Resource
    private SnapshotCoreChartViewMapper snapshotCoreChartViewMapper;
    @Resource
    private DataPortalPermissionManage dataPortalPermissionManage;
    @Resource
    private ChartDataManage chartDataManage;
    @Resource
    private CoreDatasetTableFieldMapper coreDatasetTableFieldMapper;
    @Resource
    private CoreDatasetGroupMapper coreDatasetGroupMapper;
    @Resource
    private PermissionManage permissionManage;

    @Resource
    private DataVisualizationInfoMapper visualizationInfoMapper;

    @Resource
    private ExtChartViewMapper extChartViewMapper;

    @Resource
    private DatasetTableFieldManage datasetTableFieldManage;
    @Resource
    private ChartViewOldDataMergeService chartViewOldDataMergeService;
    @Autowired(required = false)
    private PluginManageApi pluginManage;

    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 保存图表视图草稿，并在写入镜像表前校验标题和访问权限
     */
    @Transactional
    public ChartViewDTO save(ChartViewDTO chartViewDTO) throws Exception {
        if (chartViewDTO.getTitle().length() > 100) {
            CrestException.throwException(Translator.get("i18n_name_limit_100"));
        }
        Long id = chartViewDTO.getId();
        if (id == null) {
            CrestException.throwException(Translator.get("i18n_no_id"));
        }
        SnapshotCoreChartView coreChartView = snapshotCoreChartViewMapper.selectById(id);
        SnapshotCoreChartView record = transDTO2Record(chartViewDTO);
        requireChartAccess(record.getCreateBy(), record.getSceneId(), record.getTableId());
        if (ObjectUtils.isEmpty(coreChartView)) {
            snapshotCoreChartViewMapper.deleteById(record.getId());
            snapshotCoreChartViewMapper.insert(record);
        } else {
            UpdateWrapper<SnapshotCoreChartView> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", record.getId());
            //富文本允许设置空的tableId 这里额外更新一下
            if (record.getTableId() == null) {
                updateWrapper.set("table_id", null);
            }
            snapshotCoreChartViewMapper.update(record, updateWrapper);
        }
        return chartViewDTO;
    }

    /**
     * 从发布表删除指定图表视图
     */
    public void delete(Long id) {
        coreChartViewMapper.deleteById(id);
    }
    /**
     * 预留图表停用入口
     */
    public void disuse(List<Long> chartIdList) {
    }

    //镜像操作发布
    public void publishThreshold(Long resourceId, List<Long> chartIdList) {
    }

    //镜像操作删除
    public void removeThreshold(Long resourceId, String resourceTable) {

    }

    //镜像操作恢复
    public void restoreThreshold(Long resourceId, String resourceTable) {
    }

    /**
     * 删除指定场景下未保留的图表视图
     */
    @Transactional
    public void deleteBySceneId(Long sceneId, List<Long> chartIds) {
        QueryWrapper<CoreChartView> wrapper = new QueryWrapper<>();
        wrapper.eq("scene_id", sceneId);
        wrapper.notIn("id", chartIds);
        coreChartViewMapper.delete(wrapper);
    }

    /**
     * 从发布表或镜像表查询图表详情并校验读取权限
     */
    public ChartViewDTO getDetails(Long id, String resourceTable) {
        CoreChartView coreChartView = null;
        if (CommonConstants.RESOURCE_TABLE.SNAPSHOT.equals(resourceTable)) {
            SnapshotCoreChartView snapshotCoreChartView = snapshotCoreChartViewMapper.selectById(id);
            if (ObjectUtils.isEmpty(snapshotCoreChartView)) {
                return null;
            }
            requireChartReadAccess(snapshotCoreChartView.getCreateBy(), snapshotCoreChartView.getSceneId(), snapshotCoreChartView.getTableId());
            coreChartView = new CoreChartView();
            BeanUtils.copyBean(coreChartView, snapshotCoreChartView);
        } else {
            coreChartView = coreChartViewMapper.selectById(id);
            if (ObjectUtils.isEmpty(coreChartView)) {
                return null;
            }
            requireChartReadAccess(coreChartView.getCreateBy(), coreChartView.getSceneId(), coreChartView.getTableId());
        }
        ChartViewDTO dto = transRecord2DTO(coreChartView);
        return dto;
    }

    /**
     * sceneId 为仪表板或者数据大屏id
     */
    public List<ChartViewDTO> listBySceneId(Long sceneId, String resourceTable) {
        requireSceneReadAccess(sceneId);
        QueryWrapper<CoreChartView> wrapper = new QueryWrapper<>();
        wrapper.eq("scene_id", sceneId);
        List<ChartViewDTO> chartViewDTOS = transChart(extChartViewMapper.selectListCustom(sceneId, resourceTable));
        if (!CollectionUtils.isEmpty(chartViewDTOS)) {
            List<Long> tableIds = chartViewDTOS.stream()
                    .map(ChartViewDTO::getTableId)
                    .filter(tableId -> tableId != null) // 过滤掉空值
                    .distinct()
                    .toList();
            if (!CollectionUtils.isEmpty(tableIds)) {
                QueryWrapper<CoreDatasetTableField> wp = new QueryWrapper<>();
                wp.in("dataset_group_id", tableIds);
                List<CoreDatasetTableField> coreDatasetTableFields = coreDatasetTableFieldMapper.selectList(wp);
                Map<Long, List<CoreDatasetTableField>> groupedByTableId = coreDatasetTableFields.stream()
                        .collect(Collectors.groupingBy(CoreDatasetTableField::getDatasetGroupId));
                if (chartViewDTOS.size() < 10) {
                    chartViewDTOS.forEach(dto -> {
                        if (dto.getTableId() != null) {
                            dto.setCalParams(Utils.getParams(datasetTableFieldManage.transDTO(groupedByTableId.get(dto.getTableId()))));
                        }
                    });
                } else {
                    ExecutorService executor = Executors.newFixedThreadPool(10);
                    try {
                        // 超过10个图表要处理启用多线程处理
                        CountDownLatch latch = new CountDownLatch(chartViewDTOS.size());
                        chartViewDTOS.forEach(dto -> {
                            executor.submit(() -> {
                                try {
                                    if (dto.getTableId() != null) {
                                        dto.setCalParams(Utils.getParams(datasetTableFieldManage.transDTO(groupedByTableId.get(dto.getTableId()))));
                                    }
                                } finally {
                                    latch.countDown(); // 减少计数器
                                }
                            });
                        });

                        // 等待所有线程完成
                        boolean completedInTime = latch.await(200, TimeUnit.SECONDS);
                        if (!completedInTime) {
                            throw new InterruptedException("Tasks did not complete within 200 seconds");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LogUtil.error(e);
                    } finally {
                        executor.shutdown(); // 确保线程池关闭
                    }
                }

            }
        }
        return chartViewDTOS;
    }

    /**
     * 将图表记录列表转换为前端使用的视图 DTO 列表
     */
    public List<ChartViewDTO> transChart(List<CoreChartView> list) {
        if (ObjectUtils.isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream().map(ele -> {
            return transRecord2DTO(ele);
        }).collect(Collectors.toList());
    }

    /**
     * 查询图表详情并计算图表数据，阈值场景使用全量结果参数
     */
    public ChartViewDTO getChart(Long id, String resourceTable, boolean forThreshold) throws Exception {
        ChartViewDTO details = getDetails(id, resourceTable);
        if (details == null) {
            return null;
        }
        if (forThreshold) {
            ChartExtRequest chartExtRequest = details.getChartExtRequest();
            if (chartExtRequest == null) {
                chartExtRequest = new ChartExtRequest();
                chartExtRequest.setResultMode("all");
                chartExtRequest.setResultCount(1000);
                chartExtRequest.setGoPage(1L);
                chartExtRequest.setPageSize(50000L);
            }
            details.setChartExtRequest(chartExtRequest);
        }
        return chartDataManage.calcData(details);
    }

    /**
     * 查询并计算普通图表数据
     */
    public ChartViewDTO getChart(Long id, String resourceTable) throws Exception {
        return getChart(id, resourceTable, false);
    }

    /**
     * 查询数据集字段并按维度、指标分组返回图表可选字段
     */
    public Map<String, List<ChartViewFieldDTO>> listByDQ(Long id, Long chartId, ChartViewDTO chartViewDTO) {
        boolean portalVisualizationRead = chartViewDTO != null
                && dataPortalPermissionManage.canReadPublishedVisualization(chartViewDTO.getSceneId(), null);
        if (!portalVisualizationRead) {
            requireDatasetAccess(id);
        }
        if (chartId != null) {
            CoreChartView chart = coreChartViewMapper.selectById(chartId);
            if (chart != null) {
                if (portalVisualizationRead) {
                    requireChartReadAccess(chart.getCreateBy(), chart.getSceneId(), chart.getTableId());
                } else {
                    requireChartAccess(chart.getCreateBy(), chart.getSceneId(), chart.getTableId());
                }
            }
        }
        QueryWrapper<CoreDatasetTableField> wrapper = new QueryWrapper<>();
        wrapper.eq("dataset_group_id", id);
        wrapper.eq("checked", true);
        wrapper.isNull("chart_id");

        TypeReference<List<CalParam>> typeToken = new TypeReference<>() {
        };
        TypeReference<List<FieldGroupDTO>> groupTokenType = new TypeReference<>() {
        };
        List<CoreDatasetTableField> fields = coreDatasetTableFieldMapper.selectList(wrapper);
        List<DatasetTableFieldDTO> collect = fields.stream().map(ele -> {
            DatasetTableFieldDTO dto = new DatasetTableFieldDTO();
            BeanUtils.copyBean(dto, ele);
            dto.setParams(JsonUtil.parseList(ele.getParams(), typeToken));
            dto.setGroupList(JsonUtil.parseList(ele.getGroupList(), groupTokenType));
            return dto;
        }).collect(Collectors.toList());
        // filter column disable field
        Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
        List<DatasetTableFieldDTO> datasetTableFieldDTOS = permissionManage.filterColumnPermissions(collect, desensitizationList, id, null);
        datasetTableFieldDTOS.forEach(ele -> ele.setDesensitized(desensitizationList.containsKey(ele.getEngineFieldName())));
        datasetTableFieldDTOS.add(createCountField(id));
        List<ChartViewFieldDTO> list = transFieldDTO(datasetTableFieldDTOS);

        // 获取图表计算字段
        wrapper.clear();
        wrapper.eq("chart_id", chartId);
        List<DatasetTableFieldDTO> chartFields = coreDatasetTableFieldMapper.selectList(wrapper).stream().map(ele -> {
            DatasetTableFieldDTO dto = new DatasetTableFieldDTO();
            BeanUtils.copyBean(dto, ele);
            dto.setGroupList(JsonUtil.parseList(ele.getGroupList(), groupTokenType));
            return dto;
        }).collect(Collectors.toList());
        list.addAll(transFieldDTO(chartFields));

        // 获取list中的聚合函数，将字段的summary设置成空
        SQLObj tableObj = new SQLObj();
        tableObj.setTableAlias("");

        for (ChartViewFieldDTO ele : list) {
            if (Objects.equals(ele.getExtField(), ExtFieldConstant.EXT_CALC)) {
                List<DatasetTableFieldDTO> f = list.stream().map(e -> {
                    DatasetTableFieldDTO dto = new DatasetTableFieldDTO();
                    BeanUtils.copyBean(dto, e);
                    return dto;
                }).collect(Collectors.toList());
                String originField = Utils.calcFieldRegex(ele, tableObj, f, true, null, Utils.mergeParam(Utils.getParams(f), null), pluginManage);
                for (String func : FunctionConstant.AGG_FUNC) {
                    if (Utils.matchFunction(func, originField)) {
                        ele.setSummary("");
                        ele.setAgg(true);
                        break;
                    }
                }
            }
        }

        List<ChartViewFieldDTO> dimensionList = list.stream().filter(ele -> Strings.CI.equals(ele.getGroupType(), "d")).collect(Collectors.toList());
        List<ChartViewFieldDTO> quotaList = list.stream().filter(ele -> Strings.CI.equals(ele.getGroupType(), "q")).collect(Collectors.toList());

        Map<String, List<ChartViewFieldDTO>> map = new LinkedHashMap<>();
        map.put("dimensionList", dimensionList);
        map.put("quotaList", quotaList);
        return map;
    }

    /**
     * 将数据集字段复制为指定图表的计算字段
     */
    public void copyField(Long id, Long chartId) {
        CoreDatasetTableField coreDatasetTableField = coreDatasetTableFieldMapper.selectById(id);
        if (coreDatasetTableField != null) {
            requireDatasetAccess(coreDatasetTableField.getDatasetGroupId());
        }
        CoreChartView chart = coreChartViewMapper.selectById(chartId);
        if (chart != null) {
            requireChartAccess(chart.getCreateBy(), chart.getSceneId(), chart.getTableId());
        }
        QueryWrapper<CoreDatasetTableField> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("dataset_group_id", coreDatasetTableField.getDatasetGroupId());
        List<CoreDatasetTableField> coreDatasetTableFields = coreDatasetTableFieldMapper.selectList(queryWrapper);
        HashMap<String, String> map = new HashMap<>();
        for (CoreDatasetTableField ele : coreDatasetTableFields) {
            map.put(ele.getName(), ele.getName());
        }
        newName(map, coreDatasetTableField, coreDatasetTableField.getName());
        coreDatasetTableField.setChartId(chartId);
        coreDatasetTableField.setExtField(2);
        coreDatasetTableField.setOriginName("[" + id + "]");
        coreDatasetTableField.setId(IDUtils.snowID());
        coreDatasetTableField.setEngineFieldName(TableUtils.fieldNameShort(coreDatasetTableField.getId() + "_" + coreDatasetTableField.getOriginName()));
        coreDatasetTableField.setFieldShortName(coreDatasetTableField.getEngineFieldName());
        coreDatasetTableFieldMapper.insert(coreDatasetTableField);
    }

    /**
     * 为复制字段递归生成不重复名称
     */
    private void newName(HashMap<String, String> map, CoreDatasetTableField coreDatasetTableField, String name) {
        name = name + "_copy";
        if (map.containsKey(name)) {
            newName(map, coreDatasetTableField, name);
        } else {
            coreDatasetTableField.setName(name);
        }
    }

    /**
     * 删除指定数据集字段并校验数据集权限
     */
    public void fieldRemoval(Long id) {
        CoreDatasetTableField field = coreDatasetTableFieldMapper.selectById(id);
        if (field != null) {
            requireDatasetAccess(field.getDatasetGroupId());
        }
        coreDatasetTableFieldMapper.deleteById(id);
    }

    /**
     * 删除指定图表下的全部计算字段
     */
    public void chartFieldRemovalId(Long chartId) {
        CoreChartView chart = coreChartViewMapper.selectById(chartId);
        if (chart != null) {
            requireChartAccess(chart.getCreateBy(), chart.getSceneId(), chart.getTableId());
        }
        QueryWrapper<CoreDatasetTableField> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("chart_id", chartId);
        coreDatasetTableFieldMapper.delete(queryWrapper);
    }

    /**
     * 校验图表写权限，允许创建者、场景可管理者或数据集可管理者访问
     */
    private void requireChartAccess(String createBy, Long sceneId, Long tableId) {
        if (CrestPermissionUtils.canAccessCreator(createBy)) {
            return;
        }
        if (sceneId != null && canAccessScene(sceneId)) {
            return;
        }
        if (tableId != null && canAccessDataset(tableId)) {
            return;
        }
        CrestPermissionUtils.requireCreator(createBy);
    }

    /**
     * 校验图表读权限，包含门户已发布资源的读取场景
     */
    private void requireChartReadAccess(String createBy, Long sceneId, Long tableId) {
        if (CrestPermissionUtils.canAccessCreator(createBy)) {
            return;
        }
        if (sceneId != null && dataPortalPermissionManage.canReadPublishedVisualization(sceneId, null)) {
            return;
        }
        if (sceneId != null && canAccessScene(sceneId)) {
            return;
        }
        if (tableId != null && canAccessDataset(tableId)) {
            return;
        }
        CrestPermissionUtils.requireCreator(createBy);
    }

    /**
     * 校验可视化场景管理权限
     */
    private void requireSceneAccess(Long sceneId) {
        if (sceneId == null || canAccessScene(sceneId)) {
            return;
        }
        CrestPermissionUtils.requireCreator(null);
    }

    /**
     * 校验可视化场景读取权限
     */
    private void requireSceneReadAccess(Long sceneId) {
        if (
                sceneId == null
                        || canAccessScene(sceneId)
                        || dataPortalPermissionManage.canReadPublishedVisualization(sceneId, null)
        ) {
            return;
        }
        CrestPermissionUtils.requireCreator(null);
    }

    /**
     * 判断当前用户是否可访问指定可视化场景
     */
    private boolean canAccessScene(Long sceneId) {
        DataVisualizationInfo scene = visualizationInfoMapper.selectById(sceneId);
        return scene != null && CrestPermissionUtils.canAccessCreator(scene.getCreateBy());
    }

    /**
     * 校验数据集管理权限
     */
    private void requireDatasetAccess(Long datasetId) {
        if (datasetId == null || canAccessDataset(datasetId)) {
            return;
        }
        CrestPermissionUtils.requireCreator(null);
    }

    /**
     * 判断当前用户是否可访问指定数据集
     */
    private boolean canAccessDataset(Long datasetId) {
        CoreDatasetGroup dataset = coreDatasetGroupMapper.selectById(datasetId);
        return dataset != null && CrestPermissionUtils.canAccessCreator(dataset.getCreateBy());
    }

    /**
     * 查询图表基础信息并解析字段配置
     */
    public ChartBaseVO chartBaseInfo(Long id, String resourceTable) {
        ChartBasePO po = extChartViewMapper.queryChart(id, resourceTable);
        if (ObjectUtils.isEmpty(po)) return null;
        ChartBaseVO vo = BeanUtils.copyBean(new ChartBaseVO(), po);
        TypeReference<List<ChartViewFieldDTO>> tokenType = new TypeReference<>() {
        };
        vo.setXAxis(JsonUtil.parseList(po.getXAxis(), tokenType));
        vo.setXAxisExt(JsonUtil.parseList(po.getXAxisExt(), tokenType));
        vo.setYAxis(JsonUtil.parseList(po.getYAxis(), tokenType));
        vo.setYAxisExt(JsonUtil.parseList(po.getYAxisExt(), tokenType));
        vo.setExtStack(JsonUtil.parseList(po.getExtStack(), tokenType));
        vo.setExtBubble(JsonUtil.parseList(po.getExtBubble(), tokenType));
        vo.setFlowMapStartName(JsonUtil.parseList(po.getFlowMapStartName(), tokenType));
        vo.setFlowMapEndName(JsonUtil.parseList(po.getFlowMapEndName(), tokenType));
        if (StringUtils.isBlank(po.getExtColor()) || Strings.CS.equals("null", po.getExtColor())) {
            vo.setExtColor(new ArrayList<>());
        } else {
            vo.setExtColor(JsonUtil.parseList(po.getExtColor(), tokenType));
        }
        vo.setExtLabel(JsonUtil.parseList(po.getExtLabel(), tokenType));
        vo.setExtTooltip(JsonUtil.parseList(po.getExtTooltip(), tokenType));
        return vo;
    }

    /**
     * 创建记录数指标字段
     */
    public DatasetTableFieldDTO createCountField(Long id) {
        DatasetTableFieldDTO dto = new DatasetTableFieldDTO();
        dto.setId(-1L);
        dto.setDatasetGroupId(id);
        dto.setOriginName("*");
        dto.setName("记录数*");
        dto.setEngineFieldName("*");
        dto.setType("INT");
        dto.setChecked(true);
        dto.setColumnIndex(999);
        dto.setFieldType(2);
        dto.setExtField(1);
        dto.setGroupType("q");
        return dto;
    }

    /**
     * 将数据集字段转换为图表字段默认配置
     */
    public List<ChartViewFieldDTO> transFieldDTO(List<DatasetTableFieldDTO> list) {
        return list.stream().map(ele -> {
            ChartViewFieldDTO dto = new ChartViewFieldDTO();
            if (ele == null) return null;
            BeanUtils.copyBean(dto, ele);
            dto.setDateStyle("y_M_d");
            dto.setDatePattern("date_sub");
            dto.setDateShowFormat("y_M_d");
            dto.setChartType("bar");

            if (dto.getId() == -1L || dto.getFieldType() == 0 || dto.getFieldType() == 1 || dto.getFieldType() == 7) {
                dto.setSummary("count");
            } else {
                dto.setSummary("sum");
            }

            ChartFieldCompareDTO chartFieldCompareDTO = new ChartFieldCompareDTO();
            chartFieldCompareDTO.setType("none");
            dto.setCompareCalc(chartFieldCompareDTO);

            dto.setFormatterCfg(new FormatterCfgDTO().setUnitLanguage(Lang.isChinese() ? "ch" : "en"));

            dto.setSort("none");
            dto.setFilter(Collections.emptyList());
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 将图表 DTO 序列化为镜像表记录
     */
    public SnapshotCoreChartView transDTO2Record(ChartViewDTO dto) throws Exception {
        SnapshotCoreChartView record = new SnapshotCoreChartView();
        BeanUtils.copyBean(record, dto);

        record.setxAxis(objectMapper.writeValueAsString(dto.getXAxis()));
        record.setxAxisExt(objectMapper.writeValueAsString(dto.getXAxisExt()));
        record.setyAxis(objectMapper.writeValueAsString(dto.getYAxis()));
        record.setyAxisExt(objectMapper.writeValueAsString(dto.getYAxisExt()));
        record.setExtStack(objectMapper.writeValueAsString(dto.getExtStack()));
        record.setExtBubble(objectMapper.writeValueAsString(dto.getExtBubble()));
        record.setExtLabel(objectMapper.writeValueAsString(dto.getExtLabel()));
        record.setExtTooltip(objectMapper.writeValueAsString(dto.getExtTooltip()));
        record.setCustomAttr(objectMapper.writeValueAsString(dto.getCustomAttr()));
        if (dto.getCustomAttrMobile() != null) {
            record.setCustomAttrMobile(objectMapper.writeValueAsString(dto.getCustomAttrMobile()));
        }
        record.setCustomStyle(objectMapper.writeValueAsString(dto.getCustomStyle()));
        if (dto.getCustomAttrMobile() != null) {
            record.setCustomStyleMobile(objectMapper.writeValueAsString(dto.getCustomStyleMobile()));
        }
        record.setSenior(objectMapper.writeValueAsString(dto.getSenior()));
        record.setDrillFields(objectMapper.writeValueAsString(dto.getDrillFields()));
        record.setCustomFilter(objectMapper.writeValueAsString(dto.getCustomFilter()));
        record.setViewFields(objectMapper.writeValueAsString(dto.getViewFields()));
        record.setFlowMapStartName(objectMapper.writeValueAsString(dto.getFlowMapStartName()));
        record.setFlowMapEndName(objectMapper.writeValueAsString(dto.getFlowMapEndName()));
        record.setExtColor(objectMapper.writeValueAsString(dto.getExtColor()));
        record.setSortPriority(objectMapper.writeValueAsString(dto.getSortPriority()));
        return record;
    }

    /**
     * 将图表记录反序列化为前端 DTO
     */
    public ChartViewDTO transRecord2DTO(CoreChartView record) {
        ChartViewDTO dto = new ChartViewDTO();
        BeanUtils.copyBean(dto, record);

        TypeReference<List<ChartViewFieldDTO>> tokenType = new TypeReference<>() {
        };

        dto.setXAxis(JsonUtil.parseList(record.getxAxis(), tokenType));
        dto.setXAxisExt(JsonUtil.parseList(record.getxAxisExt(), tokenType));
        dto.setYAxis(JsonUtil.parseList(record.getyAxis(), tokenType));
        dto.setYAxisExt(JsonUtil.parseList(record.getyAxisExt(), tokenType));
        dto.setExtStack(JsonUtil.parseList(record.getExtStack(), tokenType));
        dto.setExtBubble(JsonUtil.parseList(record.getExtBubble(), tokenType));
        dto.setExtLabel(JsonUtil.parseList(record.getExtLabel(), tokenType));
        dto.setExtTooltip(JsonUtil.parseList(record.getExtTooltip(), tokenType));
        dto.setCustomAttr(JsonUtil.parse(record.getCustomAttr(), Map.class));
        if (record.getCustomAttrMobile() != null) {
            dto.setCustomAttrMobile(JsonUtil.parse(record.getCustomAttrMobile(), Map.class));
        }
        dto.setCustomStyle(JsonUtil.parse(record.getCustomStyle(), Map.class));
        if (record.getCustomStyleMobile() != null) {
            dto.setCustomStyleMobile(JsonUtil.parse(record.getCustomStyleMobile(), Map.class));
        }
        dto.setSenior(JsonUtil.parse(record.getSenior(), Map.class));
        dto.setDrillFields(JsonUtil.parseList(record.getDrillFields(), tokenType));
        dto.setCustomFilter(parseCustomFilter(record.getCustomFilter()));
        dto.setViewFields(JsonUtil.parseList(record.getViewFields(), tokenType));
        dto.setFlowMapStartName(JsonUtil.parseList(record.getFlowMapStartName(), tokenType));
        dto.setFlowMapEndName(JsonUtil.parseList(record.getFlowMapEndName(), tokenType));
        dto.setExtColor(JsonUtil.parseList(record.getExtColor(), tokenType));
        dto.setSortPriority(JsonUtil.parseList(record.getSortPriority(), new TypeReference<List<SortAxis>>() {
        }));

        return dto;

    }

    /**
     * 解析图表自定义过滤条件并兼容旧数组格式
     */
    private FilterTreeObj parseCustomFilter(String customFilter) {
        if (StringUtils.isBlank(customFilter)) {
            return null;
        }
        String trimmed = customFilter.trim();
        if (trimmed.startsWith("[")) {
            TypeReference<List<ChartFieldCustomFilterDTO>> tokenType = new TypeReference<>() {
            };
            List<ChartFieldCustomFilterDTO> legacyFilters = JsonUtil.parseList(trimmed, tokenType);
            if (CollectionUtils.isEmpty(legacyFilters)) {
                return null;
            }
            return chartViewOldDataMergeService.transArr2Obj(legacyFilters);
        }
        return JsonUtil.parseObject(trimmed, FilterTreeObj.class);
    }

    /**
     * 判断两个图表视图是否来自同一个数据集
     */
    public String checkSameDataSet(String viewIdSource, String viewIdTarget) {
        QueryWrapper<CoreChartView> wrapper = new QueryWrapper<>();
        wrapper.select("distinct table_id");
        wrapper.in("id", Arrays.asList(viewIdSource, viewIdTarget));
        coreChartViewMapper.selectCount(wrapper);
        if (coreChartViewMapper.selectCount(wrapper) == 1) {
            return "YES";
        } else {
            return "NO";
        }

    }

    /**
     * 查询资源下仍被组件引用的视图选项
     */
    public List<ViewSelectorVO> viewOption(Long resourceId) {
        List<ViewSelectorVO> result = extChartViewMapper.queryViewOption(resourceId);
        DataVisualizationInfo dvInfo = visualizationInfoMapper.selectById(resourceId);
        if (dvInfo != null && !CollectionUtils.isEmpty(result)) {
            String componentData = dvInfo.getComponentData();
            return result.stream().filter(item -> componentData.indexOf(String.valueOf(item.getId())) > 0).toList();
        } else {
            return result;
        }
    }

    /**
     * 查询指定视图的上下文图表信息
     */
    public ChartViewDTO findChartViewAround(String viewId) {
        return extChartViewMapper.findChartViewAround(viewId);
    }
}
