package io.crest.dataset.sync;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.crest.api.dataset.dto.DatasetSyncTaskDTO;
import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.commons.constants.TaskStatus;
import io.crest.dataset.dao.auto.entity.CoreDatasetGroup;
import io.crest.dataset.dao.auto.entity.CoreDatasetSyncTask;
import io.crest.dataset.dao.auto.entity.CoreDatasetSyncTaskLog;
import io.crest.dataset.dao.auto.mapper.CoreDatasetGroupMapper;
import io.crest.dataset.manage.DatasetGroupManage;
import io.crest.dataset.sync.queue.DatasetSyncTaskQueueService;
import io.crest.datasource.dao.auto.entity.CoreEngine;
import io.crest.datasource.manage.EngineManage;
import io.crest.datasource.provider.EngineProvider;
import io.crest.datasource.provider.ProviderUtil;
import io.crest.datasource.request.EngineRequest;
import io.crest.datasource.server.DatasourceServer;
import io.crest.datasource.server.DatasourceTaskServer;
import io.crest.engine.utils.Utils;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasourceRequest;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import io.crest.extensions.datasource.dto.TableField;
import io.crest.extensions.datasource.factory.ProviderFactory;
import io.crest.extensions.datasource.provider.Provider;
import io.crest.extensions.datasource.vo.DatasourceConfiguration;
import io.crest.datasource.provider.CalciteProvider;
import io.crest.utils.CommonThreadPool;
import io.crest.utils.LogUtil;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 数据集缓存同步执行器，负责把单源数据集查询结果写入引擎缓存表，并维护全量、增量和校验结果
 * 任务配置、调度注册和日志持久化由 DatasetSyncTaskManage 管理，本类只编排单次执行过程
 */
@Component
@SuppressWarnings("unchecked")
public class DatasetSyncManage {

    private static final int PAGE_SIZE = 1000;
    private static final int FETCH_RETRY_TIMES = 3;
    private static final long PROGRESS_UPDATE_INTERVAL_MILLIS = 10_000L;

    @Resource
    private DatasetSyncTaskManage taskManage;
    @Resource
    private DatasetGroupManage datasetGroupManage;
    @Resource
    private DatasetSyncSupportValidator supportValidator;
    @Resource
    private EngineManage engineManage;
    @Resource
    private CalciteProvider calciteProvider;
    @Resource
    private CoreDatasetGroupMapper datasetGroupMapper;
    @Resource
    private CommonThreadPool commonThreadPool;
    @Resource
    private DatasetSyncTaskQueueService queueService;

    /**
     * 手动触发数据集同步。未配置任务时会创建一次性任务，再异步进入执行流程
     */
    public DatasetSyncTaskDTO executeNow(Long datasetGroupId) throws Exception {
        supportValidator.assertSupported(datasetGroupId);
        CoreDatasetSyncTask task = taskManage.selectByDatasetGroupId(datasetGroupId);
        if (task == null) {
            DatasetSyncTaskDTO dto = new DatasetSyncTaskDTO();
            dto.setDatasetGroupId(datasetGroupId);
            dto.setUpdateType(DatasetSyncTaskManage.DEFAULT_UPDATE_TYPE);
            dto.setSyncRate(DatasourceTaskServer.ScheduleType.RIGHTNOW.name());
            task = taskManage.selectById(taskManage.save(dto).getId());
        }
        Long taskId = task.getId();
        if (queueEnabled()) {
            long now = System.currentTimeMillis();
            if (taskManage.markQueuedTaskEnqueued(taskId, now, queueService.enqueueRetryMillis())) {
                queueService.enqueueDatasetSyncTask(task.getDatasetGroupId(), taskId,
                        DatasourceTaskServer.ScheduleType.MANUAL.name(), true, now);
            }
            return taskManage.task(datasetGroupId);
        }
        commonThreadPool.addTask(() -> {
            try {
                execute(datasetGroupId, taskId, DatasourceTaskServer.ScheduleType.MANUAL.name(), null, true);
            } catch (Exception e) {
                LogUtil.error(e.getMessage(), e);
            }
        });
        return taskManage.task(datasetGroupId);
    }

    /**
     * Quartz 调度入口，按任务 ID 或数据集 ID 找到任务后执行，异常只记录日志
     */
    public void execute(Long datasetGroupId, Long taskId, JobExecutionContext context) {
        try {
            CoreDatasetSyncTask task = taskManage.selectById(taskId);
            if (task == null) {
                task = taskManage.selectByDatasetGroupId(datasetGroupId);
            }
            if (task == null) {
                return;
            }
            if (Strings.CI.equalsAny(task.getTaskStatus(), TaskStatus.Stopped.name(), TaskStatus.Suspend.name())) {
                return;
            }
            if (queueEnabled()) {
                long now = System.currentTimeMillis();
                if (taskManage.markQueuedTaskEnqueued(task.getId(), now, queueService.enqueueRetryMillis())) {
                    queueService.enqueueDatasetSyncTask(task.getDatasetGroupId(), task.getId(), task.getSyncRate(), false, now);
                }
                return;
            }
            execute(task.getDatasetGroupId(), task.getId(), task.getSyncRate(), context, false);
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
        }
    }

