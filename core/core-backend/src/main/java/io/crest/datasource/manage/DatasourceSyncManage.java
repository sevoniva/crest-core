package io.crest.datasource.manage;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.crest.commons.constants.TaskStatus;
import io.crest.dataset.utils.TableUtils;
import io.crest.datasource.dao.auto.entity.CoreDatasource;
import io.crest.datasource.dao.auto.entity.CoreDatasourceTask;
import io.crest.datasource.dao.auto.entity.CoreDatasourceTaskLog;
import io.crest.datasource.dao.auto.entity.CoreEngine;
import io.crest.datasource.dao.auto.mapper.CoreDatasourceMapper;
import io.crest.datasource.queue.DatasourceSyncTaskQueueService;
import io.crest.datasource.provider.*;
import io.crest.datasource.request.EngineRequest;
import io.crest.datasource.server.DatasourceServer;
import io.crest.datasource.server.DatasourceTaskServer;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.DatasetTableDTO;
import io.crest.extensions.datasource.dto.DatasourceDTO;
import io.crest.extensions.datasource.dto.DatasourceRequest;
import io.crest.extensions.datasource.dto.TableField;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import io.crest.job.schedule.ExtractDataJob;
import io.crest.job.schedule.ScheduleManager;
import io.crest.utils.BeanUtils;
import io.crest.utils.LogUtil;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Component;

import java.util.*;

import static io.crest.datasource.server.DatasourceTaskServer.ScheduleType.CRON;
import static io.crest.datasource.server.DatasourceTaskServer.ScheduleType.MANUAL;

@Component
@SuppressWarnings({"deprecation", "unchecked"})
// 管理数据源抽取任务、引擎表创建和同步调度
public class DatasourceSyncManage {

    @Resource
    private CoreDatasourceMapper datasourceMapper;
    @Resource
    private EngineManage engineManage;
    @Resource
    private DatasourceTaskServer datasourceTaskServer;
    @Resource
    private ScheduleManager scheduleManager;
    @Resource
    private CalciteProvider calciteProvider;
    @Resource
    private DatasourceServer datasourceServer;
    @Resource
    private DatasourceSyncTaskQueueService queueService;


    // 抽取 Excel 数据源的所有表数据
    public void extractExcelData(CoreDatasource coreDatasource, String type) {
        if (coreDatasource == null) {
            LogUtil.error("Can not find CoreDatasource");
            return;
        }
        DatasourceServer.UpdateType updateType = DatasourceServer.UpdateType.valueOf(type);
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDatasource(transDTO(coreDatasource));
        List<DatasetTableDTO> tables = ExcelUtils.tables(datasourceRequest);
        for (DatasetTableDTO tableDTO : tables) {
            CoreDatasourceTaskLog datasetTableTaskLog = datasourceTaskServer.initTaskLog(coreDatasource.getId(), null, tableDTO.getTableName(), CRON.toString());
            datasourceRequest.setTable(tableDTO.getTableName());
            List<TableField> tableFields = ExcelUtils.getTableFields(datasourceRequest);
            try {
                datasetTableTaskLog.setInfo(datasetTableTaskLog.getInfo() + "/n Begin to sync datatable: " + datasourceRequest.getTable());
                createEngineTable(datasourceRequest.getTable(), tableFields);
                if (updateType.equals(DatasourceServer.UpdateType.all_scope)) {
                    createEngineTable(TableUtils.tmpName(datasourceRequest.getTable()), tableFields);
                }
                extractExcelData(datasourceRequest, updateType, tableFields);
                if (updateType.equals(DatasourceServer.UpdateType.all_scope)) {
                    replaceTable(datasourceRequest.getTable());
                }
                datasetTableTaskLog.setInfo(datasetTableTaskLog.getInfo() + "/n End to sync datatable: " + datasourceRequest.getTable());
                datasetTableTaskLog.setTaskStatus(TaskStatus.Completed.toString());
            } catch (Exception e) {
                try {
                    if (updateType.equals(DatasourceServer.UpdateType.all_scope)) {
                        dropEngineTable(TableUtils.tmpName(datasourceRequest.getTable()));
                    }
                } catch (Exception ignore) {
                }
                datasetTableTaskLog.setTaskStatus(TaskStatus.Error.toString());
                datasetTableTaskLog.setInfo(datasetTableTaskLog.getInfo() + "/n Failed to sync datatable: " + datasourceRequest.getTable() + ", " + e.getMessage());
                if (e.getMessage().contains("Duplicate entry")) {
                    CrestException.throwException("不能追加主键相同的数据, " + e.getMessage());
                } else {
                    CrestException.throwException(e);
                }

            } finally {
                datasourceTaskServer.saveLog(datasetTableTaskLog);
            }
        }
    }


