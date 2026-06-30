package io.crest.exportCenter.manage;


import com.fasterxml.jackson.core.type.TypeReference;
import io.crest.api.chart.dto.ChartSortFieldDTO;
import io.crest.api.chart.dto.ViewDetailField;
import io.crest.api.chart.request.ChartExcelRequest;
import io.crest.api.chart.request.ChartExcelRequestInner;
import io.crest.api.dataset.dto.DataSetExportRequest;
import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.api.dataset.union.UnionDTO;
import io.crest.api.permissions.dataset.dto.DataSetRowPermissionsTreeDTO;
import io.crest.api.permissions.user.vo.UserFormVO;
import io.crest.auth.bo.TokenUserBO;
import io.crest.chart.dao.auto.mapper.CoreChartViewMapper;
import io.crest.chart.server.ChartDataServer;
import io.crest.commons.utils.ExcelWatermarkUtils;
import io.crest.constant.FieldTypeConstants;
import io.crest.constant.LogOT;
import io.crest.constant.LogST;
import io.crest.dataset.dao.auto.entity.CoreDatasetGroup;
import io.crest.dataset.dao.auto.mapper.CoreDatasetGroupMapper;
import io.crest.dataset.manage.*;
import io.crest.datasource.utils.DatasourceUtils;
import io.crest.engine.sql.SQLProvider;
import io.crest.engine.trans.*;
import io.crest.engine.utils.Utils;
import io.crest.exception.CrestException;
import io.crest.exportCenter.dao.auto.entity.CoreExportTask;
import io.crest.exportCenter.dao.auto.mapper.CoreExportTaskMapper;
import io.crest.exportCenter.util.ExportCenterUtils;
import io.crest.extensions.datasource.api.PluginManageApi;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasourceRequest;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import io.crest.extensions.datasource.factory.ProviderFactory;
import io.crest.extensions.datasource.model.SQLMeta;
import io.crest.extensions.datasource.provider.Provider;
import io.crest.extensions.view.dto.ChartViewDTO;
import io.crest.extensions.view.dto.ChartViewFieldDTO;
import io.crest.extensions.view.dto.ColumnPermissionItem;
import io.crest.extensions.view.dto.DatasetRowPermissionsTreeObj;
import io.crest.i18n.Translator;
import io.crest.log.CrestAudit;
import io.crest.model.ExportTaskDTO;
import io.crest.runtime.CrestRuntimeRole;
import io.crest.storage.StorageService;
import io.crest.substitute.permissions.auth.PlatformPermissionManage;
import io.crest.utils.*;
import io.crest.visualization.dao.auto.entity.VisualizationWatermark;
import io.crest.visualization.dao.auto.mapper.VisualizationWatermarkMapper;
import io.crest.visualization.dao.ext.mapper.ExtDataVisualizationMapper;
import io.crest.visualization.dto.WatermarkContentDTO;
import io.crest.websocket.WsMessage;
import io.crest.websocket.WsService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Transactional(rollbackFor = Exception.class)
@SuppressWarnings({"unchecked", "rawtypes"})
// 管理导出任务调度、Excel 文件生成、文件落盘和下载响应。
public class ExportCenterDownLoadManage {
    @Resource
    private CoreExportTaskMapper exportTaskMapper;
    @Resource
    private Environment environment;
    @Resource
    private CoreChartViewMapper coreChartViewMapper;
    @Resource
    private PermissionManage permissionManage;
    @Resource
    private DatasetGroupManage datasetGroupManage;
    @Autowired
    private WsService wsService;
    @Autowired(required = false)
    private PluginManageApi pluginManage;
    @Value("${crest.export.core.size:10}")
    private int core;
    @Value("${crest.export.max.size:10}")
    private int max;

    @Value("${crest.path.exportData:/opt/crest/data/exportData/}")
    private String exportData_path;
    @Resource
    private VisualizationWatermarkMapper watermarkMapper;
    @Resource
    private ExtDataVisualizationMapper visualizationMapper;
    @Value("${crest.export.page.size:50000}")
    private Integer extractPageSize;
    static private List<String> STATUS = Arrays.asList("SUCCESS", "FAILED", "PENDING", "IN_PROGRESS", "ALL");
    private ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    private int keepAliveSeconds = 600;
    private Map<String, Future> Running_Task = new ConcurrentHashMap<>();
    @Resource
    private ChartDataServer chartDataServer;
    @Resource
    private CoreDatasetGroupMapper coreDatasetGroupMapper;
    @Resource
    private DatasetSQLManage datasetSQLManage;
    @Resource
    private DatasetTableFieldManage datasetTableFieldManage;
    @Resource
    private DatasetDataManage datasetDataManage;
    @Resource
    private PlatformPermissionManage platformPermissionManage;
    @Resource
    private StorageService storageService;
    private final Long sheetLimit = 1000000L;

    // 初始化导出任务线程池，线程数和最大并发由导出配置控制。
    @PostConstruct
    public void init() {
        scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(core);
        scheduledThreadPoolExecutor.setKeepAliveTime(keepAliveSeconds, TimeUnit.SECONDS);
        scheduledThreadPoolExecutor.setMaximumPoolSize(max);
    }