    /**
     * Worker 消费 Redis 队列后的执行入口，异常由任务日志和任务状态记录。
     */
    public void executeQueued(Long datasetGroupId, Long taskId, String triggerType, boolean manual) {
        executeQueued(datasetGroupId, taskId, triggerType, manual, null);
    }

    /**
     * Worker 消费 Redis 队列后的执行入口，记录执行者用于多副本排障和恢复。
     */
    public void executeQueued(Long datasetGroupId, Long taskId, String triggerType, boolean manual, String workerId) {
        executeQueued(datasetGroupId, taskId, triggerType, manual, workerId, null);
    }

    /**
     * Worker 消费 Redis 队列后的执行入口，投递时间用于过滤已经完成的旧消息。
     */
    public void executeQueued(Long datasetGroupId, Long taskId, String triggerType, boolean manual, String workerId,
                              Long enqueueTime) {
        try {
            execute(datasetGroupId, taskId, triggerType, null, manual, workerId, enqueueTime);
        } catch (Exception e) {
            LogUtil.error("Dataset sync queued task failed: " + taskId, e);
        }
    }

    /**
     * 执行一次同步任务，负责抢占执行锁、选择全量或增量策略、记录日志并回写任务状态
     */
    private void execute(Long datasetGroupId, Long taskId, String triggerType, JobExecutionContext context, boolean manual) throws Exception {
        execute(datasetGroupId, taskId, triggerType, context, manual, null);
    }

    /**
     * 执行一次同步任务，Worker 路径会传入执行者标识用于数据库 CAS 记录。
     */
    private void execute(Long datasetGroupId, Long taskId, String triggerType, JobExecutionContext context,
                         boolean manual, String workerId) throws Exception {
        execute(datasetGroupId, taskId, triggerType, context, manual, workerId, null);
    }

    private void execute(Long datasetGroupId, Long taskId, String triggerType, JobExecutionContext context,
                         boolean manual, String workerId, Long enqueueTime) throws Exception {
        CoreDatasetSyncTask task = taskManage.selectById(taskId);
        if (task == null) {
            return;
        }
        if (!manual && Strings.CI.equalsAny(task.getTaskStatus(), TaskStatus.Stopped.name(), TaskStatus.Suspend.name())) {
            return;
        }
        if (taskManage.markUnderExecution(task, workerId, enqueueTime)) {
            LogUtil.info("Skip dataset sync task due to exist another running task, dataset ID: " + datasetGroupId);
            return;
        }

        CoreDatasetSyncTaskLog log = taskManage.initLog(task, triggerType);
        TaskStatus status = TaskStatus.Completed;
        SyncResult result = new SyncResult();
        long syncStartTime = System.currentTimeMillis();
        try {
            SyncContext syncContext = prepareContext(datasetGroupId, task);
            String schemaHash = DatasetSyncUtils.schemaHash(syncContext.checkedFields);
            result.schemaHash = schemaHash;
            updateProgress(log, result, "同步中，正在准备缓存表", true);
            DatasourceServer.UpdateType updateType = DatasourceServer.UpdateType.valueOf(
                    StringUtils.defaultIfBlank(task.getUpdateType(), DatasetSyncTaskManage.DEFAULT_UPDATE_TYPE)
            );
            boolean runFullCalibration = DatasetSyncUtils.shouldRunFullCalibration(task, System.currentTimeMillis());
            if (updateType == DatasourceServer.UpdateType.add_scope
                    && DatasetSyncUtils.canRunIncremental(task, schemaHash)
                    && cacheTableExists(syncContext, DatasetSyncUtils.cacheTableName(datasetGroupId))
                    && !runFullCalibration) {
                syncIncremental(syncContext, task, syncStartTime, log, result);
            } else {
                syncFull(syncContext, task, syncStartTime, log, result);
            }
            result.schemaHash = schemaHash;
            if (Objects.equals(task.getVerifyEnabled(), 0)) {
                log.setInfo("同步完成");
            } else if (result.reconcileResult != null && !Strings.CS.equals(result.reconcileResult.status(), "PASSED")) {
                log.setInfo("同步完成；" + result.reconcileResult.message());
            } else {
                log.setInfo("同步完成");
            }
            updateDatasetSyncStatus(datasetGroupId, TaskStatus.Completed, context);
        } catch (Exception e) {
            status = TaskStatus.Error;
            log.setInfo(StringUtils.defaultString(e.getMessage()));
            updateDatasetSyncStatus(datasetGroupId, TaskStatus.Error, context);
            if (manual) {
                throw e;
            }
        } finally {
            log.setTaskStatus(status.name());
            log.setEndTime(System.currentTimeMillis());
            log.setRowCount(result.rowCount);
            log.setUpdateType(result.fullSync ? DatasourceServer.UpdateType.all_scope.name() : task.getUpdateType());
            taskManage.updateLog(log);
            taskManage.finishTask(task, status, result.incrementalLastValue, result.schemaHash,
                    result.fullSync, result.reconcileResult, result.sourceRowCount, result.cacheRowCount,
                    status == TaskStatus.Completed ? null : log.getInfo());
        }
    }