    // 执行数据源同步任务入口
    public void extractData(Long datasourceId, Long taskId, JobExecutionContext context) {
        extractData(datasourceId, taskId, context == null ? null : context.getFireInstanceId(), false, null);
    }

    // Worker 消费队列后执行任务，执行前仍由数据库状态抢占防重。
    public void executeQueued(Long datasourceId, Long taskId, String fireInstanceId, String workerId) {
        executeQueued(datasourceId, taskId, fireInstanceId, workerId, null);
    }

    // Worker 消费队列后执行任务，投递时间用于过滤已经完成的旧消息。
    public void executeQueued(Long datasourceId, Long taskId, String fireInstanceId, String workerId, Long enqueueTime) {
        extractData(datasourceId, taskId, fireInstanceId, true, workerId, enqueueTime);
    }

    private void extractData(Long datasourceId, Long taskId, String fireInstanceId, boolean queued, String workerId) {
        extractData(datasourceId, taskId, fireInstanceId, queued, workerId, null);
    }

    private void extractData(Long datasourceId, Long taskId, String fireInstanceId, boolean queued, String workerId,
                             Long enqueueTime) {
        CoreDatasource coreDatasource = datasourceMapper.selectById(datasourceId);
        if (coreDatasource == null) {
            LogUtil.error("Can not find datasource: " + datasourceId);
            return;
        }
        CoreDatasourceTask coreDatasourceTask = datasourceTaskServer.selectById(taskId);
        if (coreDatasourceTask == null) {
            return;
        }
        datasourceTaskServer.checkTaskIsStopped(coreDatasourceTask);
        coreDatasourceTask = datasourceTaskServer.selectById(taskId);
        if (coreDatasourceTask == null) {
            return;
        }
        if (StringUtils.isNotEmpty(coreDatasourceTask.getTaskStatus()) && (coreDatasourceTask.getTaskStatus().equalsIgnoreCase(TaskStatus.Stopped.name()) || coreDatasourceTask.getTaskStatus().equalsIgnoreCase(TaskStatus.Suspend.name()))) {
            LogUtil.info("Skip synchronization task: {} ,due to task status is {}", coreDatasourceTask.getId(), coreDatasourceTask.getTaskStatus());
            if (queued) {
                datasourceTaskServer.finishQueuedTask(coreDatasourceTask.getId(), null);
            }
            return;
        }

        if (!queued && queueService.enabled()) {
            long now = System.currentTimeMillis();
            if (datasourceTaskServer.markQueuedTaskEnqueued(coreDatasourceTask.getId(), now, queueService.enqueueRetryMillis())) {
                queueService.enqueueDatasourceSyncTask(datasourceId, coreDatasourceTask.getId(), fireInstanceId, now);
            } else {
                LogUtil.info("Skip enqueue datasource sync task due to recent enqueue, task ID : " + coreDatasourceTask.getId());
            }
            return;
        }

        if (datasourceTaskServer.existUnderExecutionTask(datasourceId, coreDatasourceTask.getId(), workerId, enqueueTime)) {
            LogUtil.info("Skip synchronization task for datasource due to exist others, datasource ID : " + datasourceId);
            return;
        }
        String lastError = null;
        try {
            DatasourceServer.UpdateType updateType = DatasourceServer.UpdateType.valueOf(coreDatasourceTask.getUpdateType());
            if (StringUtils.isNotBlank(fireInstanceId)) {
                UpdateWrapper<CoreDatasource> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("id", datasourceId);
                CoreDatasource record = new CoreDatasource();
                record.setSchedulerFireInstanceId(fireInstanceId);
                datasourceMapper.update(record, updateWrapper);
            }
            if (coreDatasource.getType().equalsIgnoreCase("ExcelRemote")) {
                extractedExcelData(taskId, coreDatasource, updateType, coreDatasourceTask.getSyncRate(), queued);
            } else {
                extractedData(taskId, coreDatasource, updateType, coreDatasourceTask.getSyncRate(), queued);
            }
        } catch (Exception e) {
            lastError = e.getMessage();
            LogUtil.error(e);
        } finally {
            datasourceTaskServer.updateTaskStatus(coreDatasourceTask);
            updateDsTaskStatus(datasourceId);
            if (queued) {
                datasourceTaskServer.finishQueuedTask(coreDatasourceTask.getId(), lastError);
            }
        }
    }

