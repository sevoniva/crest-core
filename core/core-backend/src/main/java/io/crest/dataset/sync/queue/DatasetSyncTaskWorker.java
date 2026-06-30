package io.crest.dataset.sync.queue;

import io.crest.dataset.dao.auto.entity.CoreDatasetSyncTask;
import io.crest.dataset.sync.DatasetSyncManage;
import io.crest.dataset.sync.DatasetSyncTaskManage;
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
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnCrestRuntimeRole({CrestRuntimeRole.ALL, CrestRuntimeRole.WORKER})
// 数据集同步 Worker，从 Redis Streams 取任务，真正执行前仍由数据库状态抢占防重。
public class DatasetSyncTaskWorker {

    @Resource
    private DatasetSyncTaskQueueService queueService;
    @Resource
    private DatasetSyncManage datasetSyncManage;
    @Resource
    private DatasetSyncTaskManage taskManage;

    @Value("${crest.task.queue.sync.batch-size:2}")
    private int batchSize;

    @Value("${crest.task.queue.sync.block-millis:1000}")
    private long blockMillis;

    @Value("${crest.task.queue.sync.recovery-interval-millis:60000}")
    private long recoveryIntervalMillis;

    @Value("${crest.task.queue.sync.recovery-batch-size:100}")
    private int recoveryBatchSize;

    @Value("${crest.task.queue.sync.stale-millis:1800000}")
    private long staleMillis;

    @Value("${crest.task.queue.sync.pending-idle-millis:60000}")
    private long pendingIdleMillis;

    private final AtomicLong lastRecoveryAt = new AtomicLong(0);

    @Scheduled(fixedDelayString = "${crest.task.queue.sync.poll-delay-millis:1000}")
    public void poll() {
        if (!queueService.enabled()) {
            return;
        }
        try {
            recoverAndEnqueueTasks();
            List<MapRecord<String, Object, Object>> records = queueService.readDatasetSyncTasks(
                    batchSize,
                    Duration.ofMillis(Math.max(100, blockMillis))
            );
            handle(records);
            handle(queueService.claimStalePendingTasks(
                    batchSize,
                    Duration.ofMillis(Math.max(1000, pendingIdleMillis))
            ));
        } catch (Exception e) {
            LogUtil.warn("Dataset sync task queue polling skipped: " + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    private void recoverAndEnqueueTasks() {
        long now = System.currentTimeMillis();
        long interval = Math.max(1000, recoveryIntervalMillis);
        long previous = lastRecoveryAt.get();
        if (now - previous < interval || !lastRecoveryAt.compareAndSet(previous, now)) {
            return;
        }
        try {
            requeueStaleInProgressTasks(now);
            enqueueWaitingTasks(now);
        } catch (Exception e) {
            LogUtil.error("Dataset sync task recovery failed", e);
        }
    }

    private void requeueStaleInProgressTasks(long now) {
        long staleBefore = now - Math.max(60000, staleMillis);
        List<CoreDatasetSyncTask> tasks = taskManage.listStaleQueuedTasks(now, staleMillis, recoveryBatchSize);
        for (CoreDatasetSyncTask task : tasks) {
            boolean reset = taskManage.resetStaleQueuedTask(
                    task.getId(),
                    staleBefore,
                    "Worker 心跳超时，任务已重新投递"
            );
            if (reset) {
                enqueueTask(task.getDatasetGroupId(), task.getId(), now);
            }
        }
    }

    private void enqueueWaitingTasks(long now) {
        long enqueueBefore = now - queueService.enqueueRetryMillis();
        List<CoreDatasetSyncTask> tasks = taskManage.listQueuedTasksWaitingRetry(enqueueBefore, recoveryBatchSize);
        for (CoreDatasetSyncTask task : tasks) {
            enqueueTask(task.getDatasetGroupId(), task.getId(), now);
        }
    }

    private void enqueueTask(Long datasetGroupId, Long taskId, long now) {
        if (taskManage.markQueuedTaskEnqueued(taskId, now, queueService.enqueueRetryMillis())) {
            queueService.enqueueDatasetSyncTask(datasetGroupId, taskId, null, false, now);
        }
    }

    private void handle(MapRecord<String, Object, Object> record) {
        DatasetSyncTaskQueueService.QueuedDatasetSyncTask task = queueService.task(record);
        if (task == null) {
            queueService.acknowledge(record.getId());
            return;
        }
        try {
            datasetSyncManage.executeQueued(task.datasetGroupId(), task.taskId(), task.triggerType(), task.manual(),
                    queueService.workerId(), task.enqueueTime());
            queueService.acknowledge(record.getId());
        } catch (Exception e) {
            LogUtil.error("Dataset sync task worker failed: " + task.taskId(), e);
        }
    }

    private void handle(List<MapRecord<String, Object, Object>> records) {
        for (MapRecord<String, Object, Object> record : records) {
            handle(record);
        }
    }
}
