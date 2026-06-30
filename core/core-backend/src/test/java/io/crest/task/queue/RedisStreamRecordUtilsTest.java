package io.crest.task.queue;

import io.crest.dataset.sync.queue.DatasetSyncTaskQueueService;
import io.crest.datasource.queue.DatasourceSyncTaskQueueService;
import io.crest.exportCenter.queue.ExportTaskQueueService;
import io.crest.job.schedule.ScheduledTaskQueueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RedisStreamRecordUtilsTest {

    @Test
    void exportTaskIdShouldReadByteArrayStreamFields() {
        ExportTaskQueueService queueService = new ExportTaskQueueService(redisProvider(), mock(Environment.class));
        MapRecord<String, Object, Object> record = record(Map.of(
                bytes("taskType"), bytes("export"),
                bytes("taskId"), bytes("1264451980160536576")
        ));

        assertEquals("1264451980160536576", queueService.taskId(record));
    }

    @Test
    void datasetSyncTaskShouldReadByteArrayStreamFields() {
        DatasetSyncTaskQueueService queueService = new DatasetSyncTaskQueueService(redisProvider(), mock(Environment.class));
        DatasetSyncTaskQueueService.QueuedDatasetSyncTask task = queueService.task(record(Map.of(
                bytes("taskType"), bytes("dataset-sync"),
                bytes("datasetGroupId"), bytes("982013"),
                bytes("taskId"), bytes("1264451980160536576"),
                bytes("triggerType"), bytes("manual"),
                bytes("manual"), bytes("true"),
                bytes("enqueueTime"), bytes("1781080000000")
        )));

        assertEquals(982013L, task.datasetGroupId());
        assertEquals(1264451980160536576L, task.taskId());
        assertEquals("manual", task.triggerType());
        assertTrue(task.manual());
        assertEquals(1781080000000L, task.enqueueTime());
    }

    @Test
    void datasourceSyncTaskShouldReadByteArrayStreamFields() {
        DatasourceSyncTaskQueueService queueService = new DatasourceSyncTaskQueueService(redisProvider(), mock(Environment.class));
        DatasourceSyncTaskQueueService.QueuedDatasourceSyncTask task = queueService.task(record(Map.of(
                bytes("taskType"), bytes("datasource-sync"),
                bytes("datasourceId"), bytes("910001"),
                bytes("taskId"), bytes("1264451980160536576"),
                bytes("fireInstanceId"), bytes("fire-1"),
                bytes("enqueueTime"), bytes("1781080000000")
        )));

        assertEquals(910001L, task.datasourceId());
        assertEquals(1264451980160536576L, task.taskId());
        assertEquals("fire-1", task.fireInstanceId());
        assertEquals(1781080000000L, task.enqueueTime());
    }

    @Test
    void scheduledTaskShouldReadByteArrayStreamFields() {
        ScheduledTaskQueueService queueService = new ScheduledTaskQueueService(redisProvider(), mock(Environment.class));
        ScheduledTaskQueueService.QueuedScheduledTask task = queueService.task(record(Map.of(
                bytes("taskType"), bytes("report"),
                bytes("payload"), bytes("{\"taskId\":1264451980160536576,\"isTempTask\":false}")
        )));

        assertEquals(ScheduledTaskQueueService.TASK_TYPE_REPORT, task.taskType());
        assertEquals(1264451980160536576L, ((Number) task.taskData().get("taskId")).longValue());
        assertEquals(false, task.taskData().get("isTempTask"));
    }

    private MapRecord<String, Object, Object> record(Map<Object, Object> body) {
        return MapRecord.create("stream", new LinkedHashMap<>(body)).withId(RecordId.of("1-0"));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<StringRedisTemplate> redisProvider() {
        return (ObjectProvider<StringRedisTemplate>) mock(ObjectProvider.class);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
