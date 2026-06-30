package io.crest.task.queue;

import io.crest.dataset.sync.DatasetSyncManage;
import io.crest.dataset.sync.DatasetSyncTaskManage;
import io.crest.dataset.sync.queue.DatasetSyncTaskQueueService;
import io.crest.dataset.sync.queue.DatasetSyncTaskWorker;
import io.crest.datasource.manage.DatasourceSyncManage;
import io.crest.datasource.queue.DatasourceSyncTaskQueueService;
import io.crest.datasource.queue.DatasourceSyncTaskWorker;
import io.crest.datasource.server.DatasourceTaskServer;
import io.crest.exportCenter.dao.auto.mapper.CoreExportTaskMapper;
import io.crest.exportCenter.manage.ExportCenterDownLoadManage;
import io.crest.exportCenter.queue.ExportTaskQueueService;
import io.crest.exportCenter.queue.ExportTaskWorker;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskWorkerPendingClaimTest {

    @Test
    void exportWorkerClaimsStalePendingMessages() {
        ExportTaskQueueService queueService = mock(ExportTaskQueueService.class);
        when(queueService.enabled()).thenReturn(true);
        when(queueService.readExportTasks(anyInt(), any(Duration.class))).thenReturn(List.of());
        when(queueService.claimStalePendingTasks(anyInt(), any(Duration.class))).thenReturn(List.of());

        ExportTaskWorker worker = new ExportTaskWorker();
        ReflectionTestUtils.setField(worker, "exportTaskQueueService", queueService);
        ReflectionTestUtils.setField(worker, "exportCenterDownLoadManage", mock(ExportCenterDownLoadManage.class));
        ReflectionTestUtils.setField(worker, "exportTaskMapper", mock(CoreExportTaskMapper.class));
        setCommonTiming(worker);

        worker.poll();

        verify(queueService).claimStalePendingTasks(anyInt(), any(Duration.class));
    }

    @Test
    void exportWorkerIgnoresQueuePollingFailure() {
        ExportTaskQueueService queueService = mock(ExportTaskQueueService.class);
        when(queueService.enabled()).thenReturn(true);
        when(queueService.readExportTasks(anyInt(), any(Duration.class))).thenThrow(new IllegalStateException("CLUSTERDOWN"));

        ExportTaskWorker worker = new ExportTaskWorker();
        ReflectionTestUtils.setField(worker, "exportTaskQueueService", queueService);
        ReflectionTestUtils.setField(worker, "exportCenterDownLoadManage", mock(ExportCenterDownLoadManage.class));
        ReflectionTestUtils.setField(worker, "exportTaskMapper", mock(CoreExportTaskMapper.class));
        setCommonTiming(worker);

        assertDoesNotThrow(worker::poll);
    }

    @Test
    void datasetSyncWorkerClaimsStalePendingMessages() {
        DatasetSyncTaskQueueService queueService = mock(DatasetSyncTaskQueueService.class);
        when(queueService.enabled()).thenReturn(true);
        when(queueService.readDatasetSyncTasks(anyInt(), any(Duration.class))).thenReturn(List.of());
        when(queueService.claimStalePendingTasks(anyInt(), any(Duration.class))).thenReturn(List.of());

        DatasetSyncTaskWorker worker = new DatasetSyncTaskWorker();
        ReflectionTestUtils.setField(worker, "queueService", queueService);
        ReflectionTestUtils.setField(worker, "datasetSyncManage", mock(DatasetSyncManage.class));
        ReflectionTestUtils.setField(worker, "taskManage", mock(DatasetSyncTaskManage.class));
        setCommonTiming(worker);

        worker.poll();

        verify(queueService).claimStalePendingTasks(anyInt(), any(Duration.class));
    }

    @Test
    void datasetSyncWorkerIgnoresQueuePollingFailure() {
        DatasetSyncTaskQueueService queueService = mock(DatasetSyncTaskQueueService.class);
        when(queueService.enabled()).thenReturn(true);
        when(queueService.readDatasetSyncTasks(anyInt(), any(Duration.class))).thenThrow(new IllegalStateException("CLUSTERDOWN"));

        DatasetSyncTaskWorker worker = new DatasetSyncTaskWorker();
        ReflectionTestUtils.setField(worker, "queueService", queueService);
        ReflectionTestUtils.setField(worker, "datasetSyncManage", mock(DatasetSyncManage.class));
        ReflectionTestUtils.setField(worker, "taskManage", mock(DatasetSyncTaskManage.class));
        setCommonTiming(worker);

        assertDoesNotThrow(worker::poll);
    }

    @Test
    void datasourceSyncWorkerClaimsStalePendingMessages() {
        DatasourceSyncTaskQueueService queueService = mock(DatasourceSyncTaskQueueService.class);
        when(queueService.enabled()).thenReturn(true);
        when(queueService.readDatasourceSyncTasks(anyInt(), any(Duration.class))).thenReturn(List.of());
        when(queueService.claimStalePendingTasks(anyInt(), any(Duration.class))).thenReturn(List.of());

        DatasourceSyncTaskWorker worker = new DatasourceSyncTaskWorker();
        ReflectionTestUtils.setField(worker, "queueService", queueService);
        ReflectionTestUtils.setField(worker, "datasourceSyncManage", mock(DatasourceSyncManage.class));
        ReflectionTestUtils.setField(worker, "datasourceTaskServer", mock(DatasourceTaskServer.class));
        setCommonTiming(worker);

        worker.poll();

        verify(queueService).claimStalePendingTasks(anyInt(), any(Duration.class));
    }

    @Test
    void datasourceSyncWorkerIgnoresQueuePollingFailure() {
        DatasourceSyncTaskQueueService queueService = mock(DatasourceSyncTaskQueueService.class);
        when(queueService.enabled()).thenReturn(true);
        when(queueService.readDatasourceSyncTasks(anyInt(), any(Duration.class))).thenThrow(new IllegalStateException("CLUSTERDOWN"));

        DatasourceSyncTaskWorker worker = new DatasourceSyncTaskWorker();
        ReflectionTestUtils.setField(worker, "queueService", queueService);
        ReflectionTestUtils.setField(worker, "datasourceSyncManage", mock(DatasourceSyncManage.class));
        ReflectionTestUtils.setField(worker, "datasourceTaskServer", mock(DatasourceTaskServer.class));
        setCommonTiming(worker);

        assertDoesNotThrow(worker::poll);
    }

    private void setCommonTiming(Object worker) {
        // 测试只覆盖 pending 认领路径，跳过数据库恢复扫描以保持断言聚焦。
        ReflectionTestUtils.setField(worker, "batchSize", 1);
        ReflectionTestUtils.setField(worker, "blockMillis", 100L);
        ReflectionTestUtils.setField(worker, "recoveryIntervalMillis", Long.MAX_VALUE);
        ReflectionTestUtils.setField(worker, "recoveryBatchSize", 1);
        ReflectionTestUtils.setField(worker, "staleMillis", 60000L);
        ReflectionTestUtils.setField(worker, "pendingIdleMillis", 60000L);
    }
}