    // 抽取 API 数据源表数据
    public void extractedData(Long taskId, CoreDatasource coreDatasource, DatasourceServer.UpdateType updateType, String scheduleType) {
        extractedData(taskId, coreDatasource, updateType, scheduleType, false);
    }

    private void extractedData(Long taskId, CoreDatasource coreDatasource, DatasourceServer.UpdateType updateType, String scheduleType, boolean queued) {
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDatasource(transDTO(coreDatasource));
        List<DatasetTableDTO> tables = (List<DatasetTableDTO>) datasourceServer.invokeMethod(coreDatasource.getType(), "getApiTables", DatasourceRequest.class, datasourceRequest);
        for (DatasetTableDTO api : tables) {
            touchQueuedHeartbeat(taskId, queued);
            CoreDatasourceTaskLog datasetTableTaskLog = datasourceTaskServer.initTaskLog(coreDatasource.getId(), taskId, api.getTableName(), scheduleType);
            datasourceRequest.setTable(api.getTableName());
            List<TableField> tableFields = (List<TableField>) datasourceServer.invokeMethod(coreDatasource.getType(), "getTableFields", DatasourceRequest.class, datasourceRequest);
            try {
                datasetTableTaskLog.setInfo(datasetTableTaskLog.getInfo() + "/n Begin to sync datatable: " + datasourceRequest.getTable());
                createEngineTable(datasourceRequest.getTable(), tableFields);
                if (updateType.equals(DatasourceServer.UpdateType.all_scope)) {
                    createEngineTable(TableUtils.tmpName(datasourceRequest.getTable()), tableFields);
                }
                extractApiData(datasourceRequest, updateType, tableFields, taskId, queued);
                if (updateType.equals(DatasourceServer.UpdateType.all_scope)) {
                    replaceTable(datasourceRequest.getTable());
                }
                datasetTableTaskLog.setInfo(datasetTableTaskLog.getInfo() + "/n End to sync datatable: " + datasourceRequest.getTable());
                datasetTableTaskLog.setTaskStatus(TaskStatus.Completed.toString());
                datasetTableTaskLog.setEndTime(System.currentTimeMillis());
            } catch (Exception e) {
                try {
                    if (updateType.equals(DatasourceServer.UpdateType.all_scope)) {
                        dropEngineTable(TableUtils.tmpName(datasourceRequest.getTable()));
                    }
                } catch (Exception ignore) {
                }
                datasetTableTaskLog.setInfo(datasetTableTaskLog.getInfo() + "/n Failed to sync datatable: " + datasourceRequest.getTable() + ", " + e.getMessage());
                datasetTableTaskLog.setTaskStatus(TaskStatus.Error.toString());
                datasetTableTaskLog.setEndTime(System.currentTimeMillis());
            } finally {
                datasourceTaskServer.saveLog(datasetTableTaskLog);
            }
        }
    }

    // 抽取远程 Excel 数据源表数据
    public void extractedExcelData(Long taskId, CoreDatasource coreDatasource, DatasourceServer.UpdateType updateType, String scheduleType) {
        extractedExcelData(taskId, coreDatasource, updateType, scheduleType, false);
    }

