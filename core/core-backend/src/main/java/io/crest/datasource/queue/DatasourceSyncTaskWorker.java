package io.crest.datasource.queue;

import io.crest.datasource.dao.auto.entity.CoreDatasourceTask;
import io.crest.datasource.manage.DatasourceSyncManage;
import io.crest.datasource.server.DatasourceTaskServer;
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
// 数据源同步 Worker，从 Redis Streams 取任务，并用数据库状态防止重复执行。
public class DatasourceSyncTaskWorker {

    @Resource
    private DatasourceSyncTaskQueueService queueService;
    @Resource
    private DatasourceSyncManage datasourceSyncManage;
    @Resource
    private DatasourceTaskServer datasourceTaskServer;

    @Value("${crest.task.queue.datasource-sync.batch-size:2}")
    private int batchSize;

    @Value("${crest.task.queue.datasource-sync.block-millis:1000}")
    private long blockMillis;

    @Value("${crest.task.queue.datasource-sync.recovery-interval-millis:60000}")
    private long recoveryIntervalMillis;

    @Value("${crest.task.queue.datasource-sync.recovery-batch-size:100}")
    private int recoveryBatchSize;

    @Value("${crest.task.queue.datasource-sync.stale-millis:1800000}")
    private long staleMillis;

    @Value("${crest.task.queue.datasource-sync.pending-idle-millis:60000}")
    private long pendingIdleMillis;

    private final AtomicLong lastRecoveryAt = new AtomicLong(0);

    @Scheduled(fixedDelayString = "${crest.task.queue.datasource-sync.poll-delay-millis:1000}")
    public void poll() {
        if (!queueService.enabled()) {
            return;
        }
        try {
            recoverAndEnqueueTasks();
            List<MapRecord<String, Object, Object>> records = queueService.readDatasourceSyncTasks(
                    batchSize,
                    Duration.ofMillis(Math.max(100, blockMillis))
            );
            handle(records);
            handle(queueService.claimStalePendingTasks(
                    batchSize,
                    Duration.ofMillis(Math.max(1000, pendingIdleMillis))
            ));
        } catch (Exception e) {
            LogUtil.warn("Datasource sync task queue polling skipped: " + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
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
            LogUtil.error("Datasource sync task recovery failed", e);
        }
    }

    private void requeueStaleInProgressTasks(long now) {
        long staleBefore = now - Math.max(60000, staleMillis);
        List<CoreDatasourceTask> tasks = datasourceTaskServer.listStaleQueuedTasks(staleBefore, recoveryBatchSize);
        for (CoreDatasourceTask task : tasks) {
            boolean reset = datasourceTaskServer.resetStaleQueuedTask(
                    task.getId(),
                    task.getDsId(),
                    staleBefore,
                    now,
                    "Worker 心跳超时，任务已重新投递"
            );
            if (reset) {
                enqueueTask(task.getDsId(), task.getId(), now);
            }
        }
    }

    private void enqueueWaitingTasks(long now) {
        long enqueueBefore = now - queueService.enqueueRetryMillis();
        List<CoreDatasourceTask> tasks = datasourceTaskServer.listQueuedTasksWaitingRetry(enqueueBefore, recoveryBatchSize);
        for (CoreDatasourceTask task : tasks) {
            enqueueTask(task.getDsId(), task.getId(), now);
        }
    }

    private void enqueueTask(Long datasourceId, Long taskId, long now) {
        if (datasourceTaskServer.markQueuedTaskEnqueued(taskId, now, queueService.enqueueRetryMillis())) {
            queueService.enqueueDatasourceSyncTask(datasourceId, taskId, null, now);
        }
    }

    private void handle(MapRecord<String, Object, Object> record) {
        DatasourceSyncTaskQueueService.QueuedDatasourceSyncTask task = queueService.task(record);
        if (task == null) {
            queueService.acknowledge(record.getId());
            return;
        }
        try {
            datasourceSyncManage.executeQueued(task.datasourceId(), task.taskId(), task.fireInstanceId(),
                    queueService.workerId(), task.enqueueTime());
            queueService.acknowledge(record.getId());
        } catch (Exception e) {
            LogUtil.error("Datasource sync task worker failed: " + task.taskId(), e);
        }
    }

    private void handle(List<MapRecord<String, Object, Object>> records) {
        for (MapRecord<String, Object, Object> record : records) {
            handle(record);
        }
    }
}
