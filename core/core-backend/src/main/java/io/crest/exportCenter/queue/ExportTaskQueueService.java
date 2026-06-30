package io.crest.exportCenter.queue;

import io.crest.exception.CrestException;
import io.crest.task.queue.RedisStreamErrorUtils;
import io.crest.task.queue.RedisStreamRecordUtils;
import io.crest.utils.LogUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Component
// 封装导出任务 Redis Streams 访问，保持业务层只关心投递和确认语义。
public class ExportTaskQueueService {

    private static final String TASK_TYPE_EXPORT = "export";
    private static final String TASK_TYPE_BOOTSTRAP = "bootstrap";
    private static final String BOOTSTRAP_TASK_ID = "__stream_bootstrap__";

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Environment environment;
    private final String defaultWorkerId = buildDefaultWorkerId();

    @Value("${crest.task.queue.export.stream:crest:core:stream:export-task}")
    private String streamName;

    @Value("${crest.task.queue.export.consumer-group:crest:core:group:export-workers}")
    private String consumerGroup;

    public ExportTaskQueueService(ObjectProvider<StringRedisTemplate> redisTemplateProvider, Environment environment) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.environment = environment;
    }

    public boolean enabled() {
        String explicit = StringUtils.defaultIfBlank(environment.getProperty("crest.task.queue.enabled"),
                environment.getProperty("CREST_TASK_QUEUE_ENABLED"));
        if (StringUtils.isNotBlank(explicit)) {
            return Boolean.parseBoolean(explicit.trim());
        }
        return environment.getProperty("crest.production-mode", Boolean.class,
                environment.getProperty("CREST_PRODUCTION_MODE", Boolean.class, false));
    }

    public String workerId() {
        return StringUtils.defaultIfBlank(environment.getProperty("crest.worker.id"),
                StringUtils.defaultIfBlank(environment.getProperty("CREST_WORKER_ID"), defaultWorkerId));
    }

    // 只写入任务编号，任务参数和状态始终以数据库记录为准。
    public void enqueueExportTask(String taskId) {
        StringRedisTemplate redisTemplate = redisTemplate();
        Map<String, String> body = Map.of(
                "taskType", TASK_TYPE_EXPORT,
                "taskId", taskId,
                "createdAt", String.valueOf(System.currentTimeMillis())
        );
        try {
            redisTemplate.opsForStream().add(StreamRecords.mapBacked(body).withStreamKey(streamName));
        } catch (Exception e) {
            CrestException.throwException("导出任务投递失败：" + e.getMessage());
        }
    }

    // 按消费组读取导出任务，消费者编号固定后可追踪每个 Worker 的处理记录。
    @SuppressWarnings("unchecked")
    public List<MapRecord<String, Object, Object>> readExportTasks(int count, Duration block) {
        StringRedisTemplate redisTemplate = redisTemplate();
        ensureConsumerGroup(redisTemplate);
        StreamReadOptions options = StreamReadOptions.empty()
                .count(Math.max(1, count))
                .block(block == null || block.isNegative() ? Duration.ofSeconds(1) : block);
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                Consumer.from(consumerGroup, workerId()),
                options,
                StreamOffset.create(streamName, ReadOffset.lastConsumed())
        );
        return records == null ? List.of() : records;
    }

    @SuppressWarnings("unchecked")
    public List<MapRecord<String, Object, Object>> claimStalePendingTasks(int count, Duration minIdle) {
        StringRedisTemplate redisTemplate = redisTemplate();
        ensureConsumerGroup(redisTemplate);
        Duration safeMinIdle = minIdle == null || minIdle.isNegative() ? Duration.ofMinutes(1) : minIdle;
        PendingMessages pending = redisTemplate.opsForStream().pending(streamName, consumerGroup, Range.unbounded(), Math.max(1, count));
        if (pending == null || pending.isEmpty()) {
            return List.of();
        }
        List<RecordId> recordIds = StreamSupport.stream(pending.spliterator(), false)
                .filter(message -> pendingLongEnough(message, safeMinIdle))
                .map(PendingMessage::getId)
                .toList();
        if (recordIds.isEmpty()) {
            return List.of();
        }
        // 认领超时未确认消息，清理 Worker 异常退出后留在 Redis 消费组里的积压。
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().claim(
                streamName,
                consumerGroup,
                workerId(),
                RedisStreamCommands.XClaimOptions.minIdle(safeMinIdle).ids(recordIds.toArray(RecordId[]::new))
        );
        return records == null ? List.of() : records;
    }

    public void acknowledge(RecordId recordId) {
        if (recordId == null) {
            return;
        }
        redisTemplate().opsForStream().acknowledge(streamName, consumerGroup, recordId);
    }

    public String taskId(MapRecord<String, Object, Object> record) {
        if (record == null || record.getValue() == null) {
            return null;
        }
        String taskType = RedisStreamRecordUtils.stringField(record.getValue(), "taskType");
        if (!TASK_TYPE_EXPORT.equals(taskType)) {
            return null;
        }
        return RedisStreamRecordUtils.stringField(record.getValue(), "taskId");
    }

    private boolean pendingLongEnough(PendingMessage message, Duration minIdle) {
        return message != null
                && message.getElapsedTimeSinceLastDelivery() != null
                && message.getElapsedTimeSinceLastDelivery().compareTo(minIdle) >= 0;
    }

    private void ensureConsumerGroup(StringRedisTemplate redisTemplate) {
        try {
            ensureStreamExists(redisTemplate);
            StreamOperations<String, Object, Object> streamOperations = redisTemplate.opsForStream();
            streamOperations.createGroup(streamName, ReadOffset.from("0-0"), consumerGroup);
        } catch (RedisSystemException e) {
            if (!RedisStreamErrorUtils.isBusyGroup(e)) {
                throw e;
            }
        }
    }

    // 空队列启动时先创建一条引导消息，避免消费组创建依赖生产者启动顺序。
    private void ensureStreamExists(StringRedisTemplate redisTemplate) {
        if (Boolean.TRUE.equals(redisTemplate.hasKey(streamName))) {
            return;
        }
        Map<String, String> body = Map.of(
                "taskType", TASK_TYPE_BOOTSTRAP,
                "taskId", BOOTSTRAP_TASK_ID,
                "createdAt", String.valueOf(System.currentTimeMillis())
        );
        redisTemplate.opsForStream().add(StreamRecords.mapBacked(body).withStreamKey(streamName));
    }

    private StringRedisTemplate redisTemplate() {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            CrestException.throwException("Redis 未配置，无法启用导出任务队列");
        }
        return redisTemplate;
    }

    private String buildDefaultWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID();
        } catch (Exception e) {
            LogUtil.warn("Failed to resolve worker hostname: " + e.getMessage());
            return "worker-" + UUID.randomUUID();
        }
    }
}