    private void extractedExcelData(Long taskId, CoreDatasource coreDatasource, DatasourceServer.UpdateType updateType, String scheduleType, boolean queued) {
        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDatasource(transDTO(coreDatasource));
        List<DatasetTableDTO> tables = ExcelUtils.tables(datasourceRequest);
        for (DatasetTableDTO tableDTO : tables) {
            touchQueuedHeartbeat(taskId, queued);
            CoreDatasourceTaskLog datasetTableTaskLog = datasourceTaskServer.initTaskLog(coreDatasource.getId(), taskId, tableDTO.getTableName(), scheduleType);
            datasourceRequest.setTable(tableDTO.getTableName());
            ExcelUtils.getTableFields(datasourceRequest);
            List<TableField> tableFields = ExcelUtils.getTableFields(datasourceRequest);
            try {
                datasetTableTaskLog.setInfo(datasetTableTaskLog.getInfo() + "/n Begin to sync datatable: " + datasourceRequest.getTable());
                createEngineTable(datasourceRequest.getTable(), tableFields);
                if (updateType.equals(DatasourceServer.UpdateType.all_scope)) {
                    createEngineTable(TableUtils.tmpName(datasourceRequest.getTable()), tableFields);
                }
                extractExcelData(datasourceRequest, updateType, tableFields, taskId, queued);
                if (updateType.equals(DatasourceServer.UpdateType.all_scope)) {
                    replaceTable(datasourceRequest.getTable());
                }
                datasetTableTaskLog.setInfo(datasetTableTaskLog.getInfo() + "/n End to sync datatable: " + datasourceRequest.getTable());
                datasetTableTaskLog.setTaskStatus(TaskStatus.Completed.toString());
                datasetTableTaskLog.setEndTime(System.currentTimeMillis());
            } catch (Exception e) {
                try {
                    if (updateType.equals(DatasourceServer.UpdateType.all_scope)) {
                        dropEngineTable(TableUtils.tmpName(datasourceRequest.getTable()));
                    }
                } catch (Exception ignore) {
                }
                datasetTableTaskLog.setInfo(datasetTableTaskLog.getInfo() + "/n Failed to sync datatable: " + datasourceRequest.getTable() + ", " + e.getMessage());
                datasetTableTaskLog.setTaskStatus(TaskStatus.Error.toString());
                datasetTableTaskLog.setEndTime(System.currentTimeMillis());

                io.crest.utils.LogUtil.error(e.getMessage(), e);
            } finally {
                datasourceTaskServer.saveLog(datasetTableTaskLog);
            }
        }
    }

    private void touchQueuedHeartbeat(Long taskId, boolean queued) {
        if (queued) {
            datasourceTaskServer.touchQueuedTaskHeartbeat(taskId);
        }
    }

    // 重置数据源任务状态为等待执行
    private void updateDsTaskStatus(Long datasourceId) {
        UpdateWrapper<CoreDatasource> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", datasourceId);
        CoreDatasource record = new CoreDatasource();
        record.setTaskStatus(TaskStatus.WaitingForExecution.name());
        datasourceMapper.update(record, updateWrapper);
    }

    // 手动抽取指定数据源表
    public void extractDataForTable(Long datasourceId, String name, String tableName, String type) {
        DatasourceServer.UpdateType updateType = DatasourceServer.UpdateType.valueOf(type);
        CoreDatasource coreDatasource = datasourceMapper.selectById(datasourceId);
        if (coreDatasource == null) {
            LogUtil.error("Can not find datasource: " + datasourceId);
            return;
        }
        CoreDatasourceTaskLog datasetTableTaskLog = datasourceTaskServer.initTaskLog(datasourceId, null, tableName, MANUAL.toString());

        DatasourceRequest datasourceRequest = new DatasourceRequest();
        datasourceRequest.setDatasource(transDTO(coreDatasource));
        List<DatasetTableDTO> tables = (List<DatasetTableDTO>) datasourceServer.invokeMethod(coreDatasource.getType(), "getApiTables", DatasourceRequest.class, datasourceRequest);
        for (DatasetTableDTO api : tables) {
            if (api.getTableName().equalsIgnoreCase(tableName)) {
                datasourceRequest.setTable(api.getTableName());
                List<TableField> tableFields = (List<TableField>) datasourceServer.invokeMethod(coreDatasource.getType(), "getTableFields", DatasourceRequest.class, datasourceRequest);
                try {
                    datasetTableTaskLog.setInfo(datasetTableTaskLog.getInfo() + "/n Begin to sync datatable: " + datasourceRequest.getTable());
                    createEngineTable(datasourceRequest.getTable(), tableFields);
                    if (updateType.equals(DatasourceServer.UpdateType.all_scope)) {
                        createEngineTable(TableUtils.tmpName(datasourceRequest.getTable()), tableFields);
                    }
                    extractApiData(datasourceRequest, updateType, tableFields);
                    if (updateType.equals(DatasourceServer.UpdateType.all_scope)) {
                        replaceTable(datasourceRequest.getTable());
                    }
                    datasetTableTaskLog.setInfo(datasetTableTaskLog.getInfo() + "/n End to sync datatable: " + datasourceRequest.getTable());
                    datasetTableTaskLog.setTaskStatus(TaskStatus.Completed.name());
                    datasetTableTaskLog.setEndTime(System.currentTimeMillis());
                } catch (Exception e) {
                    try {
                        if (updateType.equals(DatasourceServer.UpdateType.all_scope)) {
                            dropEngineTable(TableUtils.tmpName(datasourceRequest.getTable()));
                        }
                    } catch (Exception ignore) {
                    }
                    datasetTableTaskLog.setInfo(datasetTableTaskLog.getInfo() + "/n Failed to sync datatable: " + datasourceRequest.getTable() + ", " + e.getMessage());
                    datasetTableTaskLog.setTaskStatus(TaskStatus.Error.name());
                    datasetTableTaskLog.setEndTime(System.currentTimeMillis());
                } finally {
                    datasourceTaskServer.saveLog(datasetTableTaskLog);
                }
            }
        }
    }

