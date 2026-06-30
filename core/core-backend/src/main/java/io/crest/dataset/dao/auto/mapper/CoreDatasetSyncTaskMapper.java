package io.crest.dataset.dao.auto.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.crest.dataset.dao.auto.entity.CoreDatasetSyncTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CoreDatasetSyncTaskMapper extends BaseMapper<CoreDatasetSyncTask> {

    // 调度端投递前先写入投递时间，避免多 Scheduler 重复投递同一触发。
    @Update("""
            UPDATE core_dataset_sync_task
            SET task_status = 'WaitingForExecution',
                last_enqueue_time = #{now},
                next_fire_time = #{now},
                update_time = #{now},
                lock_version = COALESCE(lock_version, 0) + 1
            WHERE id = #{id}
              AND task_status IN ('WaitingForExecution', 'Stopped')
              AND (
                  task_status <> 'WaitingForExecution'
                  OR last_enqueue_time IS NULL
                  OR last_enqueue_time < #{enqueueBefore}
              )
            """)
    int markQueuedTaskEnqueued(@Param("id") Long id,
                               @Param("now") Long now,
                               @Param("enqueueBefore") Long enqueueBefore);

    // Worker 成功抢占后记录执行者和版本，数据库状态是最终防重依据。
    @Update("""
            UPDATE core_dataset_sync_task
            SET task_status = 'UnderExecution',
                last_exec_time = #{now},
                heartbeat_time = #{now},
                worker_id = #{workerId},
                retry_count = COALESCE(retry_count, 0) + 1,
                lock_version = COALESCE(lock_version, 0) + 1,
                last_error = NULL
            WHERE id = #{id}
              AND task_status <> 'UnderExecution'
            """)
    int markWorkerStarted(@Param("id") Long id,
                          @Param("workerId") String workerId,
                          @Param("now") Long now);

    // 队列 Worker 只抢占等待执行任务，避免停用任务被旧 Redis 消息重新拉起。
    @Update("""
            UPDATE core_dataset_sync_task
            SET task_status = 'UnderExecution',
                last_exec_time = #{now},
                heartbeat_time = #{now},
                worker_id = #{workerId},
                retry_count = COALESCE(retry_count, 0) + 1,
                lock_version = COALESCE(lock_version, 0) + 1,
                last_error = NULL
            WHERE id = #{id}
              AND task_status = 'WaitingForExecution'
            """)
    int claimQueuedWorkerTask(@Param("id") Long id,
                              @Param("workerId") String workerId,
                              @Param("now") Long now);

    // 带投递时间抢占队列任务，旧 Redis 消息在任务完成后无法再次拉起同一次执行。
    @Update("""
            UPDATE core_dataset_sync_task
            SET task_status = 'UnderExecution',
                last_exec_time = #{now},
                heartbeat_time = #{now},
                worker_id = #{workerId},
                retry_count = COALESCE(retry_count, 0) + 1,
                lock_version = COALESCE(lock_version, 0) + 1,
                last_error = NULL
            WHERE id = #{id}
              AND task_status = 'WaitingForExecution'
              AND last_enqueue_time = #{enqueueTime}
            """)
    int claimQueuedWorkerTaskByEnqueueTime(@Param("id") Long id,
                                           @Param("workerId") String workerId,
                                           @Param("now") Long now,
                                           @Param("enqueueTime") Long enqueueTime);

    // 长任务执行过程中刷新心跳，避免正常执行被恢复逻辑误判。
    @Update("""
            UPDATE core_dataset_sync_task
            SET heartbeat_time = #{now}
            WHERE id = #{id}
              AND task_status = 'UnderExecution'
            """)
    int touchWorkerHeartbeat(@Param("id") Long id,
                             @Param("now") Long now);

    // 超时执行中的任务回到等待态，之后再由正常投递 CAS 写入队列。
    @Update("""
            UPDATE core_dataset_sync_task
            SET task_status = 'WaitingForExecution',
                last_exec_status = 'Error',
                worker_id = NULL,
                heartbeat_time = NULL,
                last_enqueue_time = NULL,
                next_fire_time = #{now},
                last_error = #{reason},
                consecutive_failures = COALESCE(consecutive_failures, 0) + 1,
                update_time = #{now},
                lock_version = COALESCE(lock_version, 0) + 1
            WHERE id = #{id}
              AND task_status = 'UnderExecution'
              AND (heartbeat_time IS NULL OR heartbeat_time < #{staleBefore})
            """)
    int resetStaleQueuedTask(@Param("id") Long id,
                             @Param("staleBefore") Long staleBefore,
                             @Param("now") Long now,
                             @Param("reason") String reason);
}
