package io.crest.exportCenter.queue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.crest.exportCenter.dao.auto.entity.CoreExportTask;
import io.crest.exportCenter.dao.auto.mapper.CoreExportTaskMapper;
import io.crest.exportCenter.manage.ExportCenterDownLoadManage;
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
// 导出任务 Worker，负责从 Redis Streams 拉取任务并通过数据库 CAS 抢占执行权。
public class ExportTaskWorker {

    @Resource
    private ExportTaskQueueService exportTaskQueueService;
    @Resource
    private ExportCenterDownLoadManage exportCenterDownLoadManage;
    @Resource
    private CoreExportTaskMapper exportTaskMapper;

    @Value("${crest.task.queue.export.batch-size:4}")
    private int batchSize;

    @Value("${crest.task.queue.export.block-millis:1000}")
    private long blockMillis;

    @Value("${crest.task.queue.export.recovery-interval-millis:60000}")
    private long recoveryIntervalMillis;

    @Value("${crest.task.queue.export.recovery-batch-size:100}")
    private int recoveryBatchSize;

    @Value("${crest.task.queue.export.stale-millis:1800000}")
    private long staleMillis;

    @Value("${crest.task.queue.export.enqueue-retry-millis:60000}")
    private long enqueueRetryMillis;

    @Value("${crest.task.queue.export.pending-idle-millis:60000}")
    private long pendingIdleMillis;

    private final AtomicLong lastRecoveryAt = new AtomicLong(0);

    // Worker 只在队列启用后工作，旧部署继续走本机线程池。
    @Scheduled(fixedDelayString = "${crest.task.queue.export.poll-delay-millis:1000}")
    public void poll() {
        if (!exportTaskQueueService.enabled()) {
            return;
        }
        try {
            recoverAndEnqueueTasks();
            List<MapRecord<String, Object, Object>> records = exportTaskQueueService.readExportTasks(
                    batchSize,
                    Duration.ofMillis(Math.max(100, blockMillis))
            );
            handle(records);
            handle(exportTaskQueueService.claimStalePendingTasks(
                    batchSize,
                    Duration.ofMillis(Math.max(1000, pendingIdleMillis))
            ));
        } catch (Exception e) {
            LogUtil.warn("Export task queue polling skipped: " + StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    // 以数据库状态为最终真相，兜底恢复 Worker 宕机或 Redis 投递失败造成的任务悬挂。
    private void recoverAndEnqueueTasks() {
        long now = System.currentTimeMillis();
        long interval = Math.max(1000, recoveryIntervalMillis);
        long previous = lastRecoveryAt.get();
        if (now - previous < interval || !lastRecoveryAt.compareAndSet(previous, now)) {
            return;
        }
        try {
            requeueStaleInProgressTasks(now);
            enqueuePendingTasks(now);
        } catch (Exception e) {
            LogUtil.error("Export task recovery failed", e);
        }
    }

    private void requeueStaleInProgressTasks(long now) {
        long staleBefore = now - Math.max(60000, staleMillis);
        LambdaQueryWrapper<CoreExportTask> wrapper = new LambdaQueryWrapper<CoreExportTask>()
                .select(CoreExportTask::getId)
                .eq(CoreExportTask::getExportStatus, "IN_PROGRESS")
                .and(condition -> condition.isNull(CoreExportTask::getHeartbeatTime)
                        .or()
                        .lt(CoreExportTask::getHeartbeatTime, staleBefore))
                .orderByAsc(CoreExportTask::getHeartbeatTime);
        List<CoreExportTask> tasks = exportTaskMapper.selectPage(new Page<>(1, Math.max(1, recoveryBatchSize), false), wrapper).getRecords();
        for (CoreExportTask task : tasks) {
            int updated = exportTaskMapper.resetStaleInProgressTask(task.getId(), staleBefore, now, "Worker 心跳超时，任务已重新投递");
            if (updated > 0) {
                enqueuePendingTask(task.getId(), now);
            }
        }
    }

    private void enqueuePendingTasks(long now) {
        long enqueueBefore = now - Math.max(10000, enqueueRetryMillis);
        LambdaQueryWrapper<CoreExportTask> wrapper = new LambdaQueryWrapper<CoreExportTask>()
                .select(CoreExportTask::getId)
                .eq(CoreExportTask::getExportStatus, "PENDING")
                .isNotNull(CoreExportTask::getLastEnqueueTime)
                .lt(CoreExportTask::getLastEnqueueTime, enqueueBefore)
                .orderByAsc(CoreExportTask::getLastEnqueueTime);
        List<CoreExportTask> tasks = exportTaskMapper.selectPage(new Page<>(1, Math.max(1, recoveryBatchSize), false), wrapper).getRecords();
        for (CoreExportTask task : tasks) {
            enqueuePendingTask(task.getId(), now);
        }
    }

    private void enqueuePendingTask(String taskId, long now) {
        long enqueueBefore = now - Math.max(10000, enqueueRetryMillis);
        int updated = exportTaskMapper.markPendingTaskEnqueued(taskId, now, enqueueBefore);
        if (updated <= 0) {
            return;
        }
        exportTaskQueueService.enqueueExportTask(taskId);
    }

    private void handle(MapRecord<String, Object, Object> record) {
        String taskId = exportTaskQueueService.taskId(record);
        if (StringUtils.isBlank(taskId)) {
            exportTaskQueueService.acknowledge(record.getId());
            return;
        }
        try {
            boolean finished = exportCenterDownLoadManage.executeQueuedTask(taskId, exportTaskQueueService.workerId());
            if (finished) {
                exportTaskQueueService.acknowledge(record.getId());
            }
        } catch (Exception e) {
            LogUtil.error("Export task worker failed: " + taskId, e);
        }
    }

    private void handle(List<MapRecord<String, Object, Object>> records) {
        for (MapRecord<String, Object, Object> record : records) {
            handle(record);
        }
    }
}