    // 分页写入 API 抽取数据到引擎表
    private void extractApiData(DatasourceRequest datasourceRequest, DatasourceServer.UpdateType extractType, List<TableField> tableFields) throws Exception {
        extractApiData(datasourceRequest, extractType, tableFields, null, false);
    }

    private void extractApiData(DatasourceRequest datasourceRequest, DatasourceServer.UpdateType extractType, List<TableField> tableFields, Long taskId, boolean queued) throws Exception {
        touchQueuedHeartbeat(taskId, queued);
        Map<String, Object> result = (Map<String, Object>) datasourceServer.invokeMethod(datasourceRequest.getDatasource().getType(), "fetchApiResultField", DatasourceRequest.class, datasourceRequest);
        List<String[]> dataList = (List<String[]>) result.get("dataList");
        CoreEngine engine = engineManage.info();
        EngineRequest engineRequest = new EngineRequest();
        engineRequest.setEngine(engine);
        EngineProvider engineProvider = ProviderUtil.getEngineProvider(engine.getType());
        int pageNumber = 1000; // 每批向引擎插入 1000 条数据。
        int totalPage;
        if (dataList.size() % pageNumber > 0) {
            totalPage = dataList.size() / pageNumber + 1;
        } else {
            totalPage = dataList.size() / pageNumber;
        }
        for (int page = 1; page <= totalPage; page++) {
            touchQueuedHeartbeat(taskId, queued);
            engineRequest.setQuery(engineProvider.insertSql(DatasourceConfiguration.DatasourceType.API.name(), datasourceRequest.getTable(), extractType, dataList, page, pageNumber, tableFields));
            calciteProvider.exec(engineRequest);
        }
    }

    // 分页写入 Excel 抽取数据到引擎表
    private void extractExcelData(DatasourceRequest datasourceRequest, DatasourceServer.UpdateType extractType, List<TableField> tableFields) throws Exception {
        extractExcelData(datasourceRequest, extractType, tableFields, null, false);
    }

    private void extractExcelData(DatasourceRequest datasourceRequest, DatasourceServer.UpdateType extractType, List<TableField> tableFields, Long taskId, boolean queued) throws Exception {
        touchQueuedHeartbeat(taskId, queued);
        ExcelUtils excelUtils = new ExcelUtils();
        List<String[]> dataList = excelUtils.fetchDataList(datasourceRequest);
        CoreEngine engine = engineManage.info();
        EngineRequest engineRequest = new EngineRequest();
        engineRequest.setEngine(engine);
        EngineProvider engineProvider = ProviderUtil.getEngineProvider(engine.getType());
        int pageNumber = 1000; // 每批向引擎插入 1000 条数据。
        int totalPage;
        if (dataList.size() % pageNumber > 0) {
            totalPage = dataList.size() / pageNumber + 1;
        } else {
            totalPage = dataList.size() / pageNumber;
        }
        for (int page = 1; page <= totalPage; page++) {
            touchQueuedHeartbeat(taskId, queued);
            engineRequest.setQuery(engineProvider.insertSql(DatasourceConfiguration.DatasourceType.Excel.name(), datasourceRequest.getTable(), extractType, dataList, page, pageNumber, tableFields));
            calciteProvider.exec(engineRequest);
        }
    }

    // 用临时表替换正式引擎表
    private void replaceTable(String tableName) throws Exception {
        CoreEngine engine = engineManage.info();
        EngineRequest engineRequest = new EngineRequest();
        engineRequest.setEngine(engine);
        EngineProvider engineProvider = ProviderUtil.getEngineProvider(engine.getType());
        String[] replaceTableSql = engineProvider.replaceTable(tableName).split(";");
        for (int i = 0; i < replaceTableSql.length; i++) {
            if (StringUtils.isNotEmpty(replaceTableSql[i])) {
                engineRequest.setQuery(replaceTableSql[i]);
                try {
                    calciteProvider.exec(engineRequest);
                } catch (Exception e) {
                    if (!isDropStatement(replaceTableSql[i]) || !isTableMissing(e)) {
                        throw e;
                    }
                }
            }
        }
    }

