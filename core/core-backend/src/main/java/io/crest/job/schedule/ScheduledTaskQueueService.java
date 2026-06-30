package io.crest.job.schedule;

import com.fasterxml.jackson.core.type.TypeReference;
import io.crest.exception.CrestException;
import io.crest.task.queue.RedisStreamErrorUtils;
import io.crest.task.queue.RedisStreamRecordUtils;
import io.crest.utils.JsonUtil;
import io.crest.utils.LogUtil;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Component
// 封装报表和填报调度任务 Redis Streams 访问，调度端只投递，执行端统一由 Worker 消费。
public class ScheduledTaskQueueService {

    public static final String TASK_TYPE_REPORT = "report";
    public static final String TASK_TYPE_DATA_FILLING = "data-filling";
    public static final String QUEUE_JOB_KEY = "_queueJobKey";
    public static final String QUEUE_SCHEDULED_FIRE_TIME = "_queueScheduledFireTime";

    private static final String TASK_TYPE_BOOTSTRAP = "bootstrap";
    private static final String BOOTSTRAP_TASK_ID = "__stream_bootstrap__";

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Environment environment;
    private final String defaultWorkerId = buildDefaultWorkerId();
    @Resource
    private ScheduledTaskQueueStateMapper stateMapper;

    @Value("${crest.task.queue.scheduled.stream:crest:core:stream:scheduled-task}")
    private String streamName;

    @Value("${crest.task.queue.scheduled.consumer-group:crest:core:group:scheduled-workers}")
    private String consumerGroup;

    @Value("${crest.task.queue.scheduled.stale-millis:1800000}")
    private long staleMillis;