    /**
     * 准备同步上下文，校验数据集模式、单源约束、SQL 参数限制和可同步字段
     */
    @SuppressWarnings("unchecked")
    private SyncContext prepareContext(Long datasetGroupId, CoreDatasetSyncTask task) throws Exception {
        DatasetSyncSupportValidator.SupportContext support = supportValidator.assertSupported(datasetGroupId);
        DatasetGroupInfoDTO dataset = support.dataset();
        Map<String, Object> sqlMap = support.sqlMap();
        Map<Long, DatasourceSchemaDTO> dsMap = support.dsMap();
        DatasourceSchemaDTO datasource = dsMap.entrySet().iterator().next().getValue();
        String sourceSql = Utils.replaceSchemaAlias((String) sqlMap.get("sql"), dsMap);
        List<DatasetTableFieldDTO> syncFields = support.syncFields();
        List<TableField> tableFields = DatasetSyncUtils.toEngineTableFields(syncFields);
        CoreEngine engine = engineManage.info();
        EngineProvider engineProvider = ProviderUtil.getEngineProvider(engine.getType());
        Provider sourceProvider = ProviderFactory.getProvider(datasource.getType());
        return new SyncContext(dataset, sourceSql, dsMap, sourceProvider, engine, engineProvider, syncFields, tableFields,
                syncQueryTimeoutSeconds(task));
    }

    private boolean queueEnabled() {
        return queueService != null && queueService.enabled();
    }

