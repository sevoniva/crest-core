package io.crest.dataset.sync;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.crest.api.dataset.dto.DatasetSyncTaskPageVO;
import io.crest.api.dataset.dto.DatasetSyncTaskRequest;
import io.crest.api.dataset.dto.DatasetSyncTaskDTO;
import io.crest.api.dataset.union.DatasetGroupInfoDTO;
import io.crest.commons.constants.TaskStatus;
import io.crest.dataset.dao.auto.entity.CoreDatasetSyncTask;
import io.crest.dataset.dao.auto.mapper.CoreDatasetSyncTaskMapper;
import io.crest.datasource.server.DatasourceTaskServer;
import io.crest.engine.constant.ExtFieldConstant;
import io.crest.exception.CrestException;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.crest.extensions.datasource.dto.DatasourceSchemaDTO;
import io.crest.job.schedule.DatasetSyncJob;
import io.crest.job.schedule.ScheduleManager;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDataMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DatasetSyncTaskManageTest {

    @Test
    void pageListsEnabledCacheDatasetsWithLatestMetrics() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(h2("dataset_sync_task_page"));
        ReflectionTestUtils.setField(manage, "jdbcTemplate", jdbcTemplate);
        createTaskCenterTables(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO core_dataset(id, name, node_type, mode, create_by, create_time, last_update_time)
                VALUES (982004, '销售缓存数据集', 'dataset', 1, '1', 1000, 2000)
                """);
        jdbcTemplate.update("""
                INSERT INTO core_dataset(id, name, node_type, mode, create_by, create_time, last_update_time)
                VALUES (982005, '直连数据集', 'dataset', 0, '1', 1000, 2000)
                """);
        jdbcTemplate.update("""
                INSERT INTO core_dataset_sync_task(
                    id, dataset_group_id, name, update_type, sync_rate, cron, task_status, last_exec_status,
                    cache_ready, last_exec_time, last_source_row_count, last_cache_row_count, create_time, update_time
                )
                VALUES (
                    1262155861866975232, 982004, '销售缓存数据集', 'all_scope', 'SIMPLE_CRON',
                    '0 0/1 * * * ? *', 'WaitingForExecution', 'Error', 0, 1781080000000, 79, 0, 1000, 2000
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO core_dataset_sync_task_log(
                    id, dataset_group_id, task_id, start_time, end_time, task_status, row_count, info, trigger_type
                )
                VALUES (
                    1, 982004, 1262155861866975232, 1781080000000, 1781080005000,
                    'Error', 0, '数据源连接失败', 'SIMPLE_CRON'
                )
                """);

        DatasetSyncTaskPageVO page = manage.page(1, 10, new DatasetSyncTaskRequest());

        assertEquals(1, page.getTotal());
        assertEquals(1, page.getRecords().size());
        DatasetSyncTaskDTO task = page.getRecords().get(0);
        assertEquals(982004L, task.getDatasetGroupId());
        assertEquals("销售缓存数据集", task.getDatasetName());
        assertEquals(5000L, task.getDurationMillis());
        assertEquals(79L, task.getLastSourceRowCount());
        assertEquals(0L, task.getLastCacheRowCount());
        assertEquals("数据源连接失败", task.getFailureReason());
    }

    @Test
    void dependenciesReturnEnabledCacheDatasetsUsedByVisualization() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(h2("dataset_sync_dependencies"));
        ReflectionTestUtils.setField(manage, "jdbcTemplate", jdbcTemplate);
        createTaskCenterTables(jdbcTemplate);
        createVisualizationTables(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO core_dataset(id, name, node_type, mode, create_by, create_time, last_update_time)
                VALUES (982004, '销售缓存数据集', 'dataset', 1, '1', 1000, 2000)
                """);
        jdbcTemplate.update("""
                INSERT INTO core_dataset(id, name, node_type, mode, create_by, create_time, last_update_time)
                VALUES (982005, '直连数据集', 'dataset', 0, '1', 1000, 2000)
                """);
        jdbcTemplate.update("""
                INSERT INTO core_dataset_sync_task(
                    id, dataset_group_id, name, update_type, sync_rate, task_status, last_exec_status,
                    cache_ready, last_exec_time, last_source_row_count, last_cache_row_count, create_time, update_time
                )
                VALUES (
                    1262155861866975232, 982004, '销售缓存数据集', 'all_scope', 'SIMPLE_CRON',
                    'WaitingForExecution', 'Completed', 1, 1781080000000, 79, 79, 1000, 2000
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO core_visualization(id, name, type, node_type, delete_flag, create_by, org_id)
                VALUES (1259851055592771584, '经营仪表盘', 'dashboard', 'leaf', 0, '1', '1')
                """);
        jdbcTemplate.update("""
                INSERT INTO core_chart_view(id, scene_id, table_id, title)
                VALUES (1, 1259851055592771584, 982004, '销售趋势')
                """);
        jdbcTemplate.update("""
                INSERT INTO core_chart_view(id, scene_id, table_id, title)
                VALUES (2, 1259851055592771584, 982005, '直连趋势')
                """);

        List<DatasetSyncTaskDTO> dependencies = manage.dependencies(1259851055592771584L);

        assertEquals(1, dependencies.size());
        DatasetSyncTaskDTO task = dependencies.get(0);
        assertEquals(982004L, task.getDatasetGroupId());
        assertEquals("销售缓存数据集", task.getDatasetName());
        assertEquals(1, task.getCacheReady());
        assertEquals(TaskStatus.Completed.name(), task.getLastExecStatus());
    }

    @Test
    void saveScheduledTaskHandlesNullRuntimeFieldsFromExistingTask() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ScheduleManager scheduleManager = mock(ScheduleManager.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(manage, "scheduleManager", scheduleManager);

        CoreDatasetSyncTask exists = new CoreDatasetSyncTask();
        exists.setId(1262155861866975232L);
        exists.setDatasetGroupId(982004L);
        exists.setName("角色分布透视");
        exists.setUpdateType("all_scope");
        exists.setSyncRate(DatasourceTaskServer.ScheduleType.RIGHTNOW.name());
        exists.setCreateTime(1781080000000L);
        exists.setTaskStatus(TaskStatus.Stopped.name());

        when(taskMapper.selectOne(any())).thenReturn(exists);
        CoreDatasetSyncTask[] updated = new CoreDatasetSyncTask[1];
        when(taskMapper.updateById(any())).thenAnswer(invocation -> {
            updated[0] = invocation.getArgument(0);
            return 1;
        });
        when(taskMapper.selectById(1262155861866975232L)).thenAnswer(invocation -> updated[0]);
        when(scheduleManager.getDefaultJobDataMap(eq("982004"), any(), eq("1262155861866975232"), any()))
                .thenReturn(new JobDataMap());

        DatasetSyncTaskDTO request = new DatasetSyncTaskDTO();
        request.setDatasetGroupId(982004L);
        request.setName("角色分布透视");
        request.setUpdateType("add_scope");
        request.setIncrementalFieldId(984000405L);
        request.setSyncRate(DatasourceTaskServer.ScheduleType.SIMPLE_CRON.name());
        request.setSimpleCronValue(30L);
        request.setSimpleCronType("minute");
        request.setFullSyncIntervalHours(24);
        request.setVerifyEnabled(1);
        request.setCacheExpireHours(26);
        request.setTaskTimeoutMinutes(360);
        request.setFailureWarnThreshold(1);

        DatasetSyncTaskDTO saved = manage.save(request);

        ArgumentCaptor<CoreDatasetSyncTask> recordCaptor = ArgumentCaptor.forClass(CoreDatasetSyncTask.class);
        verify(taskMapper).updateById(recordCaptor.capture());
        CoreDatasetSyncTask record = recordCaptor.getValue();
        assertEquals(TaskStatus.WaitingForExecution.name(), record.getTaskStatus());
        assertEquals("add_scope", record.getUpdateType());
        assertEquals(984000405L, record.getIncrementalFieldId());
        assertFalse(record.getCron().isBlank());
        assertEquals(TaskStatus.WaitingForExecution.name(), saved.getTaskStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveRejectsUnsupportedDatasetBeforePersistingTask() throws Exception {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        DatasetSyncSupportValidator supportValidator = mock(DatasetSyncSupportValidator.class);
        ObjectProvider<DatasetSyncSupportValidator> supportValidatorProvider = mock(ObjectProvider.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(manage, "supportValidatorProvider", supportValidatorProvider);
        when(supportValidatorProvider.getIfAvailable()).thenReturn(supportValidator);
        doThrow(new RuntimeException("带 SQL 参数或未绑定占位符的数据集暂不支持缓存"))
                .when(supportValidator).assertSupported(982004L);

        DatasetSyncTaskDTO request = new DatasetSyncTaskDTO();
        request.setDatasetGroupId(982004L);
        request.setName("参数化数据集");
        request.setUpdateType("all_scope");
        request.setSyncRate(DatasourceTaskServer.ScheduleType.RIGHTNOW.name());

        CrestException exception = assertThrows(CrestException.class, () -> manage.save(request));

        assertEquals("带 SQL 参数或未绑定占位符的数据集暂不支持缓存", exception.getMessage());
        verifyNoInteractions(taskMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveRejectsAddScopeWhenNoIncrementalCandidateExists() throws Exception {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        DatasetSyncSupportValidator supportValidator = mock(DatasetSyncSupportValidator.class);
        ObjectProvider<DatasetSyncSupportValidator> supportValidatorProvider = mock(ObjectProvider.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(manage, "supportValidatorProvider", supportValidatorProvider);
        when(supportValidatorProvider.getIfAvailable()).thenReturn(supportValidator);
        when(supportValidator.assertSupported(982004L)).thenReturn(supportContext(List.of(textField(1001L))));

        DatasetSyncTaskDTO request = new DatasetSyncTaskDTO();
        request.setDatasetGroupId(982004L);
        request.setName("文本字段数据集");
        request.setUpdateType("add_scope");
        request.setSyncRate(DatasourceTaskServer.ScheduleType.RIGHTNOW.name());

        CrestException exception = assertThrows(CrestException.class, () -> manage.save(request));

        assertEquals("增量同步需要选择时间或数值字段", exception.getMessage());
        verifyNoInteractions(taskMapper);
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveRejectsAddScopeWhenSelectedFieldIsNotIncrementalCandidate() throws Exception {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        DatasetSyncSupportValidator supportValidator = mock(DatasetSyncSupportValidator.class);
        ObjectProvider<DatasetSyncSupportValidator> supportValidatorProvider = mock(ObjectProvider.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(manage, "supportValidatorProvider", supportValidatorProvider);
        when(supportValidatorProvider.getIfAvailable()).thenReturn(supportValidator);
        when(supportValidator.assertSupported(982004L)).thenReturn(supportContext(List.of(timeField(1001L), textField(1002L))));

        DatasetSyncTaskDTO request = new DatasetSyncTaskDTO();
        request.setDatasetGroupId(982004L);
        request.setName("错误增量字段数据集");
        request.setUpdateType("add_scope");
        request.setIncrementalFieldId(1002L);
        request.setSyncRate(DatasourceTaskServer.ScheduleType.RIGHTNOW.name());

        CrestException exception = assertThrows(CrestException.class, () -> manage.save(request));

        assertEquals("增量字段必须是已选中的时间或数值字段", exception.getMessage());
        verifyNoInteractions(taskMapper);
    }

    @Test
    void staleQueuedTaskRecoveryWaitsForTaskTimeout() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        long startedAt = 1782460000000L;
        long now = startedAt + 31 * 60 * 1000L;
        CoreDatasetSyncTask task = runningTask(startedAt, 360);
        Page<CoreDatasetSyncTask> page = new Page<>();
        page.setRecords(List.of(task));
        when(taskMapper.selectPage(any(), any())).thenReturn(page);

        List<CoreDatasetSyncTask> tasks = manage.listStaleQueuedTasks(now, 30 * 60 * 1000L, 100);

        assertTrue(tasks.isEmpty());
    }

    @Test
    void staleQueuedTaskRecoveryWaitsForTimeoutGrace() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        long startedAt = 1782460000000L;
        long now = startedAt + 31 * 60 * 1000L;
        CoreDatasetSyncTask task = runningTask(startedAt, 30);
        Page<CoreDatasetSyncTask> page = new Page<>();
        page.setRecords(List.of(task));
        when(taskMapper.selectPage(any(), any())).thenReturn(page);

        List<CoreDatasetSyncTask> tasks = manage.listStaleQueuedTasks(now, 30 * 60 * 1000L, 100);

        assertTrue(tasks.isEmpty());
    }

    @Test
    void staleQueuedTaskRecoveryAllowsTaskAfterTimeoutGrace() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        long startedAt = 1782460000000L;
        long now = startedAt + 61 * 60 * 1000L;
        CoreDatasetSyncTask task = runningTask(startedAt, 30);
        Page<CoreDatasetSyncTask> page = new Page<>();
        page.setRecords(List.of(task));
        when(taskMapper.selectPage(any(), any())).thenReturn(page);

        List<CoreDatasetSyncTask> tasks = manage.listStaleQueuedTasks(now, 30 * 60 * 1000L, 100);

        assertEquals(List.of(task), tasks);
    }

    @Test
    void staleQueuedTaskRecoverySkipsTaskWhenTimeoutDisabled() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        long startedAt = 1782460000000L;
        long now = startedAt + 31 * 60 * 1000L;
        CoreDatasetSyncTask task = runningTask(startedAt, 0);
        Page<CoreDatasetSyncTask> page = new Page<>();
        page.setRecords(List.of(task));
        when(taskMapper.selectPage(any(), any())).thenReturn(page);

        List<CoreDatasetSyncTask> tasks = manage.listStaleQueuedTasks(now, 30 * 60 * 1000L, 100);

        assertTrue(tasks.isEmpty());
    }

    @Test
    void staleQueuedTaskRecoveryAllowsTaskWithoutStartTime() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        CoreDatasetSyncTask task = new CoreDatasetSyncTask();
        task.setTaskStatus(TaskStatus.UnderExecution.name());
        task.setTaskTimeoutMinutes(360);
        Page<CoreDatasetSyncTask> page = new Page<>();
        page.setRecords(List.of(task));
        when(taskMapper.selectPage(any(), any())).thenReturn(page);

        List<CoreDatasetSyncTask> tasks = manage.listStaleQueuedTasks(1782460000000L, 30 * 60 * 1000L, 100);

        assertEquals(List.of(task), tasks);
    }

    @Test
    void obOracleCacheWatermarkPredicateUsesTimestampLiteralForTimeField() {
        String predicate = DatasetSyncUtils.buildCacheWatermarkPredicate(
                timeField(1001L),
                "2026-06-26 12:30:15.123",
                "\"",
                "\"",
                "<",
                "obOracle"
        );

        assertEquals("\"f_1001\" < TO_TIMESTAMP('2026-06-26 12:30:15.123', 'YYYY-MM-DD HH24:MI:SS.FF')", predicate);
    }

    @Test
    void mysqlCacheWatermarkPredicateKeepsStringLiteralForTimeField() {
        String predicate = DatasetSyncUtils.buildCacheWatermarkPredicate(
                timeField(1001L),
                "2026-06-26 12:30:15",
                "`",
                "`",
                "<",
                "mysql"
        );

        assertEquals("`f_1001` < '2026-06-26 12:30:15'", predicate);
    }

    @Test
    void numericCacheWatermarkPredicateDoesNotQuoteValidNumber() {
        String predicate = DatasetSyncUtils.buildCacheWatermarkPredicate(
                numberField(1001L),
                "1024",
                "\"",
                "\"",
                "<",
                "obOracle"
        );

        assertEquals("\"f_1001\" < 1024", predicate);
    }

    @Test
    void incrementalFieldMustBeCheckedNormalTimeOrNumberField() {
        assertTrue(DatasetSyncUtils.isIncrementalFieldSupported(timeField(1001L)));
        assertTrue(DatasetSyncUtils.isIncrementalFieldSupported(numberField(1002L)));
        assertFalse(DatasetSyncUtils.isIncrementalFieldSupported(textField(1003L)));

        DatasetTableFieldDTO unchecked = numberField(1004L);
        unchecked.setChecked(false);
        assertFalse(DatasetSyncUtils.isIncrementalFieldSupported(unchecked));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void saveAllScopeClearsStaleIncrementalFields() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ScheduleManager scheduleManager = mock(ScheduleManager.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(manage, "scheduleManager", scheduleManager);

        CoreDatasetSyncTask exists = new CoreDatasetSyncTask();
        exists.setId(1262155861866975232L);
        exists.setDatasetGroupId(982004L);
        exists.setName("角色分布透视");
        exists.setUpdateType("add_scope");
        exists.setIncrementalFieldId(984000405L);
        exists.setIncrementalLastValue("79");
        exists.setSchemaHash("old-schema");
        exists.setSyncRate(DatasourceTaskServer.ScheduleType.SIMPLE_CRON.name());
        exists.setCron("0 0/30 * * * ? *");
        exists.setCreateTime(1781080000000L);
        exists.setTaskStatus(TaskStatus.WaitingForExecution.name());
        exists.setCacheReady(1);

        when(taskMapper.selectOne(any())).thenReturn(exists);
        CoreDatasetSyncTask[] updated = new CoreDatasetSyncTask[1];
        when(taskMapper.updateById(any())).thenAnswer(invocation -> {
            updated[0] = invocation.getArgument(0);
            return 1;
        });
        when(taskMapper.selectById(1262155861866975232L)).thenAnswer(invocation -> updated[0]);
        when(scheduleManager.getDefaultJobDataMap(eq("982004"), any(), eq("1262155861866975232"), any()))
                .thenReturn(new JobDataMap());

        DatasetSyncTaskDTO request = new DatasetSyncTaskDTO();
        request.setDatasetGroupId(982004L);
        request.setName("角色分布透视");
        request.setUpdateType("all_scope");
        request.setSyncRate(DatasourceTaskServer.ScheduleType.SIMPLE_CRON.name());
        request.setSimpleCronValue(30L);
        request.setSimpleCronType("minute");

        manage.save(request);

        verify(taskMapper).update(any(CoreDatasetSyncTask.class), any(Wrapper.class));
    }

    @Test
    void pauseScheduledTaskSuspendsTaskAndDeletesSchedule() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ScheduleManager scheduleManager = mock(ScheduleManager.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(manage, "scheduleManager", scheduleManager);

        CoreDatasetSyncTask exists = new CoreDatasetSyncTask();
        exists.setId(1262155861866975232L);
        exists.setDatasetGroupId(982004L);
        exists.setSyncRate(DatasourceTaskServer.ScheduleType.SIMPLE_CRON.name());
        exists.setCron("0 0/30 * * * ? *");
        exists.setTaskStatus(TaskStatus.WaitingForExecution.name());
        when(taskMapper.selectOne(any())).thenReturn(exists);
        when(scheduleManager.exist(any())).thenReturn(true);

        manage.pause(982004L);

        ArgumentCaptor<CoreDatasetSyncTask> recordCaptor = ArgumentCaptor.forClass(CoreDatasetSyncTask.class);
        verify(taskMapper).update(recordCaptor.capture(), any());
        assertEquals(TaskStatus.Suspend.name(), recordCaptor.getValue().getTaskStatus());
        verify(scheduleManager).removeJob(any(), any());
    }

    @Test
    void resumeScheduledTaskMarksWaitingAndRestoresSchedule() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ScheduleManager scheduleManager = mock(ScheduleManager.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(manage, "scheduleManager", scheduleManager);

        CoreDatasetSyncTask exists = new CoreDatasetSyncTask();
        exists.setId(1262155861866975232L);
        exists.setDatasetGroupId(982004L);
        exists.setSyncRate(DatasourceTaskServer.ScheduleType.SIMPLE_CRON.name());
        exists.setSimpleCronValue(30L);
        exists.setSimpleCronType("minute");
        exists.setCron("0 0/30 * * * ? *");
        exists.setStartTime(1781080000000L);
        exists.setTaskStatus(TaskStatus.Suspend.name());
        when(taskMapper.selectOne(any())).thenReturn(exists);
        when(scheduleManager.getDefaultJobDataMap(eq("982004"), any(), eq("1262155861866975232"), any()))
                .thenReturn(new JobDataMap());

        manage.resume(982004L);

        ArgumentCaptor<CoreDatasetSyncTask> recordCaptor = ArgumentCaptor.forClass(CoreDatasetSyncTask.class);
        verify(taskMapper).update(recordCaptor.capture(), any());
        assertEquals(TaskStatus.WaitingForExecution.name(), recordCaptor.getValue().getTaskStatus());
        verify(scheduleManager).addOrUpdateCronJob(any(), any(), eq(DatasetSyncJob.class), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void resumeRejectsUnsupportedDatasetBeforeRestoringSchedule() throws Exception {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ScheduleManager scheduleManager = mock(ScheduleManager.class);
        DatasetSyncSupportValidator supportValidator = mock(DatasetSyncSupportValidator.class);
        ObjectProvider<DatasetSyncSupportValidator> supportValidatorProvider = mock(ObjectProvider.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(manage, "scheduleManager", scheduleManager);
        ReflectionTestUtils.setField(manage, "supportValidatorProvider", supportValidatorProvider);

        CoreDatasetSyncTask exists = new CoreDatasetSyncTask();
        exists.setId(1262155861866975232L);
        exists.setDatasetGroupId(982004L);
        exists.setSyncRate(DatasourceTaskServer.ScheduleType.SIMPLE_CRON.name());
        exists.setCron("0 0/30 * * * ? *");
        exists.setTaskStatus(TaskStatus.Suspend.name());
        when(taskMapper.selectOne(any())).thenReturn(exists);
        when(supportValidatorProvider.getIfAvailable()).thenReturn(supportValidator);
        doThrow(new RuntimeException("带 SQL 参数或未绑定占位符的数据集暂不支持缓存"))
                .when(supportValidator).assertSupported(982004L);

        CrestException exception = assertThrows(CrestException.class, () -> manage.resume(982004L));

        assertEquals("带 SQL 参数或未绑定占位符的数据集暂不支持缓存", exception.getMessage());
        verify(taskMapper, never()).update(any(CoreDatasetSyncTask.class), any());
        verify(scheduleManager, never()).addOrUpdateCronJob(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void finishTaskClearsQueueMarkerAfterQueuedExecution() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);

        CoreDatasetSyncTask task = scheduledTask(TaskStatus.UnderExecution.name());
        CoreDatasetSyncTask latest = scheduledTask(TaskStatus.UnderExecution.name());
        latest.setLastEnqueueTime(1781080000000L);
        when(taskMapper.selectById(task.getId())).thenReturn(latest);

        manage.finishTask(task, TaskStatus.Completed, null, null, false, null, null, null);

        ArgumentCaptor<CoreDatasetSyncTask> recordCaptor = ArgumentCaptor.forClass(CoreDatasetSyncTask.class);
        ArgumentCaptor<Wrapper<CoreDatasetSyncTask>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskMapper).update(recordCaptor.capture(), wrapperCaptor.capture());
        assertEquals(TaskStatus.WaitingForExecution.name(), recordCaptor.getValue().getTaskStatus());
        UpdateWrapper<CoreDatasetSyncTask> wrapper = (UpdateWrapper<CoreDatasetSyncTask>) wrapperCaptor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("last_exec_time"));
        assertTrue(wrapper.getSqlSegment().contains("worker_id"));
        assertTrue(wrapper.getSqlSegment().contains("task_status"));
        assertTrue(wrapper.getSqlSet().contains("worker_id"));
        assertTrue(wrapper.getSqlSet().contains("last_enqueue_time"));
        assertTrue(wrapper.getSqlSet().contains("next_fire_time"));
        assertTrue(wrapper.getSqlSet().contains("last_error"));
        assertTrue(wrapper.getSqlSet().contains("lock_version"));
    }

    @Test
    void finishTaskMarksCacheUnavailableAfterFailedExecution() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);

        CoreDatasetSyncTask task = scheduledTask(TaskStatus.UnderExecution.name());
        CoreDatasetSyncTask latest = scheduledTask(TaskStatus.UnderExecution.name());
        latest.setConsecutiveFailures(2);
        when(taskMapper.selectById(task.getId())).thenReturn(latest);

        manage.finishTask(task, TaskStatus.Error, null, null, false, null, null, null, "缓存表创建失败");

        ArgumentCaptor<CoreDatasetSyncTask> recordCaptor = ArgumentCaptor.forClass(CoreDatasetSyncTask.class);
        verify(taskMapper).update(recordCaptor.capture(), any());
        CoreDatasetSyncTask record = recordCaptor.getValue();
        assertEquals(0, record.getCacheReady());
        assertEquals(3, record.getConsecutiveFailures());
        assertEquals(TaskStatus.WaitingForExecution.name(), record.getTaskStatus());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void markCacheUnavailableDisablesOnlyReadyCacheTask() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);

        manage.markCacheUnavailable(982004L, "缓存表不可访问，已回退实时查询");

        ArgumentCaptor<CoreDatasetSyncTask> recordCaptor = ArgumentCaptor.forClass(CoreDatasetSyncTask.class);
        ArgumentCaptor<Wrapper<CoreDatasetSyncTask>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskMapper).update(recordCaptor.capture(), wrapperCaptor.capture());
        CoreDatasetSyncTask record = recordCaptor.getValue();
        assertEquals(0, record.getCacheReady());
        assertEquals(TaskStatus.Error.name(), record.getLastExecStatus());
        assertEquals("缓存表不可访问，已回退实时查询", record.getLastError());
        UpdateWrapper<CoreDatasetSyncTask> wrapper = (UpdateWrapper<CoreDatasetSyncTask>) wrapperCaptor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("cache_ready"));
    }

    @Test
    void markQueuedTaskEnqueuedUsesAtomicQueueUpdate() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        long now = 1781080000000L;
        long retryMillis = 60000L;
        when(taskMapper.markQueuedTaskEnqueued(1262155861866975232L, now, now - retryMillis)).thenReturn(1);

        assertTrue(manage.markQueuedTaskEnqueued(1262155861866975232L, now, retryMillis));

        verify(taskMapper).markQueuedTaskEnqueued(1262155861866975232L, now, now - retryMillis);
    }

    @Test
    void markUnderExecutionRecordsWorkerIdWithDatabaseCas() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        CoreDatasetSyncTask task = scheduledTask(TaskStatus.WaitingForExecution.name());
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(taskMapper.claimQueuedWorkerTask(eq(task.getId()), eq("worker-a"), any())).thenReturn(1);

        assertFalse(manage.markUnderExecution(task, "worker-a"));

        verify(taskMapper).claimQueuedWorkerTask(eq(task.getId()), eq("worker-a"), any());
        assertEquals(TaskStatus.UnderExecution.name(), task.getTaskStatus());
        assertEquals("worker-a", task.getWorkerId());
        assertTrue(task.getLastExecTime() > 0);
        assertEquals(task.getLastExecTime(), task.getHeartbeatTime());
    }

    @Test
    void markUnderExecutionUsesEnqueueTimeForQueuedMessage() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        CoreDatasetSyncTask task = scheduledTask(TaskStatus.WaitingForExecution.name());
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(taskMapper.claimQueuedWorkerTaskByEnqueueTime(eq(task.getId()), eq("worker-a"), any(), eq(1781080000000L)))
                .thenReturn(1);

        assertFalse(manage.markUnderExecution(task, "worker-a", 1781080000000L));

        verify(taskMapper).claimQueuedWorkerTaskByEnqueueTime(eq(task.getId()), eq("worker-a"), any(), eq(1781080000000L));
        verify(taskMapper, never()).claimQueuedWorkerTask(any(), any(), any());
    }

    @Test
    void markUnderExecutionKeepsLocalExecutionCompatible() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        CoreDatasetSyncTask task = scheduledTask(TaskStatus.Stopped.name());
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(taskMapper.markWorkerStarted(eq(task.getId()), isNull(), any())).thenReturn(1);

        assertFalse(manage.markUnderExecution(task, null));

        verify(taskMapper).markWorkerStarted(eq(task.getId()), isNull(), any());
        assertEquals(TaskStatus.UnderExecution.name(), task.getTaskStatus());
        assertEquals(null, task.getWorkerId());
        assertTrue(task.getLastExecTime() > 0);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void touchHeartbeatScopesUpdateByExecutionToken() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        CoreDatasetSyncTask task = runningTask(1781080000000L, 360);
        task.setWorkerId("worker-a");
        when(taskMapper.update(any(CoreDatasetSyncTask.class), any())).thenReturn(1);

        assertTrue(manage.touchHeartbeat(task));

        ArgumentCaptor<Wrapper<CoreDatasetSyncTask>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(taskMapper).update(any(CoreDatasetSyncTask.class), wrapperCaptor.capture());
        UpdateWrapper<CoreDatasetSyncTask> wrapper = (UpdateWrapper<CoreDatasetSyncTask>) wrapperCaptor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("last_exec_time"));
        assertTrue(wrapper.getSqlSegment().contains("worker_id"));
        assertTrue(wrapper.getSqlSegment().contains("task_status"));
    }

    @Test
    void sameExecutionRejectsExecutionTokenChanged() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTask expected = runningTask(1781080000000L, 360);
        expected.setWorkerId("worker-a");
        CoreDatasetSyncTask latest = runningTask(1781080060000L, 360);
        latest.setWorkerId("worker-a");

        assertFalse(manage.sameExecution(expected, latest));
    }

    @Test
    void markUnderExecutionSkipsQueuedTaskWhenStatusChangedBeforeWorkerClaim() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        CoreDatasetSyncTaskMapper taskMapper = mock(CoreDatasetSyncTaskMapper.class);
        ReflectionTestUtils.setField(manage, "taskMapper", taskMapper);
        CoreDatasetSyncTask task = scheduledTask(TaskStatus.Stopped.name());
        when(taskMapper.selectById(task.getId())).thenReturn(task);
        when(taskMapper.claimQueuedWorkerTask(eq(task.getId()), eq("worker-a"), any())).thenReturn(0);

        assertTrue(manage.markUnderExecution(task, "worker-a"));

        verify(taskMapper).claimQueuedWorkerTask(eq(task.getId()), eq("worker-a"), any());
        verify(taskMapper, never()).markWorkerStarted(any(), any(), any());
    }

    @Test
    void shouldRestoreScheduleSkipsManualPausedAndStoppedTasks() {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();

        assertEquals(true, manage.shouldRestoreSchedule(scheduledTask(TaskStatus.WaitingForExecution.name())));
        assertEquals(false, manage.shouldRestoreSchedule(scheduledTask(TaskStatus.Suspend.name())));
        assertEquals(false, manage.shouldRestoreSchedule(scheduledTask(TaskStatus.Stopped.name())));

        CoreDatasetSyncTask manual = scheduledTask(TaskStatus.WaitingForExecution.name());
        manual.setSyncRate(DatasourceTaskServer.ScheduleType.RIGHTNOW.name());
        assertEquals(false, manage.shouldRestoreSchedule(manual));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRestoreScheduleSkipsUnsupportedHistoricalTask() throws Exception {
        DatasetSyncTaskManage manage = new DatasetSyncTaskManage();
        DatasetSyncSupportValidator supportValidator = mock(DatasetSyncSupportValidator.class);
        ObjectProvider<DatasetSyncSupportValidator> supportValidatorProvider = mock(ObjectProvider.class);
        ReflectionTestUtils.setField(manage, "supportValidatorProvider", supportValidatorProvider);
        when(supportValidatorProvider.getIfAvailable()).thenReturn(supportValidator);
        doThrow(new RuntimeException("带 SQL 参数或未绑定占位符的数据集暂不支持缓存"))
                .when(supportValidator).assertSupported(982004L);

        assertFalse(manage.shouldRestoreSchedule(scheduledTask(TaskStatus.WaitingForExecution.name())));
    }

    private CoreDatasetSyncTask scheduledTask(String taskStatus) {
        CoreDatasetSyncTask task = new CoreDatasetSyncTask();
        task.setId(1262155861866975232L);
        task.setDatasetGroupId(982004L);
        task.setSyncRate(DatasourceTaskServer.ScheduleType.SIMPLE_CRON.name());
        task.setCron("0 0/30 * * * ? *");
        task.setTaskStatus(taskStatus);
        return task;
    }

    private CoreDatasetSyncTask runningTask(long startedAt, int timeoutMinutes) {
        CoreDatasetSyncTask task = new CoreDatasetSyncTask();
        task.setId(1262155861866975232L);
        task.setDatasetGroupId(982004L);
        task.setTaskStatus(TaskStatus.UnderExecution.name());
        task.setLastExecTime(startedAt);
        task.setHeartbeatTime(startedAt);
        task.setTaskTimeoutMinutes(timeoutMinutes);
        return task;
    }

    private DatasetSyncSupportValidator.SupportContext supportContext(List<DatasetTableFieldDTO> syncFields) {
        DatasetGroupInfoDTO dataset = new DatasetGroupInfoDTO();
        dataset.setId(982004L);
        DatasourceSchemaDTO datasource = new DatasourceSchemaDTO();
        datasource.setId(1L);
        datasource.setType("obOracle");
        Map<Long, DatasourceSchemaDTO> dsMap = Map.of(1L, datasource);
        return new DatasetSyncSupportValidator.SupportContext(dataset, Map.of("sql", "SELECT 1 FROM dual"), dsMap, syncFields, syncFields);
    }

    private DatasetTableFieldDTO timeField(Long id) {
        DatasetTableFieldDTO field = field(id);
        field.setExtractedFieldType(1);
        field.setFieldType(1);
        return field;
    }

    private DatasetTableFieldDTO numberField(Long id) {
        DatasetTableFieldDTO field = field(id);
        field.setExtractedFieldType(2);
        field.setFieldType(2);
        return field;
    }

    private DatasetTableFieldDTO textField(Long id) {
        DatasetTableFieldDTO field = field(id);
        field.setExtractedFieldType(0);
        field.setFieldType(0);
        return field;
    }

    private DatasetTableFieldDTO field(Long id) {
        DatasetTableFieldDTO field = new DatasetTableFieldDTO();
        field.setId(id);
        field.setChecked(true);
        field.setExtField(ExtFieldConstant.EXT_NORMAL);
        field.setEngineFieldName("f_" + id);
        return field;
    }

    private JdbcDataSource h2(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void createTaskCenterTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE core_dataset (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(128),
                    node_type VARCHAR(50),
                    mode INT,
                    create_by VARCHAR(50),
                    create_time BIGINT,
                    last_update_time BIGINT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE core_dataset_sync_task (
                    id BIGINT PRIMARY KEY,
                    dataset_group_id BIGINT,
                    name VARCHAR(255),
                    update_type VARCHAR(50),
                    incremental_field_id BIGINT,
                    incremental_last_value VARCHAR(255),
                    start_time BIGINT,
                    sync_rate VARCHAR(50),
                    cron VARCHAR(255),
                    simple_cron_value BIGINT,
                    simple_cron_type VARCHAR(50),
                    end_time BIGINT,
                    create_time BIGINT,
                    update_time BIGINT,
                    last_exec_time BIGINT,
                    heartbeat_time BIGINT,
                    worker_id VARCHAR(128),
                    retry_count INT,
                    lock_version BIGINT,
                    next_fire_time BIGINT,
                    last_enqueue_time BIGINT,
                    last_error VARCHAR(1024),
                    last_exec_status VARCHAR(50),
                    task_status VARCHAR(50),
                    cache_ready TINYINT,
                    schema_hash VARCHAR(128),
                    full_sync_interval_hours INT,
                    last_full_sync_time BIGINT,
                    verify_enabled TINYINT,
                    last_verify_time BIGINT,
                    last_verify_status VARCHAR(50),
                    last_verify_message VARCHAR(1024),
                    last_source_row_count BIGINT,
                    last_cache_row_count BIGINT,
                    cache_expire_hours INT,
                    task_timeout_minutes INT,
                    consecutive_failures INT,
                    failure_warn_threshold INT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE core_dataset_sync_task_log (
                    id BIGINT PRIMARY KEY,
                    dataset_group_id BIGINT,
                    task_id BIGINT,
                    update_type VARCHAR(50),
                    table_name VARCHAR(255),
                    start_time BIGINT,
                    end_time BIGINT,
                    task_status VARCHAR(50),
                    row_count BIGINT,
                    info VARCHAR(1024),
                    create_time BIGINT,
                    trigger_type VARCHAR(50)
                )
                """);
    }

    private void createVisualizationTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE core_visualization (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(255),
                    type VARCHAR(50),
                    node_type VARCHAR(50),
                    delete_flag INT,
                    create_by VARCHAR(50),
                    org_id VARCHAR(50)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE core_chart_view (
                    id BIGINT PRIMARY KEY,
                    scene_id BIGINT,
                    table_id BIGINT,
                    title VARCHAR(255)
                )
                """);
    }
}
