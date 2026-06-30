package io.crest.job.schedule;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduledTaskQueueServiceTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void enqueueShouldCreateDatabaseStateBeforeRedisMessage() {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations streamOperations = mock(StreamOperations.class);
        ScheduledTaskQueueStateMapper stateMapper = mock(ScheduledTaskQueueStateMapper.class);
        ScheduledTaskQueueService service = new ScheduledTaskQueueService(provider, new MockEnvironment());
        ReflectionTestUtils.setField(service, "stateMapper", stateMapper);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.add(any(MapRecord.class))).thenReturn(RecordId.of("1-0"));
        when(stateMapper.insertPending(anyString(), eq(ScheduledTaskQueueService.TASK_TYPE_REPORT), eq("7"),
                anyString(), anyLong())).thenReturn(1);

        service.enqueueReportTask(Map.of(
                "taskId", 7L,
                ScheduledTaskQueueService.QUEUE_JOB_KEY, "REPORT_TASK:7",
                ScheduledTaskQueueService.QUEUE_SCHEDULED_FIRE_TIME, 1781080000000L
        ));

        verify(stateMapper).insertPending(
                argThat(value -> value != null && value.length() == 64),
                eq(ScheduledTaskQueueService.TASK_TYPE_REPORT),
                eq("7"),
                argThat(value -> value != null && value.length() == 64),
                anyLong()
        );
        ArgumentCaptor<MapRecord> recordCaptor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOperations).add(recordCaptor.capture());
        Object redisBody = recordCaptor.getValue().getValue();
        assertTrue(redisBody instanceof Map);
        assertTrue(((Map<?, ?>) redisBody).containsKey("stateId"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void enqueueShouldReopenPreviousEnqueueFailureBeforeRedisMessage() {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations streamOperations = mock(StreamOperations.class);
        ScheduledTaskQueueStateMapper stateMapper = mock(ScheduledTaskQueueStateMapper.class);
        ScheduledTaskQueueService service = new ScheduledTaskQueueService(provider, new MockEnvironment());
        ReflectionTestUtils.setField(service, "stateMapper", stateMapper);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.add(any(MapRecord.class))).thenReturn(RecordId.of("1-0"));
        when(stateMapper.insertPending(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(stateMapper.reopenEnqueueFailed(anyString(), anyString(), anyLong())).thenReturn(1);

        service.enqueueReportTask(Map.of(
                "taskId", 7L,
                ScheduledTaskQueueService.QUEUE_JOB_KEY, "REPORT_TASK:7",
                ScheduledTaskQueueService.QUEUE_SCHEDULED_FIRE_TIME, 1781080000000L
        ));

        verify(stateMapper).reopenEnqueueFailed(
                argThat(value -> value != null && value.length() == 64),
                argThat(value -> value != null && value.length() == 64),
                anyLong()
        );
        verify(streamOperations).add(any(MapRecord.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void enqueueShouldMarkStateAsEnqueueFailedWhenRedisWriteFails() {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        StreamOperations streamOperations = mock(StreamOperations.class);
        ScheduledTaskQueueStateMapper stateMapper = mock(ScheduledTaskQueueStateMapper.class);
        ScheduledTaskQueueService service = new ScheduledTaskQueueService(provider, new MockEnvironment());
        ReflectionTestUtils.setField(service, "stateMapper", stateMapper);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.add(any(MapRecord.class))).thenThrow(new IllegalStateException("redis down"));
        when(stateMapper.insertPending(anyString(), anyString(), anyString(), anyString(), anyLong())).thenReturn(1);

        assertThrows(RuntimeException.class, () -> service.enqueueReportTask(Map.of(
                "taskId", 7L,
                ScheduledTaskQueueService.QUEUE_JOB_KEY, "REPORT_TASK:7",
                ScheduledTaskQueueService.QUEUE_SCHEDULED_FIRE_TIME, 1781080000000L
        )));

        verify(stateMapper).markEnqueueFailed(
                argThat(value -> value != null && value.length() == 64),
                anyLong(),
                eq("redis down")
        );
    }

    @Test
    void claimShouldUseDatabaseCasBeforeWorkerExecution() {
        ScheduledTaskQueueStateMapper stateMapper = mock(ScheduledTaskQueueStateMapper.class);
        ScheduledTaskQueueService service = serviceWithStateMapper(stateMapper);
        ScheduledTaskQueueService.QueuedScheduledTask task = new ScheduledTaskQueueService.QueuedScheduledTask(
                ScheduledTaskQueueService.TASK_TYPE_REPORT,
                Map.of("taskId", 7L),
                "state-a"
        );
        when(stateMapper.insertPending(anyString(), anyString(), anyString(), anyString(), anyLong())).thenReturn(1);
        when(stateMapper.claimPending(eq("state-a"), anyString(), anyLong())).thenReturn(1);

        ScheduledTaskQueueService.ClaimResult result = service.claimTask(task);

        assertEquals(ScheduledTaskQueueService.ClaimResult.CLAIMED, result);
        verify(stateMapper).resetStaleInProgress(eq("state-a"), anyLong(), anyString());
        verify(stateMapper).claimPending(eq("state-a"), anyString(), anyLong());
    }

    @Test
    void claimShouldSkipFinishedDuplicateMessage() {
        ScheduledTaskQueueStateMapper stateMapper = mock(ScheduledTaskQueueStateMapper.class);
        ScheduledTaskQueueService service = serviceWithStateMapper(stateMapper);
        ScheduledTaskQueueService.QueuedScheduledTask task = new ScheduledTaskQueueService.QueuedScheduledTask(
                ScheduledTaskQueueService.TASK_TYPE_REPORT,
                Map.of("taskId", 7L),
                "state-a"
        );
        when(stateMapper.insertPending(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(stateMapper.claimPending(eq("state-a"), anyString(), anyLong())).thenReturn(0);
        when(stateMapper.selectStatus("state-a")).thenReturn("COMPLETED");

        ScheduledTaskQueueService.ClaimResult result = service.claimTask(task);

        assertEquals(ScheduledTaskQueueService.ClaimResult.SKIPPED, result);
    }

    @Test
    void claimShouldDeferRunningTaskOwnedByAnotherWorker() {
        ScheduledTaskQueueStateMapper stateMapper = mock(ScheduledTaskQueueStateMapper.class);
        ScheduledTaskQueueService service = serviceWithStateMapper(stateMapper);
        ScheduledTaskQueueService.QueuedScheduledTask task = new ScheduledTaskQueueService.QueuedScheduledTask(
                ScheduledTaskQueueService.TASK_TYPE_REPORT,
                Map.of("taskId", 7L),
                "state-a"
        );
        when(stateMapper.insertPending(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new DuplicateKeyException("duplicate"));
        when(stateMapper.claimPending(eq("state-a"), anyString(), anyLong())).thenReturn(0);
        when(stateMapper.selectStatus("state-a")).thenReturn("IN_PROGRESS");

        ScheduledTaskQueueService.ClaimResult result = service.claimTask(task);

        assertEquals(ScheduledTaskQueueService.ClaimResult.DEFERRED, result);
    }

    private ScheduledTaskQueueService serviceWithStateMapper(ScheduledTaskQueueStateMapper stateMapper) {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        ScheduledTaskQueueService service = new ScheduledTaskQueueService(provider, new MockEnvironment());
        ReflectionTestUtils.setField(service, "stateMapper", stateMapper);
        ReflectionTestUtils.setField(service, "staleMillis", 60000L);
        return service;
    }
}