    public ScheduledTaskQueueService(ObjectProvider<StringRedisTemplate> redisTemplateProvider, Environment environment) {
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

    public void enqueueReportTask(Map<String, Object> taskData) {
        enqueue(TASK_TYPE_REPORT, taskData);
    }

    public void enqueueDataFillingTask(Map<String, Object> taskData) {
        enqueue(TASK_TYPE_DATA_FILLING, taskData);
    }

    @SuppressWarnings("unchecked")
    public List<MapRecord<String, Object, Object>> readScheduledTasks(int count, Duration block) {
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
        // 认领超时未确认消息，避免 Worker 异常退出后调度任务长期停留在 pending。
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

    public void deadLetter(MapRecord<String, Object, Object> record, QueuedScheduledTask task, String reason) {
        if (record == null) {
            return;
        }
        Map<String, String> body = new LinkedHashMap<>();
        body.put("taskType", StringUtils.defaultString(task == null ? RedisStreamRecordUtils.stringField(record.getValue(), "taskType") : task.taskType()));
        body.put("taskId", StringUtils.defaultString(RedisStreamRecordUtils.stringField(record.getValue(), "taskId")));
        body.put("payload", StringUtils.defaultString(task == null ? RedisStreamRecordUtils.stringField(record.getValue(), "payload") : taskPayload(task.taskData())));
        body.put("reason", StringUtils.defaultString(reason));
        body.put("workerId", workerId());
        body.put("sourceRecordId", record.getId() == null ? "" : record.getId().getValue());
        body.put("failedAt", String.valueOf(System.currentTimeMillis()));
        // 死信流保留无法执行的调度消息，便于生产排查缺少执行器或数据异常。
        redisTemplate().opsForStream().add(StreamRecords.mapBacked(body).withStreamKey(streamName + ":dead"));
    }

    public ClaimResult claimTask(QueuedScheduledTask task) {
        if (task == null || StringUtils.isBlank(task.stateId())) {
            return ClaimResult.SKIPPED;
        }
        ensureStateExists(task);
        long now = System.currentTimeMillis();
        long staleBefore = now - Math.max(60000, staleMillis);
        stateMapper.resetStaleInProgress(task.stateId(), staleBefore, "Worker 心跳超时，任务已重新投递");
        if (stateMapper.claimPending(task.stateId(), workerId(), now) > 0) {
            return ClaimResult.CLAIMED;
        }
        String status = stateMapper.selectStatus(task.stateId());
        if ("COMPLETED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
            return ClaimResult.SKIPPED;
        }
        return ClaimResult.DEFERRED;
    }

    public void completeTask(QueuedScheduledTask task) {
        if (task != null && StringUtils.isNotBlank(task.stateId())) {
            stateMapper.complete(task.stateId(), System.currentTimeMillis());
        }
    }

    public void failTask(QueuedScheduledTask task, String reason) {
        if (task != null && StringUtils.isNotBlank(task.stateId())) {
            stateMapper.fail(task.stateId(), System.currentTimeMillis(), StringUtils.defaultString(reason));
        }
    }

    public void retryTaskLater(QueuedScheduledTask task, String reason) {
        if (task != null && StringUtils.isNotBlank(task.stateId())) {
            stateMapper.retryLater(task.stateId(), StringUtils.defaultString(reason));
        }
    }

    public QueuedScheduledTask task(MapRecord<String, Object, Object> record) {
        if (record == null || record.getValue() == null) {
            return null;
        }
        String taskType = RedisStreamRecordUtils.stringField(record.getValue(), "taskType");
        if (!TASK_TYPE_REPORT.equals(taskType) && !TASK_TYPE_DATA_FILLING.equals(taskType)) {
            return null;
        }
        String payload = RedisStreamRecordUtils.stringField(record.getValue(), "payload");
        Map<String, Object> taskData = JsonUtil.parseObject(payload, new TypeReference<Map<String, Object>>() {
        });
        String stateId = RedisStreamRecordUtils.stringField(record.getValue(), "stateId");
        if (StringUtils.isBlank(stateId)) {
            // 兼容升级前已经进入 Redis Streams、但还没有数据库状态记录的消息。
            stateId = legacyStateId(taskType, record.getId() == null ? "" : record.getId().getValue());
        }
        return new QueuedScheduledTask(taskType, taskData == null ? Map.of() : taskData, stateId);
    }

    private void enqueue(String taskType, Map<String, Object> taskData) {
        Map<String, Object> safeTaskData = taskData == null ? Map.of() : new LinkedHashMap<>(taskData);
        Object json = JsonUtil.toJSONString(safeTaskData);
        if (json == null) {
            CrestException.throwException("调度任务投递失败：任务参数无法序列化");
        }
        String payload = json.toString();
        String payloadHash = sha256(payload);
        String stateId = stateId(taskType, safeTaskData, payloadHash);
        boolean stateCreated = createPendingState(stateId, taskType, taskId(safeTaskData), payloadHash, System.currentTimeMillis());
        if (!stateCreated) {
            return;
        }
        Map<String, String> body = Map.of(
                "taskType", taskType,
                "stateId", stateId,
                "taskId", taskId(safeTaskData),
                "payload", payload,
                "createdAt", String.valueOf(System.currentTimeMillis())
        );
        try {
            redisTemplate().opsForStream().add(StreamRecords.mapBacked(body).withStreamKey(streamName));
        } catch (Exception e) {
            stateMapper.markEnqueueFailed(stateId, System.currentTimeMillis(), e.getMessage());
            CrestException.throwException("调度任务投递失败：" + e.getMessage());
        }
    }

    private boolean pendingLongEnough(PendingMessage message, Duration minIdle) {
        return message != null
                && message.getElapsedTimeSinceLastDelivery() != null
                && message.getElapsedTimeSinceLastDelivery().compareTo(minIdle) >= 0;
    }

    private String taskId(Map<String, Object> taskData) {
        Object taskId = taskData.get("taskId");
        return taskId == null ? "" : taskId.toString();
    }

    private String taskPayload(Map<String, Object> taskData) {
        Object json = JsonUtil.toJSONString(taskData == null ? Map.of() : taskData);
        return json == null ? "{}" : json.toString();
    }

    private void ensureStateExists(QueuedScheduledTask task) {
        String payloadHash = sha256(taskPayload(task.taskData()));
        createPendingState(task.stateId(), task.taskType(), taskId(task.taskData()), payloadHash, System.currentTimeMillis());
    }

    private boolean createPendingState(String stateId, String taskType, String taskId, String payloadHash, long now) {
        try {
            return stateMapper.insertPending(stateId, taskType, StringUtils.defaultString(taskId), payloadHash, now) > 0;
        } catch (DuplicateKeyException e) {
            return stateMapper.reopenEnqueueFailed(stateId, payloadHash, now) > 0;
        }
    }

    private String stateId(String taskType, Map<String, Object> taskData, String payloadHash) {
        String jobKey = stringTaskData(taskData, QUEUE_JOB_KEY);
        String scheduledFireTime = stringTaskData(taskData, QUEUE_SCHEDULED_FIRE_TIME);
        if (StringUtils.isNotBlank(jobKey) && StringUtils.isNotBlank(scheduledFireTime)) {
            return sha256("scheduled|" + taskType + "|" + jobKey + "|" + scheduledFireTime);
        }
        return sha256("message|" + taskType + "|" + payloadHash + "|" + UUID.randomUUID());
    }

    private String legacyStateId(String taskType, String recordId) {
        return sha256("legacy|" + taskType + "|" + recordId);
    }

    private String stringTaskData(Map<String, Object> taskData, String key) {
        if (taskData == null || !taskData.containsKey(key)) {
            return null;
        }
        Object value = taskData.get(key);
        return value == null ? null : value.toString();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
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

    // 空队列启动时先创建引导消息，避免消费组创建依赖调度端先投递任务。
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
            CrestException.throwException("Redis 未配置，无法启用调度任务队列");
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

    public enum ClaimResult {
        CLAIMED,
        SKIPPED,
        DEFERRED
    }

    public record QueuedScheduledTask(String taskType, Map<String, Object> taskData, String stateId) {
        public QueuedScheduledTask(String taskType, Map<String, Object> taskData) {
            this(taskType, taskData, null);
        }
    }
}