    private int syncQueryTimeoutSeconds(CoreDatasetSyncTask task) {
        int timeoutMinutes = Objects.requireNonNullElse(
                task == null ? null : task.getTaskTimeoutMinutes(),
                DatasetSyncUtils.DEFAULT_TASK_TIMEOUT_MINUTES
        );
        if (timeoutMinutes <= 0) {
            return 0;
        }
        long timeoutSeconds = timeoutMinutes * 60L;
        return timeoutSeconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) timeoutSeconds;
    }

    private void updateProgress(CoreDatasetSyncTaskLog log, SyncResult result, String info, boolean force) {
        if (log == null || result == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - result.lastProgressUpdateTime < PROGRESS_UPDATE_INTERVAL_MILLIS) {
            return;
        }
        log.setRowCount(result.rowCount);
        log.setInfo(info);
        taskManage.updateLog(log);
        result.lastProgressUpdateTime = now;
    }

    /**
     * 校验数据集存在且已开启同步模式，缓存同步只面向同步模式数据集
     */
    private DatasetGroupInfoDTO assertDatasetSyncModeEnabled(Long datasetGroupId) throws Exception {
        DatasetGroupInfoDTO dataset = datasetGroupManage.getForCount(datasetGroupId);
        if (dataset == null || !Strings.CI.equals(dataset.getNodeType(), "dataset")) {
            CrestException.throwException("数据集不存在");
        }
        if (!Objects.equals(dataset.getMode(), 1)) {
            CrestException.throwException("请先开启数据集同步模式");
        }
        return dataset;
    }

    /**
     * 执行全量同步。数据先写入临时缓存表，成功后再替换正式缓存表
     */
    private SyncResult syncFull(SyncContext context, CoreDatasetSyncTask task, long syncStartTime,
                                CoreDatasetSyncTaskLog log, SyncResult result) throws Exception {
        assertTaskRunnable(task, syncStartTime);
        String tableName = DatasetSyncUtils.cacheTableName(context.dataset.getId());
        createEngineTable(context, tableName);
        dropEngineTable(context, DatasetSyncUtils.tmpCacheTableName(context.dataset.getId()));
        createEngineTable(context, DatasetSyncUtils.tmpCacheTableName(context.dataset.getId()));

        DatasetTableFieldDTO incrementalField = incrementalField(context.dataset, task);
        int incrementalIndex = incrementalIndex(context.checkedFields, incrementalField);
        updateProgress(log, result, "同步中，正在读取源数据", true);
        int offset = 0;
        try {
            while (true) {
                assertTaskRunnable(task, syncStartTime);
                touchHeartbeat(task);
                List<String[]> rows = fetchSourcePage(context, context.sourceSql, offset);
                insertRows(context, tableName, DatasourceServer.UpdateType.all_scope, rows);
                result.rowCount += rows.size();
                result.incrementalLastValue = maxWatermark(result.incrementalLastValue, rows, incrementalIndex, incrementalField);
                updateProgress(log, result, "同步中，已写入 " + result.rowCount + " 行", false);
                if (rows.size() < PAGE_SIZE) {
                    break;
                }
                offset += PAGE_SIZE;
            }
            assertTaskRunnable(task, syncStartTime);
            replaceTable(context, tableName);
            result.fullSync = true;
            updateProgress(log, result, "同步中，正在校验数据一致性", true);
            reconcile(context, tableName, incrementalField, task, result);
            return result;
        } catch (Exception e) {
            dropEngineTable(context, DatasetSyncUtils.tmpCacheTableName(context.dataset.getId()));
            throw e;
        }
    }

    /**
     * 执行增量同步。先保留旧缓存中未超过水位的行，再追加源端水位之后的数据
     */
    private SyncResult syncIncremental(SyncContext context, CoreDatasetSyncTask task, long syncStartTime,
                                       CoreDatasetSyncTaskLog log, SyncResult result) throws Exception {
        assertTaskRunnable(task, syncStartTime);
        DatasetTableFieldDTO incrementalField = incrementalField(context.dataset, task);
        if (incrementalField == null || !DatasetSyncUtils.isWatermarkCompatible(incrementalField, task.getIncrementalLastValue())) {
            return syncFull(context, task, syncStartTime, log, result);
        }
        int incrementalIndex = incrementalIndex(context.checkedFields, incrementalField);
        if (incrementalIndex < 0) {
            CrestException.throwException("增量字段必须是数据集已选中的普通字段");
        }

        String tableName = DatasetSyncUtils.cacheTableName(context.dataset.getId());
        createEngineTable(context, tableName);
        dropEngineTable(context, DatasetSyncUtils.tmpCacheTableName(context.dataset.getId()));
        createEngineTable(context, DatasetSyncUtils.tmpCacheTableName(context.dataset.getId()));
        DatasourceSchemaDTO datasource = context.dsMap.entrySet().iterator().next().getValue();
        DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(datasource.getType());
        String fieldName = StringUtils.defaultIfBlank(incrementalField.getEngineFieldName(), incrementalField.getFieldShortName());
        updateProgress(log, result, "同步中，正在保留历史缓存数据", true);
        copyRetainedCacheRows(context, tableName, incrementalField, task.getIncrementalLastValue());
        String predicate = DatasetSyncUtils.buildIncrementalPredicate(
                incrementalField,
                task.getIncrementalLastValue(),
                datasourceType.getPrefix(),
                datasourceType.getSuffix(),
                true,
                datasource.getType()
        );
        String incrementalSql = "SELECT * FROM (" + context.sourceSql + ") sync_src WHERE " + predicate
                + " ORDER BY " + incrementalOrderBy(context.checkedFields, fieldName, datasourceType);

        result.incrementalLastValue = task.getIncrementalLastValue();
        updateProgress(log, result, "同步中，正在读取增量数据", true);
        int offset = 0;
        try {
            while (true) {
                assertTaskRunnable(task, syncStartTime);
                touchHeartbeat(task);
                List<String[]> rows = fetchSourcePage(context, incrementalSql, offset);
                insertRows(context, tableName, DatasourceServer.UpdateType.all_scope, rows);
                result.rowCount += rows.size();
                result.incrementalLastValue = maxWatermark(result.incrementalLastValue, rows, incrementalIndex, incrementalField);
                updateProgress(log, result, "同步中，已写入 " + result.rowCount + " 行", false);
                if (rows.size() < PAGE_SIZE) {
                    break;
                }
                offset += PAGE_SIZE;
            }
            assertTaskRunnable(task, syncStartTime);
            replaceTable(context, tableName);
            result.fullSync = false;
            updateProgress(log, result, "同步中，正在校验数据一致性", true);
            reconcile(context, tableName, incrementalField, task, result);
            return result;
        } catch (Exception e) {
            dropEngineTable(context, DatasetSyncUtils.tmpCacheTableName(context.dataset.getId()));
            throw e;
        }
    }

    /**
     * 按分页从源库读取数据，瞬时连接异常会有限次数重试
     */
    private List<String[]> fetchSourcePage(SyncContext context, String sql, int offset) {
        for (int attempt = 1; attempt <= FETCH_RETRY_TIMES; attempt++) {
            try {
                DatasourceSchemaDTO datasource = context.dsMap.entrySet().iterator().next().getValue();
                DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(datasource.getType());
                String pageSql = DatasetSyncUtils.buildSourcePageSql(
                        datasource.getType(),
                        sql,
                        PAGE_SIZE,
                        offset,
                        context.checkedFields,
                        datasourceType.getPrefix(),
                        datasourceType.getSuffix()
                );
                DatasourceRequest request = new DatasourceRequest();
                request.setDsList(context.dsMap);
                request.setIsCross(false);
                request.setReadOnly(true);
                request.setQueryTimeout(context.queryTimeoutSeconds);
                request.setQuery(pageSql);
                Map<String, Object> data = context.sourceProvider.fetchResultField(request);
                return (List<String[]>) data.get("data");
            } catch (CrestException e) {
                if (attempt >= FETCH_RETRY_TIMES || !isTransientDatasourceConnection(e)) {
                    throw e;
                }
                LogUtil.warn("Retry dataset sync source fetch after datasource connection exception, attempt " + attempt + ": " + e.getMessage());
                sleepBeforeRetry(attempt);
            }
        }
        return List.of();
    }

    /**
     * 判断源库异常是否属于可重试的连接类异常
     */
    private boolean isTransientDatasourceConnection(CrestException e) {
        return Strings.CI.contains(e.getMessage(), "Datasource connection exception");
    }

    /**
     * 按重试次数递增等待时间，中断时恢复线程中断标记并抛出业务异常
     */
    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(attempt * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            CrestException.throwException("数据集同步重试被中断");
        }
    }

    /**
     * 将一批源端行写入引擎缓存表，具体 INSERT SQL 由引擎 provider 生成
     */
    private void insertRows(SyncContext context, String tableName, DatasourceServer.UpdateType updateType, List<String[]> rows) throws Exception {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        EngineRequest request = new EngineRequest();
        request.setEngine(context.engine);
        request.setQueryTimeout(context.queryTimeoutSeconds);
        DatasourceSchemaDTO datasource = context.dsMap.entrySet().iterator().next().getValue();
        request.setQuery(context.engineProvider.insertSql(
                datasource.getType(),
                tableName,
                updateType,
                rows,
                1,
                rows.size(),
                context.tableFields
        ));
        calciteProvider.exec(request);
    }

    /**
     * 增量同步时把旧缓存中小于当前水位的行复制到临时表
     */
    private void copyRetainedCacheRows(SyncContext context, String tableName, DatasetTableFieldDTO incrementalField, String lastValue) throws Exception {
        DatasourceConfiguration.DatasourceType engineType = DatasourceConfiguration.DatasourceType.valueOf(context.engine.getType());
        String predicate = DatasetSyncUtils.buildCacheWatermarkPredicate(
                incrementalField,
                lastValue,
                engineType.getPrefix(),
                engineType.getSuffix(),
                "<",
                context.engine.getType()
        );
        EngineRequest request = new EngineRequest();
        request.setEngine(context.engine);
        request.setQueryTimeout(context.queryTimeoutSeconds);
        request.setQuery("INSERT INTO " + DatasetSyncUtils.quote(DatasetSyncUtils.tmpCacheTableName(context.dataset.getId()), engineType.getPrefix(), engineType.getSuffix())
                + " SELECT * FROM " + DatasetSyncUtils.quote(tableName, engineType.getPrefix(), engineType.getSuffix()) + " WHERE " + predicate);
        calciteProvider.exec(request);
    }

    /**
     * 在引擎库创建缓存表，字段结构来自当前数据集已选中的普通字段
     */
    private void createEngineTable(SyncContext context, String tableName) throws Exception {
        EngineRequest request = new EngineRequest();
        request.setEngine(context.engine);
        request.setQueryTimeout(context.queryTimeoutSeconds);
        request.setQuery(context.engineProvider.createTableSql(tableName, context.tableFields, context.engine));
        try {
            calciteProvider.exec(request);
        } catch (Exception e) {
            if (!isTableAlreadyExists(e)) {
                throw e;
            }
        }
    }

    /**
     * 校验缓存表是否可访问，失败时允许调用方回退到全量同步
     */
    private boolean cacheTableExists(SyncContext context, String tableName) {
        try {
            DatasourceConfiguration.DatasourceType engineType = DatasourceConfiguration.DatasourceType.valueOf(context.engine.getType());
            EngineRequest request = new EngineRequest();
            request.setEngine(context.engine);
            request.setQueryTimeout(context.queryTimeoutSeconds);
            request.setQuery("SELECT 1 FROM " + DatasetSyncUtils.quote(tableName, engineType.getPrefix(), engineType.getSuffix()) + " WHERE 1 = 0");
            calciteProvider.exec(request);
            return true;
        } catch (Exception e) {
            LogUtil.warn("Dataset sync cache table is not available, fallback to full sync: " + tableName + ", " + e.getMessage());
            return false;
        }
    }

    /**
     * 对比源端和缓存端的行数及水位，校验失败不会中断同步，只写入告警结果
     */
    private void reconcile(SyncContext context, String tableName, DatasetTableFieldDTO incrementalField,
                           CoreDatasetSyncTask task, SyncResult result) {
        if (Objects.equals(task.getVerifyEnabled(), 0)) {
            return;
        }
        try {
            result.sourceRowCount = sourceLong(context, "SELECT COUNT(*) FROM (" + context.sourceSql + ") SYNC_VERIFY_SRC");
            result.cacheRowCount = engineLong(context, "SELECT COUNT(*) FROM " + quotedEngineTable(context, tableName));
            String sourceWatermark = null;
            String cacheWatermark = null;
            if (incrementalField != null) {
                DatasourceSchemaDTO datasource = context.dsMap.entrySet().iterator().next().getValue();
                DatasourceConfiguration.DatasourceType datasourceType = DatasourceConfiguration.DatasourceType.valueOf(datasource.getType());
                String fieldName = StringUtils.defaultIfBlank(incrementalField.getEngineFieldName(), incrementalField.getFieldShortName());
                sourceWatermark = sourceString(context, "SELECT MAX(" + DatasetSyncUtils.quote(fieldName, datasourceType.getPrefix(), datasourceType.getSuffix())
                        + ") FROM (" + context.sourceSql + ") SYNC_VERIFY_SRC");
                cacheWatermark = engineString(context, "SELECT MAX(" + quotedEngineColumn(context, fieldName)
                        + ") FROM " + quotedEngineTable(context, tableName));
            }
            result.reconcileResult = DatasetSyncUtils.reconcile(result.sourceRowCount, result.cacheRowCount, sourceWatermark, cacheWatermark);
        } catch (Exception e) {
            result.reconcileResult = new DatasetSyncUtils.ReconcileResult("WARNING", "数据一致性校验失败：" + e.getMessage());
        }
    }

    /**
     * 从源库读取单个数值结果
     */
    private Long sourceLong(SyncContext context, String sql) {
        String value = sourceString(context, sql);
        return StringUtils.isBlank(value) ? null : new BigDecimal(value).longValue();
    }

    /**
     * 从源库读取单个字符串结果，查询始终以只读方式执行
     */
    @SuppressWarnings("unchecked")
    private String sourceString(SyncContext context, String sql) {
        DatasourceRequest request = new DatasourceRequest();
        request.setDsList(context.dsMap);
        request.setIsCross(false);
        request.setReadOnly(true);
        request.setQueryTimeout(context.queryTimeoutSeconds);
        request.setQuery(sql);
        Map<String, Object> data = context.sourceProvider.fetchResultField(request);
        return firstCell((List<String[]>) data.get("data"));
    }

    /**
     * 从引擎库读取单个数值结果
     */
    private Long engineLong(SyncContext context, String sql) throws Exception {
        String value = engineString(context, sql);
        return StringUtils.isBlank(value) ? null : new BigDecimal(value).longValue();
    }

    /**
     * 从引擎库读取单个字符串结果
     */
    @SuppressWarnings("unchecked")
    private String engineString(SyncContext context, String sql) throws Exception {
        EngineRequest request = new EngineRequest();
        request.setEngine(context.engine);
        request.setQueryTimeout(context.queryTimeoutSeconds);
        request.setQuery(sql);
        Map<String, Object> data = calciteProvider.fetchResultField(request);
        return firstCell((List<String[]>) data.get("data"));
    }

    /**
     * 提取查询结果第一行第一列，空结果返回空值
     */
    private String firstCell(List<String[]> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0) == null || rows.get(0).length == 0) {
            return null;
        }
        return rows.get(0)[0];
    }

    /**
     * 按引擎数据库类型转义缓存表名
     */
    private String quotedEngineTable(SyncContext context, String tableName) {
        DatasourceConfiguration.DatasourceType engineType = DatasourceConfiguration.DatasourceType.valueOf(context.engine.getType());
        return DatasetSyncUtils.quote(tableName, engineType.getPrefix(), engineType.getSuffix());
    }

    /**
     * 按引擎数据库类型转义缓存字段名
     */
    private String quotedEngineColumn(SyncContext context, String columnName) {
        DatasourceConfiguration.DatasourceType engineType = DatasourceConfiguration.DatasourceType.valueOf(context.engine.getType());
        return DatasetSyncUtils.quote(columnName, engineType.getPrefix(), engineType.getSuffix());
    }

    /**
     * 删除引擎缓存表，具体 DROP SQL 由引擎 provider 生成
     */
    private void dropEngineTable(SyncContext context, String tableName) throws Exception {
        EngineRequest request = new EngineRequest();
        request.setEngine(context.engine);
        request.setQueryTimeout(context.queryTimeoutSeconds);
        request.setQuery(context.engineProvider.dropTable(tableName));
        try {
            calciteProvider.exec(request);
        } catch (Exception e) {
            if (!isTableMissing(e)) {
                throw e;
            }
        }
    }

    /**
     * 用临时缓存表替换正式缓存表，provider 可能返回多条 SQL，因此逐条执行
     */
    private void replaceTable(SyncContext context, String tableName) throws Exception {
        String[] sqls = context.engineProvider.replaceTable(tableName).split(";");
        for (String sql : sqls) {
            if (StringUtils.isBlank(sql)) {
                continue;
            }
            EngineRequest request = new EngineRequest();
            request.setEngine(context.engine);
            request.setQueryTimeout(context.queryTimeoutSeconds);
            request.setQuery(sql);
            try {
                calciteProvider.exec(request);
            } catch (Exception e) {
                if (!isDropStatement(sql) || !isTableMissing(e)) {
                    throw e;
                }
            }
        }
    }

    private boolean isDropStatement(String sql) {
        return StringUtils.defaultString(sql).stripLeading()
                .toUpperCase(java.util.Locale.ROOT)
                .startsWith("DROP TABLE");
    }

    private boolean isTableMissing(Exception e) {
        String message = StringUtils.defaultString(e.getMessage()).toUpperCase(java.util.Locale.ROOT);
        return message.contains("ORA-00942") || message.contains("DOES NOT EXIST");
    }

    private boolean isTableAlreadyExists(Exception e) {
        String message = StringUtils.defaultString(e.getMessage()).toUpperCase(java.util.Locale.ROOT);
        return message.contains("ORA-00955") || message.contains("ALREADY EXISTS");
    }

    /**
     * 解析增量字段。优先使用用户配置字段，缺省时选择已勾选的时间字段或数值字段
     */
    private DatasetTableFieldDTO incrementalField(DatasetGroupInfoDTO dataset, CoreDatasetSyncTask task) {
        if (!Strings.CI.equals(task.getUpdateType(), DatasourceServer.UpdateType.add_scope.name())
                || dataset == null || dataset.getAllFields() == null) {
            return null;
        }
        if (task.getIncrementalFieldId() != null) {
            DatasetTableFieldDTO field = dataset.getAllFields().stream()
                    .filter(item -> Objects.equals(item.getId(), task.getIncrementalFieldId()))
                    .filter(DatasetSyncUtils::isIncrementalFieldSupported)
                    .findFirst()
                    .orElse(null);
            if (field != null) {
                return field;
            }
        }
        return dataset.getAllFields().stream()
                .filter(DatasetSyncUtils::isIncrementalFieldSupported)
                .filter(field -> Objects.equals(field.getExtractedFieldType(), 1) || Objects.equals(field.getFieldType(), 1))
                .findFirst()
                .or(() -> dataset.getAllFields().stream()
                        .filter(DatasetSyncUtils::isIncrementalFieldSupported)
                        .filter(field -> Objects.equals(field.getExtractedFieldType(), 2) || Objects.equals(field.getExtractedFieldType(), 3)
                                || Objects.equals(field.getFieldType(), 2) || Objects.equals(field.getFieldType(), 3))
                        .findFirst())
                .orElse(null);
    }

    /**
     * 返回增量字段在同步字段列表中的位置，用于从源端行数据中读取水位值
     */
    private int incrementalIndex(List<DatasetTableFieldDTO> fields, DatasetTableFieldDTO incrementalField) {
        if (incrementalField == null) {
            return -1;
        }
        for (int i = 0; i < fields.size(); i++) {
            if (Objects.equals(fields.get(i).getId(), incrementalField.getId())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 构造增量查询排序字段，先按水位字段排序，再按其他字段稳定排序
     */
    private String incrementalOrderBy(List<DatasetTableFieldDTO> fields, String firstFieldName, DatasourceConfiguration.DatasourceType datasourceType) {
        LinkedHashSet<String> orderFields = new LinkedHashSet<>();
        orderFields.add(firstFieldName);
        for (DatasetTableFieldDTO field : fields) {
            String fieldName = StringUtils.defaultIfBlank(field.getEngineFieldName(), field.getFieldShortName());
            if (StringUtils.isNotBlank(fieldName)) {
                orderFields.add(fieldName);
            }
        }
        List<String> quotedFields = new ArrayList<>();
        for (String field : orderFields) {
            quotedFields.add(DatasetSyncUtils.quote(field, datasourceType.getPrefix(), datasourceType.getSuffix()));
        }
        return StringUtils.join(quotedFields, ",");
    }

    /**
     * 执行过程中校验任务仍可运行，超时、停止或删除都会中断当前同步
     */
    private void assertTaskRunnable(CoreDatasetSyncTask task, long syncStartTime) {
        if (DatasetSyncUtils.isTaskTimedOut(task, syncStartTime, System.currentTimeMillis())) {
            int timeoutMinutes = Objects.requireNonNullElse(task.getTaskTimeoutMinutes(), DatasetSyncUtils.DEFAULT_TASK_TIMEOUT_MINUTES);
            CrestException.throwException("数据集同步任务超过 " + timeoutMinutes + " 分钟未完成，已自动终止");
        }
        CoreDatasetSyncTask latest = taskManage.selectById(task.getId());
        if (!taskManage.sameExecution(task, latest)) {
            CrestException.throwException("数据集同步任务已被新的执行接管");
        }
        if (!DatasetSyncUtils.isTaskRunnable(latest)) {
            CrestException.throwException("数据集同步任务已停止或已删除");
        }
    }

    private void touchHeartbeat(CoreDatasetSyncTask task) {
        if (!taskManage.touchHeartbeat(task)) {
            CrestException.throwException("数据集同步任务已被新的执行接管");
        }
    }

    /**
     * 从一批源端行中计算新的最大增量水位
     */
    private String maxWatermark(String current, List<String[]> rows, int index, DatasetTableFieldDTO field) {
        if (index < 0 || field == null || rows == null) {
            return current;
        }
        String max = current;
        for (String[] row : rows) {
            if (row.length <= index) {
                continue;
            }
            max = maxWatermark(max, row[index], field);
        }
        return max;
    }

    /**
     * 比较两个水位值，数值字段按数值比较，其他字段按字符串顺序比较
     */
    private String maxWatermark(String current, String candidate, DatasetTableFieldDTO field) {
        if (StringUtils.isBlank(candidate)) {
            return current;
        }
        if (StringUtils.isBlank(current)) {
            return candidate;
        }
        if (Objects.equals(field.getExtractedFieldType(), 2) || Objects.equals(field.getExtractedFieldType(), 3)
                || Objects.equals(field.getFieldType(), 2) || Objects.equals(field.getFieldType(), 3)) {
            return new BigDecimal(candidate).compareTo(new BigDecimal(current)) > 0 ? candidate : current;
        }
        return candidate.compareTo(current) > 0 ? candidate : current;
    }

    /**
     * 更新数据集同步状态和调度实例标识，供数据集列表和恢复逻辑展示
     */
    private void updateDatasetSyncStatus(Long datasetGroupId, TaskStatus status, JobExecutionContext context) {
        CoreDatasetGroup record = new CoreDatasetGroup();
        record.setSyncStatus(status.name());
        record.setLastUpdateTime(System.currentTimeMillis());
        if (context != null) {
            record.setSchedulerFireInstanceId(context.getFireInstanceId());
        }
        UpdateWrapper<CoreDatasetGroup> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", datasetGroupId);
        datasetGroupMapper.update(record, wrapper);
    }

    /**
     * 单次同步执行上下文，集中保存源端 SQL、源端 provider、引擎 provider 和字段映射
     */
    private static class SyncContext {
        private final DatasetGroupInfoDTO dataset;
        private final String sourceSql;
        private final Map<Long, DatasourceSchemaDTO> dsMap;
        private final Provider sourceProvider;
        private final CoreEngine engine;
        private final EngineProvider engineProvider;
        private final List<DatasetTableFieldDTO> checkedFields;
        private final List<TableField> tableFields;
        private final int queryTimeoutSeconds;

        /**
         * 创建同步上下文，字段列表必须已经过滤为可同步的普通字段
         */
        private SyncContext(DatasetGroupInfoDTO dataset, String sourceSql, Map<Long, DatasourceSchemaDTO> dsMap,
                            Provider sourceProvider, CoreEngine engine, EngineProvider engineProvider,
                            List<DatasetTableFieldDTO> checkedFields, List<TableField> tableFields,
                            int queryTimeoutSeconds) {
            this.dataset = dataset;
            this.sourceSql = sourceSql;
            this.dsMap = dsMap;
            this.sourceProvider = sourceProvider;
            this.engine = engine;
            this.engineProvider = engineProvider;
            this.checkedFields = checkedFields;
            this.tableFields = tableFields;
            this.queryTimeoutSeconds = queryTimeoutSeconds;
        }
    }

    /**
     * 单次同步执行结果，记录写入行数、水位、结构哈希和一致性校验结果
     */
    private static class SyncResult {
        private long rowCount = 0L;
        private String incrementalLastValue;
        private String schemaHash;
        private boolean fullSync;
        private DatasetSyncUtils.ReconcileResult reconcileResult;
        private Long sourceRowCount;
        private Long cacheRowCount;
        private long lastProgressUpdateTime;
    }
}