    // 在当前引擎中创建数据表
    public void createEngineTable(String tableName, List<TableField> tableFields) throws Exception {
        CoreEngine engine = engineManage.info();
        EngineRequest engineRequest = new EngineRequest();
        engineRequest.setEngine(engine);
        EngineProvider engineProvider = ProviderUtil.getEngineProvider(engine.getType());
        engineRequest.setQuery(engineProvider.createTableSql(tableName, tableFields, engine));
        try {
            calciteProvider.exec(engineRequest);
        } catch (Exception e) {
            if (!isTableAlreadyExists(e)) {
                throw e;
            }
        }
    }

    // 删除当前引擎中的数据表
    public void dropEngineTable(String tableName) throws Exception {
        CoreEngine engine = engineManage.info();
        EngineRequest engineRequest = new EngineRequest();
        engineRequest.setEngine(engine);
        EngineProvider engineProvider = ProviderUtil.getEngineProvider(engine.getType());
        engineRequest.setQuery(engineProvider.dropTable(tableName));
        try {
            calciteProvider.exec(engineRequest);
        } catch (Exception e) {
            if (!isTableMissing(e)) {
                throw e;
            }
        }
    }

    private boolean isTableMissing(Exception e) {
        String message = StringUtils.defaultString(e.getMessage()).toUpperCase(java.util.Locale.ROOT);
        return message.contains("ORA-00942") || message.contains("DOES NOT EXIST");
    }

    private boolean isDropStatement(String sql) {
        return StringUtils.defaultString(sql).stripLeading()
                .toUpperCase(java.util.Locale.ROOT)
                .startsWith("DROP TABLE");
    }

    private boolean isTableAlreadyExists(Exception e) {
        String message = StringUtils.defaultString(e.getMessage()).toUpperCase(java.util.Locale.ROOT);
        return message.contains("ORA-00955") || message.contains("ALREADY EXISTS");
    }

    // 新增或更新数据源同步调度
    public void addSchedule(CoreDatasourceTask datasourceTask) throws CrestException {
        if (Strings.CI.equals(datasourceTask.getSyncRate(), DatasourceTaskServer.ScheduleType.RIGHTNOW.toString())) {
            scheduleManager.addOrUpdateSingleJob(new JobKey(datasourceTask.getId().toString(), datasourceTask.getDsId().toString()), new TriggerKey(datasourceTask.getId().toString(), datasourceTask.getDsId().toString()), ExtractDataJob.class, new Date(datasourceTask.getStartTime()), scheduleManager.getDefaultJobDataMap(datasourceTask.getDsId().toString(), datasourceTask.getCron(), datasourceTask.getId().toString(), datasourceTask.getUpdateType()));
        } else {
            Date endTime;
            if (datasourceTask.getEndTime() == null || datasourceTask.getEndTime() == 0) {
                endTime = null;
            } else {
                endTime = new Date(datasourceTask.getEndTime());
                if (endTime.before(new Date())) {
                    deleteSchedule(datasourceTask);
                    return;
                }
            }

            scheduleManager.addOrUpdateCronJob(new JobKey(datasourceTask.getId().toString(), datasourceTask.getDsId().toString()), new TriggerKey(datasourceTask.getId().toString(), datasourceTask.getDsId().toString()), ExtractDataJob.class, datasourceTask.getCron(), new Date(datasourceTask.getStartTime()), endTime, scheduleManager.getDefaultJobDataMap(datasourceTask.getDsId().toString(), datasourceTask.getCron(), datasourceTask.getId().toString(), datasourceTask.getUpdateType()));
        }
    }

    // 删除数据源同步调度
    public void deleteSchedule(CoreDatasourceTask datasourceTask) {
        scheduleManager.removeJob(new JobKey(datasourceTask.getId().toString(), datasourceTask.getDsId().toString()), new TriggerKey(datasourceTask.getId().toString(), datasourceTask.getDsId().toString()));
    }

    // 立即触发数据源同步调度
    public void fireNow(CoreDatasourceTask datasourceTask) throws Exception {
        scheduleManager.fireNow(datasourceTask.getId().toString(), datasourceTask.getDsId().toString());
    }

    // 将数据源实体转换为 DTO
    private DatasourceDTO transDTO(CoreDatasource record) {
        DatasourceDTO datasourceDTO = new DatasourceDTO();
        BeanUtils.copyBean(datasourceDTO, record);
        return datasourceDTO;
    }
}
