package io.crest.job.schedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledTaskWorkerTest {

    @Test
    @DisplayName("Worker 应消费报表和填报调度任务")
    void workerShouldConsumeReportAndDataFillingTasks() {
        ScheduledTaskQueueService queueService = mock(ScheduledTaskQueueService.class);
        ScheduledTaskExecutor scheduledTaskExecutor = mock(ScheduledTaskExecutor.class);
        DataFillingTaskExecutor dataFillingTaskExecutor = mock(DataFillingTaskExecutor.class);
        MapRecord<String, Object, Object> reportRecord = record("1-0");
        MapRecord<String, Object, Object> dataFillingRecord = record("2-0");
        Map<String, Object> reportData = Map.of("taskId", 1L);
        Map<String, Object> dataFillingData = Map.of("taskId", 2L);

        when(queueService.enabled()).thenReturn(true);
        when(queueService.readScheduledTasks(anyInt(), any(Duration.class))).thenReturn(List.of(reportRecord));
        when(queueService.claimStalePendingTasks(anyInt(), any(Duration.class))).thenReturn(List.of(dataFillingRecord));
        when(queueService.task(reportRecord)).thenReturn(new ScheduledTaskQueueService.QueuedScheduledTask(
                ScheduledTaskQueueService.TASK_TYPE_REPORT,
                reportData,
                "report-state"
        ));
        when(queueService.task(dataFillingRecord)).thenReturn(new ScheduledTaskQueueService.QueuedScheduledTask(
                ScheduledTaskQueueService.TASK_TYPE_DATA_FILLING,
                dataFillingData,
                "data-filling-state"
        ));
        when(queueService.claimTask(new ScheduledTaskQueueService.QueuedScheduledTask(
                ScheduledTaskQueueService.TASK_TYPE_REPORT,
                reportData,
                "report-state"
        ))).thenReturn(ScheduledTaskQueueService.ClaimResult.CLAIMED);
        when(queueService.claimTask(new ScheduledTaskQueueService.QueuedScheduledTask(
                ScheduledTaskQueueService.TASK_TYPE_DATA_FILLING,
                dataFillingData,
                "data-filling-state"
        ))).thenReturn(ScheduledTaskQueueService.ClaimResult.CLAIMED);
        when(scheduledTaskExecutor.executeQueued(reportData)).thenReturn(true);
        when(dataFillingTaskExecutor.executeQueued(dataFillingData)).thenReturn(true);

        ScheduledTaskWorker worker = new ScheduledTaskWorker();
        ReflectionTestUtils.setField(worker, "queueService", queueService);
        ReflectionTestUtils.setField(worker, "scheduledTaskExecutor", scheduledTaskExecutor);
        ReflectionTestUtils.setField(worker, "dataFillingTaskExecutor", dataFillingTaskExecutor);
        ReflectionTestUtils.setField(worker, "batchSize", 2);
        ReflectionTestUtils.setField(worker, "blockMillis", 100L);
        ReflectionTestUtils.setField(worker, "pendingIdleMillis", 1000L);

        worker.poll();

        verify(scheduledTaskExecutor).executeQueued(reportData);
        verify(dataFillingTaskExecutor).executeQueued(dataFillingData);
        verify(queueService).completeTask(new ScheduledTaskQueueService.QueuedScheduledTask(
                ScheduledTaskQueueService.TASK_TYPE_REPORT,
                reportData,
                "report-state"
        ));
        verify(queueService).completeTask(new ScheduledTaskQueueService.QueuedScheduledTask(
                ScheduledTaskQueueService.TASK_TYPE_DATA_FILLING,
                dataFillingData,
                "data-filling-state"
        ));
        verify(queueService).acknowledge(reportRecord.getId());
        verify(queueService).acknowledge(dataFillingRecord.getId());
    }

    @Test
    @DisplayName("Worker 执行器缺失时应写入死信并确认原消息")
    void workerShouldDeadLetterUnloadedTask() {
        ScheduledTaskQueueService queueService = mock(ScheduledTaskQueueService.class);
        ScheduledTaskExecutor scheduledTaskExecutor = mock(ScheduledTaskExecutor.class);
        DataFillingTaskExecutor dataFillingTaskExecutor = mock(DataFillingTaskExecutor.class);
        MapRecord<String, Object, Object> reportRecord = record("1-0");
        Map<String, Object> reportData = Map.of("taskId", 1L);
        ScheduledTaskQueueService.QueuedScheduledTask task = new ScheduledTaskQueueService.QueuedScheduledTask(
                ScheduledTaskQueueService.TASK_TYPE_REPORT,
                reportData,
                "report-state"
        );

        when(queueService.enabled()).thenReturn(true);
        when(queueService.readScheduledTasks(anyInt(), any(Duration.class))).thenReturn(List.of(reportRecord));
        when(queueService.claimStalePendingTasks(anyInt(), any(Duration.class))).thenReturn(List.of());
        when(queueService.task(reportRecord)).thenReturn(task);
        when(queueService.claimTask(task)).thenReturn(ScheduledTaskQueueService.ClaimResult.CLAIMED);
        when(scheduledTaskExecutor.executeQueued(reportData)).thenReturn(false);

        ScheduledTaskWorker worker = new ScheduledTaskWorker();
        ReflectionTestUtils.setField(worker, "queueService", queueService);
        ReflectionTestUtils.setField(worker, "scheduledTaskExecutor", scheduledTaskExecutor);
        ReflectionTestUtils.setField(worker, "dataFillingTaskExecutor", dataFillingTaskExecutor);
        ReflectionTestUtils.setField(worker, "batchSize", 2);
        ReflectionTestUtils.setField(worker, "blockMillis", 100L);
        ReflectionTestUtils.setField(worker, "pendingIdleMillis", 1000L);

        worker.poll();

        verify(queueService).deadLetter(
                same(reportRecord),
                same(task),
                eq("Scheduled task executor is not configured: report")
        );
        verify(queueService).failTask(same(task), eq("Scheduled task executor is not configured: report"));
        verify(queueService).acknowledge(reportRecord.getId());
    }

    @Test
    @DisplayName("Worker 抢占失败且状态已结束时应确认消息")
    void workerShouldAcknowledgeSkippedClaim() {
        ScheduledTaskQueueService queueService = mock(ScheduledTaskQueueService.class);
        ScheduledTaskExecutor scheduledTaskExecutor = mock(ScheduledTaskExecutor.class);
        DataFillingTaskExecutor dataFillingTaskExecutor = mock(DataFillingTaskExecutor.class);
        MapRecord<String, Object, Object> reportRecord = record("1-0");
        Map<String, Object> reportData = Map.of("taskId", 1L);
        ScheduledTaskQueueService.QueuedScheduledTask task = new ScheduledTaskQueueService.QueuedScheduledTask(
                ScheduledTaskQueueService.TASK_TYPE_REPORT,
                reportData,
                "report-state"
        );

        when(queueService.enabled()).thenReturn(true);
        when(queueService.readScheduledTasks(anyInt(), any(Duration.class))).thenReturn(List.of(reportRecord));
        when(queueService.claimStalePendingTasks(anyInt(), any(Duration.class))).thenReturn(List.of());
        when(queueService.task(reportRecord)).thenReturn(task);
        when(queueService.claimTask(task)).thenReturn(ScheduledTaskQueueService.ClaimResult.SKIPPED);

        ScheduledTaskWorker worker = worker(queueService, scheduledTaskExecutor, dataFillingTaskExecutor);

        worker.poll();

        verify(scheduledTaskExecutor, never()).executeQueued(any());
        verify(queueService).acknowledge(reportRecord.getId());
    }

    @Test
    @DisplayName("Worker 抢占到其他实例执行中任务时应保留消息")
    void workerShouldDeferInProgressClaim() {
        ScheduledTaskQueueService queueService = mock(ScheduledTaskQueueService.class);
        ScheduledTaskExecutor scheduledTaskExecutor = mock(ScheduledTaskExecutor.class);
        DataFillingTaskExecutor dataFillingTaskExecutor = mock(DataFillingTaskExecutor.class);
        MapRecord<String, Object, Object> reportRecord = record("1-0");
        Map<String, Object> reportData = Map.of("taskId", 1L);
        ScheduledTaskQueueService.QueuedScheduledTask task = new ScheduledTaskQueueService.QueuedScheduledTask(
                ScheduledTaskQueueService.TASK_TYPE_REPORT,
                reportData,
                "report-state"
        );

        when(queueService.enabled()).thenReturn(true);
        when(queueService.readScheduledTasks(anyInt(), any(Duration.class))).thenReturn(List.of(reportRecord));
        when(queueService.claimStalePendingTasks(anyInt(), any(Duration.class))).thenReturn(List.of());
        when(queueService.task(reportRecord)).thenReturn(task);
        when(queueService.claimTask(task)).thenReturn(ScheduledTaskQueueService.ClaimResult.DEFERRED);

        ScheduledTaskWorker worker = worker(queueService, scheduledTaskExecutor, dataFillingTaskExecutor);

        worker.poll();

        verify(scheduledTaskExecutor, never()).executeQueued(any());
        verify(queueService, never()).acknowledge(reportRecord.getId());
    }

    @Test
    @DisplayName("Worker 队列暂不可用时不应向调度线程抛出异常")
    void workerShouldIgnoreQueuePollingFailure() {
        ScheduledTaskQueueService queueService = mock(ScheduledTaskQueueService.class);
        ScheduledTaskExecutor scheduledTaskExecutor = mock(ScheduledTaskExecutor.class);
        DataFillingTaskExecutor dataFillingTaskExecutor = mock(DataFillingTaskExecutor.class);

        when(queueService.enabled()).thenReturn(true);
        when(queueService.readScheduledTasks(anyInt(), any(Duration.class))).thenThrow(new IllegalStateException("CLUSTERDOWN"));

        ScheduledTaskWorker worker = worker(queueService, scheduledTaskExecutor, dataFillingTaskExecutor);

        assertDoesNotThrow(worker::poll);

        verify(scheduledTaskExecutor, never()).executeQueued(any());
        verify(dataFillingTaskExecutor, never()).executeQueued(any());
    }

    private MapRecord<String, Object, Object> record(String id) {
        Map<Object, Object> body = new LinkedHashMap<>();
        body.put("taskType", "test");
        return MapRecord.create("stream", body).withId(RecordId.of(id));
    }

    private ScheduledTaskWorker worker(ScheduledTaskQueueService queueService,
                                       ScheduledTaskExecutor scheduledTaskExecutor,
                                       DataFillingTaskExecutor dataFillingTaskExecutor) {
        ScheduledTaskWorker worker = new ScheduledTaskWorker();
        ReflectionTestUtils.setField(worker, "queueService", queueService);
        ReflectionTestUtils.setField(worker, "scheduledTaskExecutor", scheduledTaskExecutor);
        ReflectionTestUtils.setField(worker, "dataFillingTaskExecutor", dataFillingTaskExecutor);
        ReflectionTestUtils.setField(worker, "batchSize", 2);
        ReflectionTestUtils.setField(worker, "blockMillis", 100L);
        ReflectionTestUtils.setField(worker, "pendingIdleMillis", 1000L);
        return worker;
    }
}