    // 定时检查已完成的导出任务，并通过 WebSocket 推送任务状态。
    @Scheduled(fixedRate = 5000)
    public void checkRunningTask() {
        if (environment != null && !CrestRuntimeRole.from(environment).runsWorker()) {
            return;
        }
        Iterator<Map.Entry<String, Future>> iterator = Running_Task.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Future> entry = iterator.next();
            if (entry.getValue().isDone()) {
                iterator.remove();
                notifyExportTaskFinishedSafely(entry.getKey());
            }
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean executeQueuedTask(String taskId, String workerId) {
        CoreExportTask exportTask = exportTaskMapper.selectById(taskId);
        if (exportTask == null) {
            return true;
        }
        if (!"PENDING".equalsIgnoreCase(exportTask.getExportStatus())) {
            return true;
        }
        long now = System.currentTimeMillis();
        int claimed = exportTaskMapper.claimPendingTask(taskId, workerId, hostName(), now);
        if (claimed <= 0) {
            return true;
        }

        TokenUserBO previousUser = AuthUtils.getUser();
        try {
            exportTask = exportTaskMapper.selectById(taskId);
            AuthUtils.setUser(new TokenUserBO(exportTask.getUserId(), platformPermissionManage.defaultOrgId(exportTask.getUserId())));
            Future future = submitQueuedTask(exportTask);
            Running_Task.put(exportTask.getId(), future);
            future.get();
            notifyExportTaskFinishedSafely(exportTask.getId());
            return true;
        } catch (Exception e) {
            markQueuedTaskFailed(taskId, e.getMessage());
            LogUtil.error("Queued export task failed: " + taskId, e);
            return true;
        } finally {
            Running_Task.remove(taskId);
            if (previousUser == null) {
                AuthUtils.remove();
            } else {
                AuthUtils.setUser(previousUser);
            }
        }
    }

    private void notifyExportTaskFinishedSafely(String taskId) {
        try {
            notifyExportTaskFinished(taskId);
        } catch (Exception e) {
            LogUtil.warn("Export task notification skipped: " + taskId + ", "
                    + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    private Future submitQueuedTask(CoreExportTask exportTask) {
        if ("dataset".equalsIgnoreCase(exportTask.getExportFromType())) {
            DataSetExportRequest request = JsonUtil.parseObject(exportTask.getParams(), DataSetExportRequest.class);
            return submitDatasetTask(exportTask, request);
        }
        ChartExcelRequest request = JsonUtil.parseObject(exportTask.getParams(), ChartExcelRequest.class);
        return submitViewTask(exportTask, request);
    }

    private void notifyExportTaskFinished(String taskId) {
        CoreExportTask exportTask = exportTaskMapper.selectById(taskId);
        if (exportTask == null) {
            return;
        }
        ExportTaskDTO exportTaskDTO = new ExportTaskDTO();
        BeanUtils.copyBean(exportTaskDTO, exportTask);
        setExportFromName(exportTaskDTO);
        WsMessage message = new WsMessage(exportTask.getUserId(), "/task-export-topic", exportTaskDTO);
        wsService.releaseMessage(message);
    }

    private void markQueuedTaskFailed(String taskId, String message) {
        CoreExportTask failed = new CoreExportTask();
        failed.setId(taskId);
        failed.setExportStatus("FAILED");
        failed.setExportProgress("100");
        failed.setMsg(message);
        failed.setLastError(message);
        failed.setHeartbeatTime(System.currentTimeMillis());
        exportTaskMapper.updateById(failed);
    }

    // 回填导出来源名称，前端任务列表需要展示图表或数据集名称。
    private void setExportFromName(ExportTaskDTO exportTaskDTO) {
        if (exportTaskDTO.getExportFromType().equalsIgnoreCase("chart")) {
            exportTaskDTO.setExportFromName(coreChartViewMapper.selectById(exportTaskDTO.getExportFrom()).getTitle());
        }
        if (exportTaskDTO.getExportFromType().equalsIgnoreCase("dataset")) {
            exportTaskDTO.setExportFromName(coreDatasetGroupMapper.selectById(exportTaskDTO.getExportFrom()).getName());
        }
    }

    // 异步启动数据集导出任务，任务状态、进度和文件信息都写回导出任务表。
    @CrestAudit(id = "#p0.exportFrom", ot = LogOT.EXPORT, st = LogST.DATASET)
    public void startDatasetTask(CoreExportTask exportTask, DataSetExportRequest request) {
        Future future = submitDatasetTask(exportTask, request);
        Running_Task.put(exportTask.getId(), future);
    }

    private Future submitDatasetTask(CoreExportTask exportTask, DataSetExportRequest request) {
        prepareTaskDirectory(exportTask.getId());
        File exportFile = storageService.resolve(exportData_path, exportTask.getId(), exportTask.getId() + ".xlsx");

        TokenUserBO tokenUserBO = AuthUtils.getUser();
        Future future = scheduledThreadPoolExecutor.submit(() -> {
            // 导出在线程池中执行，需要恢复提交任务时的用户上下文。
            AuthUtils.setUser(tokenUserBO);
            try {
                exportTask.setExportStatus("IN_PROGRESS");
                exportTask.setMsg(null);
                exportTask.setFileSize(null);
                exportTask.setFileSizeUnit(null);
                exportTask.setHeartbeatTime(System.currentTimeMillis());
                exportTaskMapper.updateById(exportTask);
                CoreDatasetGroup coreDatasetGroup = coreDatasetGroupMapper.selectById(exportTask.getExportFrom());
                if (coreDatasetGroup == null) {
                    throw new Exception("Not found dataset group: " + exportTask.getExportFrom());
                }
                DatasetGroupInfoDTO dto = new DatasetGroupInfoDTO();
                BeanUtils.copyBean(dto, coreDatasetGroup);
                dto.setUnionSql(null);
                List<UnionDTO> unionDTOList = JsonUtil.parseList(coreDatasetGroup.getInfo(), new TypeReference<>() {
                });
                dto.setUnion(unionDTOList);
                List<DatasetTableFieldDTO> dsFields = datasetTableFieldManage.selectByDatasetGroupId(Long.valueOf(exportTask.getExportFrom()));
                List<DatasetTableFieldDTO> allFields = dsFields.stream().map(ele -> {
                    DatasetTableFieldDTO datasetTableFieldDTO = new DatasetTableFieldDTO();
                    BeanUtils.copyBean(datasetTableFieldDTO, ele);
                    datasetTableFieldDTO.setFieldShortName(ele.getEngineFieldName());
                    return datasetTableFieldDTO;
                }).collect(Collectors.toList());
                DatasetGroupInfoDTO datasetGroupInfoDTO = datasetGroupManage.datasetGroupInfoDTO(request.getId(), null);
                Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, null);
                dto.setIsCross(Boolean.TRUE.equals(datasetGroupInfoDTO.getIsCross()));
                if (sqlMap == null || StringUtils.isBlank((String) sqlMap.get("sql"))) {
                    CrestException.throwException("数据集配置不完整，无法导出");
                }
                String sql = (String) sqlMap.get("sql");
                if (ObjectUtils.isEmpty(allFields)) {
                    CrestException.throwException(Translator.get("i18n_no_fields"));
                }
                Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
                // 导出必须复用列权限和脱敏规则，避免绕过在线数据查看权限。
                allFields = permissionManage.filterColumnPermissions(allFields, desensitizationList, dto.getId(), null);
                if (ObjectUtils.isEmpty(allFields)) {
                    CrestException.throwException(Translator.get("i18n_no_column_permission"));
                }
                dto.setAllFields(allFields);
                datasetDataManage.buildFieldName(sqlMap, allFields);
                Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
                DatasourceUtils.checkDsStatus(dsMap);
                List<String> dsList = new ArrayList<>();
                for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
                    dsList.add(next.getValue().getType());
                }
                boolean needOrder = Utils.isNeedOrder(dsList);
                boolean crossDs = dto.getIsCross();
                if (!crossDs) {
                    if (datasetDataManage.notFullDs.contains(dsMap.entrySet().iterator().next().getValue().getType()) && (boolean) sqlMap.get("isFullJoin")) {
                        CrestException.throwException(Translator.get("i18n_not_full"));
                    }
                    sql = Utils.replaceSchemaAlias(sql, dsMap);
                }
                List<DataSetRowPermissionsTreeDTO> rowPermissionsTree = new ArrayList<>();
                TokenUserBO user = AuthUtils.getUser();
                if (user != null) {
                    rowPermissionsTree = permissionManage.getRowPermissionsTree(dto.getId(), user.getUserId());
                }
                if (StringUtils.isNotEmpty(request.getExpressionTree())) {
                    // 用户在导出弹窗追加的过滤条件作为临时行权限参与 SQL 构建。
                    DatasetRowPermissionsTreeObj datasetRowPermissionsTreeObj = JsonUtil.parseObject(request.getExpressionTree(), DatasetRowPermissionsTreeObj.class);
                    permissionManage.getField(datasetRowPermissionsTreeObj);
                    DataSetRowPermissionsTreeDTO dataSetRowPermissionsTreeDTO = new DataSetRowPermissionsTreeDTO();
                    dataSetRowPermissionsTreeDTO.setTree(datasetRowPermissionsTreeObj);
                    dataSetRowPermissionsTreeDTO.setExportData(true);
                    rowPermissionsTree.add(dataSetRowPermissionsTreeDTO);
                }

                Provider provider;
                if (crossDs) {
                    // 跨源数据集使用默认 Provider，由引擎层负责跨源 SQL 处理。
                    provider = ProviderFactory.getDefaultProvider();
                } else {
                    provider = ProviderFactory.getProvider(dsList.get(0));
                }
                SQLMeta sqlMeta = new SQLMeta();
                Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);
                Field2SQLObj.field2sqlObj(sqlMeta, allFields, allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
                WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
                List<ChartSortFieldDTO> sortFields = new ArrayList<>();
                for (DatasetTableFieldDTO field : allFields) {
                    if (field.getOrderChecked()) {
                        ChartSortFieldDTO sortField = new ChartSortFieldDTO();
                        BeanUtils.copyBean(sortField, field);
                        sortField.setOrderDirection("asc");
                        sortFields.add(sortField);
                    }
                }
                dto.setSortFields(sortFields);
                DatasetOrder2SQLObj.getOrders(sqlMeta, dto.getSortFields(), allFields);
                String replaceSql = provider.rebuildSQL(SQLProvider.createQuerySQL(sqlMeta, false, false, false), sqlMeta, crossDs, dsMap);
                Long totalCount = datasetDataManage.datasetTotal(dto, replaceSql, null);
                Long curLimit = ExportCenterUtils.getExportLimit("dataset");
                // 导出行数受系统限制保护，避免一次任务生成超大文件。
                totalCount = totalCount > curLimit ? curLimit : totalCount;

                Long sheetCount = (totalCount / sheetLimit) + (totalCount % sheetLimit > 0 ? 1 : 0);
                Workbook wb = new SXSSFWorkbook();
                CellStyle cellStyle = wb.createCellStyle();
                Font font = wb.createFont();
                font.setFontHeightInPoints((short) 12);
                font.setBold(true);
                cellStyle.setFont(font);
                cellStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                for (Long s = 1L; s < sheetCount + 1; s++) {
                    // Excel 单 Sheet 有行数上限，超过上限时拆分为多个 Sheet。
                    Long sheetSize;
                    if (s.equals(sheetCount)) {
                        sheetSize = totalCount - (s - 1) * sheetLimit;
                    } else {
                        sheetSize = sheetLimit;
                    }
                    Long pageSize = (sheetSize / extractPageSize) + (sheetSize % extractPageSize > 0 ? 1 : 0);
                    Sheet detailsSheet = null;
                    List<List<String>> details = new ArrayList<>();
                    for (Long p = 0L; p < pageSize; p++) {
                        // 每个 Sheet 内再按 extractPageSize 分页查询，降低单次内存压力。
                        int beforeCount = (int) ((s - 1) * sheetLimit);
                        String querySQL = SQLProvider.createQuerySQLWithLimit(sqlMeta, false, needOrder, false, beforeCount + p.intValue() * extractPageSize, extractPageSize);
                        if (pageSize == 1) {
                            querySQL = SQLProvider.createQuerySQLWithLimit(sqlMeta, false, needOrder, false, 0, sheetSize.intValue());
                        }
                        querySQL = provider.rebuildSQL(querySQL, sqlMeta, crossDs, dsMap);
                        DatasourceRequest datasourceRequest = new DatasourceRequest();
                        datasourceRequest.setQuery(querySQL);
                        datasourceRequest.setDsList(dsMap);
                        datasourceRequest.setIsCross(crossDs);
                        Map<String, Object> previewData = datasetDataManage.buildPreviewData(provider.fetchResultField(datasourceRequest), allFields, desensitizationList, false);
                        List<Map<String, Object>> data = (List<Map<String, Object>>) previewData.get("data");
                        if (p.equals(0L)) {
                            // 每个 Sheet 的第一页写入表头，后续页只追加数据行。
                            detailsSheet = wb.createSheet("数据" + s);
                            List<String> header = new ArrayList<>();
                            for (DatasetTableFieldDTO field : allFields) {
                                header.add(field.getName());
                            }
                            details.add(header);
                            for (Map<String, Object> obj : data) {
                                List<String> row = new ArrayList<>();
                                for (DatasetTableFieldDTO field : allFields) {
                                    String string = (String) obj.get(field.getEngineFieldName());
                                    row.add(string);
                                }
                                details.add(row);
                            }
                            if (CollectionUtils.isNotEmpty(details)) {
                                for (int i = 0; i < details.size(); i++) {
                                    Row row = detailsSheet.createRow(i);
                                    List<String> rowData = details.get(i);
                                    if (rowData != null) {
                                        for (int j = 0; j < rowData.size(); j++) {
                                            Cell cell = row.createCell(j);
                                            if (i == 0) {
                                                cell.setCellValue(rowData.get(j));
                                                cell.setCellStyle(cellStyle);
                                                detailsSheet.setColumnWidth(j, 255 * 20);
                                            } else {
                                                if (isNumericExportField(allFields.get(j).getFieldType()) && StringUtils.isNotEmpty(rowData.get(j))) {
                                                    try {
                                                        cell.setCellValue(Double.valueOf(rowData.get(j)));
                                                    } catch (Exception e) {
                                                        cell.setCellValue(rowData.get(j));
                                                    }
                                                } else {
                                                    cell.setCellValue(rowData.get(j));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            details.clear();
                            for (Map<String, Object> obj : data) {
                                List<String> row = new ArrayList<>();
                                for (DatasetTableFieldDTO field : allFields) {
                                    String string = (String) obj.get(field.getEngineFieldName());
                                    row.add(string);
                                }
                                details.add(row);
                            }
                            int lastNum = detailsSheet.getLastRowNum();
                            for (int i = 0; i < details.size(); i++) {
                                Row row = detailsSheet.createRow(i + lastNum + 1);
                                List<String> rowData = details.get(i);
                                if (rowData != null) {
                                    for (int j = 0; j < rowData.size(); j++) {
                                        Cell cell = row.createCell(j);
                                        if (isNumericExportField(allFields.get(j).getFieldType()) && StringUtils.isNotEmpty(rowData.get(j))) {
                                            try {
                                                cell.setCellValue(Double.valueOf(rowData.get(j)));
                                            } catch (Exception e) {
                                                cell.setCellValue(rowData.get(j));
                                            }
                                        } else {
                                            cell.setCellValue(rowData.get(j));
                                        }
                                    }
                                }
                            }
                        }
                        exportTask.setExportStatus("IN_PROGRESS");
                        exportTask.setHeartbeatTime(System.currentTimeMillis());
                        // 进度按 Sheet 和页两层比例计算，便于前端展示连续进度。
                        double exportRogress2 = (double) ((double) s - 1) / ((double) sheetCount);
                        double exportRogress = (double) ((double) (p + 1) / (double) pageSize) * ((double) 1 / sheetCount);
                        DecimalFormat df = new DecimalFormat("#.##");
                        String formattedResult = df.format((exportRogress + exportRogress2) * 100);
                        exportTask.setExportProgress(formattedResult);
                        exportTaskMapper.updateById(exportTask);
                    }
                }
                this.addWatermarkTools(wb);
                try (OutputStream fileOutputStream = storageService.newOutputStream(exportFile)) {
                    wb.write(fileOutputStream);
                    fileOutputStream.flush();
                }
                wb.close();
                exportTask.setExportProgress("100");
                exportTask.setExportStatus("SUCCESS");
                exportTask.setMsg(null);
                exportTask.setLastError(null);
                exportTask.setHeartbeatTime(System.currentTimeMillis());
                setFileSize(exportFile, exportTask);

            } catch (Exception e) {
                LogUtil.error("Failed to export data", e);
                exportTask.setMsg(e.getMessage());
                exportTask.setLastError(e.getMessage());
                exportTask.setExportStatus("FAILED");
                exportTask.setHeartbeatTime(System.currentTimeMillis());
            } finally {
                exportTaskMapper.updateById(exportTask);
            }
        });
        return future;
    }

    // 启动仪表板图表导出任务，审计类型按仪表板记录。
    @CrestAudit(id = "#p0.exportFrom", ot = LogOT.EXPORT, st = LogST.PANEL)
    public void startPanelViewTask(CoreExportTask exportTask, ChartExcelRequest request) {
        startViewTask(exportTask, request);
    }

    // 启动数据大屏图表导出任务，审计类型按大屏记录。
    @CrestAudit(id = "#p0.exportFrom", ot = LogOT.EXPORT, st = LogST.SCREEN)
    public void startDataVViewTask(CoreExportTask exportTask, ChartExcelRequest request) {
        startViewTask(exportTask, request);
    }

    // 从二维数据中移除指定列，用于导出时过滤隐藏字段。
    public static void removeColumn(List<Object[]> list, List<Integer> columnIndexs) {
        if (list == null || list.isEmpty() || columnIndexs == null || columnIndexs.isEmpty()) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Object[] originalRow = list.get(i);
            Object[] newRow = new Object[originalRow.length - columnIndexs.size()];
            int newIndex = 0;
            for (int j = 0; j < originalRow.length; j++) {
                if (!columnIndexs.contains(j)) {
                    newRow[newIndex++] = originalRow[j];
                }
            }
            list.set(i, newRow);
        }
    }

    // 判断导出字段是否为数值类型，数值类型写入 Excel 时优先使用数字单元格。
    private boolean isNumericExportField(Integer fieldType) {
        return Objects.equals(fieldType, FieldTypeConstants.INTEGER) || Objects.equals(fieldType, FieldTypeConstants.FLOAT);
    }

    // 异步启动图表视图导出任务，表格类视图按分页导出，普通图表一次性写入。
    public void startViewTask(CoreExportTask exportTask, ChartExcelRequest request) {
        Future future = submitViewTask(exportTask, request);
        Running_Task.put(exportTask.getId(), future);
    }

    private Future submitViewTask(CoreExportTask exportTask, ChartExcelRequest request) {
        prepareTaskDirectory(exportTask.getId());
        File exportFile = storageService.resolve(exportData_path, exportTask.getId(), exportTask.getId() + ".xlsx");
        TokenUserBO tokenUserBO = AuthUtils.getUser();
        Future future = scheduledThreadPoolExecutor.submit(() -> {
            // 线程池任务需要恢复用户上下文，后续权限、水印和审计依赖当前用户。
            AuthUtils.setUser(tokenUserBO);
            try {
                exportTask.setExportStatus("IN_PROGRESS");
                exportTask.setMsg(null);
                exportTask.setFileSize(null);
                exportTask.setFileSizeUnit(null);
                exportTask.setHeartbeatTime(System.currentTimeMillis());
                exportTaskMapper.updateById(exportTask);
                Workbook wb = new SXSSFWorkbook();
                CellStyle cellStyle = wb.createCellStyle();
                Font font = wb.createFont();
                font.setFontHeightInPoints((short) 12);
                font.setBold(true);
                cellStyle.setFont(font);
                cellStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                List<Object[]> details = new ArrayList<>();
                Sheet detailsSheet;
                Integer sheetIndex = 1;
                if ("dataset".equals(request.getDownloadType()) || request.getViewInfo().getType().equalsIgnoreCase("table-info") || request.getViewInfo().getType().equalsIgnoreCase("table-normal")) {
                    // 明细表、普通表和数据集下载可能数据量较大，需要分页写入多个 Sheet。
                    boolean summaryEnabled = !"dataset".equals(request.getDownloadType()) && ChartDataServer.isSummaryEnabled(request.getViewInfo());
                    ChartDataServer.SummaryConfig summaryConfig = null;
                    ChartDataServer.SummaryAccumulator summaryAcc = null;
                    List<ChartViewFieldDTO> allExportColumns = null;
                    Map<String, java.math.BigDecimal> customSumResult = null;
                    if (summaryEnabled) {
                        summaryConfig = ChartDataServer.parseSummaryConfig(request.getViewInfo());
                        summaryAcc = new ChartDataServer.SummaryAccumulator();
                        allExportColumns = ChartDataServer.getAllExportColumns(request.getViewInfo());
                    }

                    request.getViewInfo().getChartExtRequest().setPageSize(Long.valueOf(extractPageSize));
                    ChartViewDTO chartViewDTO = chartDataServer.findExcelData(request);
                    for (long i = 1; i < chartViewDTO.getTotalPage() + 1; i++) {
                        // 每页重新查询图表导出数据，避免一次性加载全部明细。
                        request.getViewInfo().getChartExtRequest().setGoPage(i);
                        request.getViewInfo().setExtStack(request.getViewInfo().getExtStack().stream().filter(ele -> !ele.isHide()).collect(Collectors.toList()));
                        ChartViewDTO pageDto = chartDataServer.findExcelData(request);
                        details.addAll(request.getDetails());

                        if (summaryEnabled) {
                            // 汇总行按页累加统计，最后一页再追加到导出明细末尾。
                            ChartDataServer.accumulatePageStats(summaryAcc, request.getDetails(), allExportColumns, summaryConfig);
                            if (i == chartViewDTO.getTotalPage() && pageDto.getData() != null && pageDto.getData().get("customSumResult") != null) {
                                customSumResult = (Map<String, java.math.BigDecimal>) pageDto.getData().get("customSumResult");
                            }
                        }

                        if (((details.size() + extractPageSize) > sheetLimit) || i == chartViewDTO.getTotalPage()) {
                            // 累积行数接近单 Sheet 上限或到达最后一页时写出当前 Sheet。
                            if (i == chartViewDTO.getTotalPage() && summaryEnabled && summaryAcc.totalCount > 0) {
                                Object[] totalRow = ChartDataServer.buildSummaryRow(allExportColumns, summaryConfig, summaryAcc, customSumResult);
                                details.add(totalRow);
                            }

                            detailsSheet = wb.createSheet("数据" + sheetIndex);
                            Integer[] excelTypes = request.getExcelTypes();
                            ViewDetailField[] detailFields = request.getDetailFields();
                            Object[] header = request.getHeader();
                            List<ChartViewFieldDTO> xAxis = new ArrayList<>();
                            xAxis.addAll(request.getViewInfo().getXAxis());
                            xAxis.addAll(request.getViewInfo().getYAxis());
                            xAxis.addAll(request.getViewInfo().getXAxisExt());
                            xAxis.addAll(request.getViewInfo().getYAxisExt());
                            xAxis.addAll(request.getViewInfo().getExtStack());
                            xAxis.addAll(request.getViewInfo().getDrillFields());
                            // 只导出当前视图仍可见的字段，隐藏字段需要同步移除表头和数据列。
                            header = Arrays.stream(request.getHeader()).filter(item -> xAxis.stream().map(d -> StringUtils.isNotBlank(d.getChartShowName()) ? d.getChartShowName() : d.getName()).toList().contains(item)).toArray();
                            details.add(0, header);
                            List<Integer> columnIndexs = new ArrayList<>();
                            for (int i1 = 0; i1 < xAxis.size(); i1++) {
                                ChartViewFieldDTO xAxi = xAxis.get(i1);
                                if (xAxi.isHide()) {
                                    columnIndexs.add(i1);
                                }
                            }
                            removeColumn(details, columnIndexs);
                            ChartDataServer.setExcelData(detailsSheet, cellStyle, header, details, detailFields, excelTypes, request.getViewInfo(), wb);
                            sheetIndex++;
                            details.clear();
                        }
                        exportTask.setExportStatus("IN_PROGRESS");
                        exportTask.setHeartbeatTime(System.currentTimeMillis());
                        double exportProgress = (double) ((double) i / (chartViewDTO.getTotalPage()));
                        DecimalFormat df = new DecimalFormat("#.##");
                        String formattedResult = df.format((exportProgress) * 100);
                        exportTask.setExportProgress(formattedResult);
                        exportTaskMapper.updateById(exportTask);
                    }
                } else {
                    // 非明细表数据量相对可控，直接使用图表导出结构写入工作簿。
                    downloadNotTableInfoData(request, wb);
                }
                this.addWatermarkTools(wb);

                try (OutputStream outputStream = storageService.newOutputStream(exportFile)) {
                    wb.write(outputStream);
                    outputStream.flush();
                }
                wb.close();
                exportTask.setExportProgress("100");
                exportTask.setExportStatus("SUCCESS");
                exportTask.setMsg(null);
                exportTask.setLastError(null);
                exportTask.setHeartbeatTime(System.currentTimeMillis());
                setFileSize(exportFile, exportTask);
            } catch (Exception e) {
                exportTask.setMsg(e.getMessage());
                exportTask.setLastError(e.getMessage());
                LogUtil.error("Failed to export data", e);
                exportTask.setExportStatus("FAILED");
                exportTask.setHeartbeatTime(System.currentTimeMillis());
            } finally {
                exportTaskMapper.updateById(exportTask);
            }
        });
        return future;
    }

    // 写入非明细表导出数据，支持单 Sheet 和多系列拆分 Sheet 两种模式。
    private void downloadNotTableInfoData(ChartExcelRequest request, Workbook wb) {
        chartDataServer.findExcelData(request);
        // 表头样式统一使用灰底加粗，和明细导出保持一致。
        CellStyle cellStyle = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontHeightInPoints((short) 12);
        font.setBold(true);
        cellStyle.setFont(font);
        cellStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        if (CollectionUtils.isEmpty(request.getMultiInfo())) {
            if (request.getViewInfo().getType().equalsIgnoreCase("chart-mix-dual-line")) {
            } else {
                List<Object[]> details = request.getDetails();
                Integer[] excelTypes = request.getExcelTypes();
                details.add(0, request.getHeader());
                ViewDetailField[] detailFields = request.getDetailFields();
                Object[] header = request.getHeader();
                Sheet detailsSheet = wb.createSheet("数据");
                if (request.getViewInfo().getType().equalsIgnoreCase("table-normal")) {
                    ChartDataServer.setExcelData(detailsSheet, cellStyle, header, details, detailFields, excelTypes, request.getViewInfo(), wb);
                } else {
                    ChartDataServer.setExcelData(detailsSheet, cellStyle, header, details, detailFields, excelTypes, request.getViewInfo(), null);
                }
            }
        } else {
            // 多系列导出按系列拆分多个 Sheet，便于用户分别查看。
            for (int i = 0; i < request.getMultiInfo().size(); i++) {
                ChartExcelRequestInner requestInner = request.getMultiInfo().get(i);

                List<Object[]> details = requestInner.getDetails();
                Integer[] excelTypes = requestInner.getExcelTypes();
                details.add(0, requestInner.getHeader());
                ViewDetailField[] detailFields = requestInner.getDetailFields();
                Object[] header = requestInner.getHeader();
                // 每个系列写入一个明细 Sheet。
                Sheet detailsSheet = wb.createSheet("数据 " + (i + 1));
                ChartDataServer.setExcelData(detailsSheet, cellStyle, header, details, detailFields, excelTypes, request.getViewInfo(), null);
            }
        }
    }

    // 回填导出文件大小信息，按 Kb/Mb/Gb 自动选择展示单位。
    private void setFileSize(File file, CoreExportTask exportTask) {
        long length = storageService.size(file);
        String unit = "Mb";
        Double size = 0.0;
        if ((double) length / 1024 / 1024 > 1) {
            if ((double) length / 1024 / 1024 / 1024 > 1) {
                unit = "Gb";
                size = Double.valueOf(String.format("%.2f", (double) length / 1024 / 1024 / 1024));
            } else {
                size = Double.valueOf(String.format("%.2f", (double) length / 1024 / 1024));
            }

        } else {
            unit = "Kb";
            size = Double.valueOf(String.format("%.2f", (double) length / 1024));
        }
        exportTask.setFileSize(size);
        exportTask.setFileSizeUnit(unit);
    }

    // 准备导出任务文件目录，防止同名普通文件占用目录路径。
    private File prepareTaskDirectory(String taskId) {
        return storageService.ensureDirectory(exportData_path, taskId);
    }

    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-worker";
        }
    }

    // 为工作簿添加水印，只有系统水印和 Excel 水印同时开启时才写入。
    public void addWatermarkTools(Workbook wb) {
        VisualizationWatermark watermark = watermarkMapper.selectById("system_default");
        WatermarkContentDTO watermarkContent = JsonUtil.parseObject(watermark.getSettingContent(), WatermarkContentDTO.class);
        if (watermarkContent.getEnable() && watermarkContent.getExcelEnable()) {
            UserFormVO userInfo = visualizationMapper.queryInnerUserInfo(AuthUtils.getUser().getUserId());
            // 先生成水印图片并注册到工作簿，再逐个 Sheet 绑定水印图片。
            int watermarkPictureIdx = ExcelWatermarkUtils.addWatermarkImage(wb, watermarkContent, userInfo);
            for (Sheet sheet : wb) {
                ExcelWatermarkUtils.addWatermarkToSheet(sheet, watermarkPictureIdx);
            }
        }
    }

    // 下载已生成的导出文件，兼容历史任务的文件命名规则。
    public void download(CoreExportTask exportTask, HttpServletResponse response) throws Exception {
        File exportFile;
        if (exportTask.getExportTime() != null && exportTask.getExportTime() < 1730277243491L) {
            // 旧版本导出文件以原始文件名保存，保留兼容路径。
            exportFile = storageService.resolve(exportData_path, exportTask.getId(), exportTask.getFileName());
        } else {
            exportFile = storageService.resolve(exportData_path, exportTask.getId(), exportTask.getId() + ".xlsx");
        }
        if (!storageService.isRegularFile(exportFile)) {
            CrestException.throwException(Translator.get("i18n_export_file_missing"));
        }

        response.setContentType("application/octet-stream");
        String encodedFileName = URLEncoder.encode(exportTask.getFileName(), StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"; filename*=utf-8''" + encodedFileName);

        try (InputStream fileInputStream = storageService.newInputStream(exportFile);
             OutputStream outputStream = response.getOutputStream()) {
            // 通过存储抽象读取文件，保证 API Pod 可下载 Worker Pod 写入的共享文件。
            fileInputStream.transferTo(outputStream);
            response.flushBuffer();
        } catch (IOException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    // 直接下载数据集导出文件，不创建导出任务记录，适用于同步下载入口。
    @CrestAudit(id = "#p0.id", ot = LogOT.EXPORT, st = LogST.DATASET)
    public void downloadDataset(DataSetExportRequest request, HttpServletResponse response) throws Exception {
        OutputStream outputStream = response.getOutputStream();
        try {
            CoreDatasetGroup coreDatasetGroup = coreDatasetGroupMapper.selectById(request.getId());
            if (coreDatasetGroup == null) {
                throw new Exception("Not found dataset group: " + request.getFilename());
            }
            DatasetGroupInfoDTO dto = new DatasetGroupInfoDTO();
            BeanUtils.copyBean(dto, coreDatasetGroup);
            dto.setUnionSql(null);
            List<UnionDTO> unionDTOList = JsonUtil.parseList(coreDatasetGroup.getInfo(), new TypeReference<>() {
            });
            dto.setUnion(unionDTOList);
            List<DatasetTableFieldDTO> dsFields = datasetTableFieldManage.selectByDatasetGroupId(request.getId());
            List<DatasetTableFieldDTO> allFields = dsFields.stream().map(ele -> {
                DatasetTableFieldDTO datasetTableFieldDTO = new DatasetTableFieldDTO();
                BeanUtils.copyBean(datasetTableFieldDTO, ele);
                datasetTableFieldDTO.setFieldShortName(ele.getEngineFieldName());
                return datasetTableFieldDTO;
            }).collect(Collectors.toList());
            DatasetGroupInfoDTO datasetGroupInfoDTO = datasetGroupManage.datasetGroupInfoDTO(request.getId(), null);
            Map<String, Object> sqlMap = datasetSQLManage.getUnionSQLForEdit(datasetGroupInfoDTO, null);
            dto.setIsCross(Boolean.TRUE.equals(datasetGroupInfoDTO.getIsCross()));
            if (sqlMap == null || StringUtils.isBlank((String) sqlMap.get("sql"))) {
                CrestException.throwException("数据集配置不完整，无法导出");
            }
            String sql = (String) sqlMap.get("sql");
            if (ObjectUtils.isEmpty(allFields)) {
                CrestException.throwException(Translator.get("i18n_no_fields"));
            }
            Map<String, ColumnPermissionItem> desensitizationList = new HashMap<>();
            // 直接下载同样应用列权限和脱敏规则，保持与异步导出一致。
            allFields = permissionManage.filterColumnPermissions(allFields, desensitizationList, dto.getId(), null);
            if (ObjectUtils.isEmpty(allFields)) {
                CrestException.throwException(Translator.get("i18n_no_column_permission"));
            }
            dto.setAllFields(allFields);
            datasetDataManage.buildFieldName(sqlMap, allFields);
            Map<Long, DatasourceSchemaDTO> dsMap = (Map<Long, DatasourceSchemaDTO>) sqlMap.get("dsMap");
            DatasourceUtils.checkDsStatus(dsMap);
            List<String> dsList = new ArrayList<>();
            for (Map.Entry<Long, DatasourceSchemaDTO> next : dsMap.entrySet()) {
                dsList.add(next.getValue().getType());
            }
            boolean needOrder = Utils.isNeedOrder(dsList);
            boolean crossDs = dto.getIsCross();
            if (!crossDs) {
                if (datasetDataManage.notFullDs.contains(dsMap.entrySet().iterator().next().getValue().getType()) && (boolean) sqlMap.get("isFullJoin")) {
                    CrestException.throwException(Translator.get("i18n_not_full"));
                }
                sql = Utils.replaceSchemaAlias(sql, dsMap);
            }
            List<DataSetRowPermissionsTreeDTO> rowPermissionsTree = new ArrayList<>();
            TokenUserBO user = AuthUtils.getUser();
            if (user != null) {
                rowPermissionsTree = permissionManage.getRowPermissionsTree(dto.getId(), user.getUserId());
            }
            if (StringUtils.isNotEmpty(request.getExpressionTree())) {
                // 导出弹窗中的临时过滤表达式作为导出专属行权限参与 SQL。
                DatasetRowPermissionsTreeObj datasetRowPermissionsTreeObj = JsonUtil.parseObject(request.getExpressionTree(), DatasetRowPermissionsTreeObj.class);
                permissionManage.getField(datasetRowPermissionsTreeObj);
                DataSetRowPermissionsTreeDTO dataSetRowPermissionsTreeDTO = new DataSetRowPermissionsTreeDTO();
                dataSetRowPermissionsTreeDTO.setTree(datasetRowPermissionsTreeObj);
                dataSetRowPermissionsTreeDTO.setExportData(true);
                rowPermissionsTree.add(dataSetRowPermissionsTreeDTO);
            }

            Provider provider;
            if (crossDs) {
                provider = ProviderFactory.getDefaultProvider();
            } else {
                provider = ProviderFactory.getProvider(dsList.get(0));
            }
            SQLMeta sqlMeta = new SQLMeta();
            Table2SQLObj.table2sqlobj(sqlMeta, null, "(" + sql + ")", crossDs);
            Field2SQLObj.field2sqlObj(sqlMeta, allFields, allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
            WhereTree2Str.transFilterTrees(sqlMeta, rowPermissionsTree, allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
            Order2SQLObj.getOrders(sqlMeta, dto.getSortFields(), allFields, crossDs, dsMap, Utils.getParams(allFields), null, pluginManage);
            String replaceSql = provider.rebuildSQL(SQLProvider.createQuerySQL(sqlMeta, false, false, false), sqlMeta, crossDs, dsMap);
            Long totalCount = datasetDataManage.datasetTotal(dto, replaceSql, null);
            Long curLimit = ExportCenterUtils.getExportLimit("dataset");
            // 同步下载也受数据集导出上限约束。
            totalCount = totalCount > curLimit ? curLimit : totalCount;

            Long sheetCount = (totalCount / sheetLimit) + (totalCount % sheetLimit > 0 ? 1 : 0);
            Workbook wb = new SXSSFWorkbook();
            CellStyle cellStyle = wb.createCellStyle();
            Font font = wb.createFont();
            font.setFontHeightInPoints((short) 12);
            font.setBold(true);
            cellStyle.setFont(font);
            cellStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            for (Long s = 1L; s < sheetCount + 1; s++) {
                // 按 Excel 单 Sheet 行数上限拆分输出。
                Long sheetSize;
                if (s.equals(sheetCount)) {
                    sheetSize = totalCount - (s - 1) * sheetLimit;
                } else {
                    sheetSize = sheetLimit;
                }
                Long pageSize = (sheetSize / extractPageSize) + (sheetSize % extractPageSize > 0 ? 1 : 0);
                Sheet detailsSheet = null;
                List<List<String>> details = new ArrayList<>();
                for (Long p = 0L; p < pageSize; p++) {
                    // Sheet 内分页查询，避免一次性读取全部导出数据。
                    int beforeCount = (int) ((s - 1) * sheetLimit);
                    String querySQL = SQLProvider.createQuerySQLWithLimit(sqlMeta, false, needOrder, false, beforeCount + p.intValue() * extractPageSize, extractPageSize);
                    if (pageSize == 1) {
                        querySQL = SQLProvider.createQuerySQLWithLimit(sqlMeta, false, needOrder, false, 0, sheetSize.intValue());
                    }
                    querySQL = provider.rebuildSQL(querySQL, sqlMeta, crossDs, dsMap);
                    DatasourceRequest datasourceRequest = new DatasourceRequest();
                    datasourceRequest.setQuery(querySQL);
                    datasourceRequest.setDsList(dsMap);
                    datasourceRequest.setIsCross(crossDs);
                    Map<String, Object> previewData = datasetDataManage.buildPreviewData(provider.fetchResultField(datasourceRequest), allFields, desensitizationList, false);
                    List<Map<String, Object>> data = (List<Map<String, Object>>) previewData.get("data");
                    if (p.equals(0L)) {
                        detailsSheet = wb.createSheet("数据" + s);
                        List<String> header = new ArrayList<>();
                        for (DatasetTableFieldDTO field : allFields) {
                            header.add(field.getName());
                        }
                        details.add(header);
                        for (Map<String, Object> obj : data) {
                            List<String> row = new ArrayList<>();
                            for (DatasetTableFieldDTO field : allFields) {
                                String string = (String) obj.get(field.getEngineFieldName());
                                row.add(string);
                            }
                            details.add(row);
                        }
                        if (CollectionUtils.isNotEmpty(details)) {
                            for (int i = 0; i < details.size(); i++) {
                                Row row = detailsSheet.createRow(i);
                                List<String> rowData = details.get(i);
                                if (rowData != null) {
                                    for (int j = 0; j < rowData.size(); j++) {
                                        Cell cell = row.createCell(j);
                                        if (i == 0) {
                                            cell.setCellValue(rowData.get(j));
                                            cell.setCellStyle(cellStyle);
                                            detailsSheet.setColumnWidth(j, 255 * 20);
                                        } else {
                                            if (isNumericExportField(allFields.get(j).getFieldType()) && StringUtils.isNotEmpty(rowData.get(j))) {
                                                try {
                                                    cell.setCellValue(Double.valueOf(rowData.get(j)));
                                                } catch (Exception e) {
                                                    cell.setCellValue(rowData.get(j));
                                                }
                                            } else {
                                                cell.setCellValue(rowData.get(j));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        details.clear();
                        for (Map<String, Object> obj : data) {
                            List<String> row = new ArrayList<>();
                            for (DatasetTableFieldDTO field : allFields) {
                                String string = (String) obj.get(field.getEngineFieldName());
                                row.add(string);
                            }
                            details.add(row);
                        }
                        int lastNum = detailsSheet.getLastRowNum();
                        for (int i = 0; i < details.size(); i++) {
                            Row row = detailsSheet.createRow(i + lastNum + 1);
                            List<String> rowData = details.get(i);
                            if (rowData != null) {
                                for (int j = 0; j < rowData.size(); j++) {
                                    Cell cell = row.createCell(j);
                                    if (i == 0) {
                                        cell.setCellValue(rowData.get(j));
                                        cell.setCellStyle(cellStyle);
                                        detailsSheet.setColumnWidth(j, 255 * 20);
                                    } else {
                                        if (isNumericExportField(allFields.get(j).getFieldType()) && StringUtils.isNotEmpty(rowData.get(j))) {
                                            try {
                                                cell.setCellValue(Double.valueOf(rowData.get(j)));
                                            } catch (Exception e) {
                                                cell.setCellValue(rowData.get(j));
                                            }
                                        } else {
                                            cell.setCellValue(rowData.get(j));
                                        }
                                    }

                                }
                            }
                        }
                    }
                }
            }
            this.addWatermarkTools(wb);
            response.setContentType("application/vnd.ms-excel");
            response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(request.getFilename(), StandardCharsets.UTF_8) + ".xlsx");
            wb.write(outputStream);
            outputStream.flush();
            outputStream.close();
        } catch (Exception e) {
            CrestException.throwException(e);
        }
    }
}
