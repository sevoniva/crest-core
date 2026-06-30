package io.crest.job.schedule;

import io.crest.runtime.ConditionalOnCrestRuntimeRole;
import io.crest.runtime.CrestRuntimeRole;
import io.crest.utils.LogUtil;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnCrestRuntimeRole({CrestRuntimeRole.ALL, CrestRuntimeRole.WORKER})
// 报表和填报调度 Worker，从 Redis Streams 消费任务，避免 scheduler 直接执行任务体。
public class ScheduledTaskWorker {

    @Resource
    private ScheduledTaskQueueService queueService;
    @Resource
    private ScheduledTaskExecutor scheduledTaskExecutor;
    @Resource
    private DataFillingTaskExecutor dataFillingTaskExecutor;

    @Value("${crest.task.queue.scheduled.batch-size:2}")
    private int batchSize;

    @Value("${crest.task.queue.scheduled.block-millis:1000}")
    private long blockMillis;

    @Value("${crest.task.queue.scheduled.pending-idle-millis:60000}")
    private long pendingIdleMillis;

    @Scheduled(fixedDelayString = "${crest.task.queue.scheduled.poll-delay-millis:1000}")
    public void poll() {
        if (!queueService.enabled()) {
            return;
        }
        try {
            List<MapRecord<String, Object, Object>> records = queueService.readScheduledTasks(
                    batchSize,
                    Duration.ofMillis(Math.max(100, blockMillis))
            );
            handle(records);
            handle(queueService.claimStalePendingTasks(
                    batchSize,
                    Duration.ofMillis(Math.max(1000, pendingIdleMillis))
            ));
        } catch (Exception e) {
            LogUtil.warn("Scheduled task queue polling skipped: " + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    private void handle(MapRecord<String, Object, Object> record) {
        ScheduledTaskQueueService.QueuedScheduledTask task = queueService.task(record);
        if (task == null) {
            queueService.acknowledge(record.getId());
            return;
        }
        ScheduledTaskQueueService.ClaimResult claimResult = queueService.claimTask(task);
        if (claimResult == ScheduledTaskQueueService.ClaimResult.SKIPPED) {
            queueService.acknowledge(record.getId());
            return;
        }
        if (claimResult == ScheduledTaskQueueService.ClaimResult.DEFERRED) {
            return;
        }
        try {
            boolean loaded = switch (task.taskType()) {
                case ScheduledTaskQueueService.TASK_TYPE_REPORT -> scheduledTaskExecutor.executeQueued(task.taskData());
                case ScheduledTaskQueueService.TASK_TYPE_DATA_FILLING -> dataFillingTaskExecutor.executeQueued(task.taskData());
                default -> false;
            };
            if (!loaded) {
                String reason = "Scheduled task executor is not configured: " + task.taskType();
                queueService.deadLetter(record, task, reason);
                queueService.failTask(task, reason);
                LogUtil.warn("Scheduled task worker moved task to dead letter: " + task.taskType());
            } else {
                queueService.completeTask(task);
            }
            queueService.acknowledge(record.getId());
        } catch (Exception e) {
            LogUtil.error("Scheduled task worker failed: " + task.taskType(), e);
            queueService.retryTaskLater(task, e.getMessage());
        }
    }

    private void handle(List<MapRecord<String, Object, Object>> records) {
        for (MapRecord<String, Object, Object> record : records) {
            handle(record);
        }
    }
}
