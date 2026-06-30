package io.crest.datasource.dao.auto.mapper;

import io.crest.datasource.dao.auto.entity.CoreDatasourceTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 数据源同步任务 Mapper。
 */
@Mapper
public interface CoreDatasourceTaskMapper extends BaseMapper<CoreDatasourceTask> {

    // 调度端投递前先写入投递时间，避免多 Scheduler 重复投递同一触发。
    @Update("""
            UPDATE core_datasource_sync_task
            SET last_enqueue_time = #{now},
                next_fire_time = #{now},
                lock_version = COALESCE(lock_version, 0) + 1
            WHERE id = #{id}
              AND (last_enqueue_time IS NULL OR last_enqueue_time < #{enqueueBefore})
            """)
    int markQueuedTaskEnqueued(@Param("id") Long id,
                               @Param("now") Long now,
                               @Param("enqueueBefore") Long enqueueBefore);

    // Worker 成功抢占后记录执行者和心跳，后续恢复逻辑以该状态为准。
    @Update("""
            UPDATE core_datasource_sync_task
            SET worker_id = #{workerId},
                heartbeat_time = #{now},
                retry_count = COALESCE(retry_count, 0) + 1,
                lock_version = COALESCE(lock_version, 0) + 1,
                last_error = NULL
            WHERE id = #{id}
              AND task_status = 'UnderExecution'
            """)
    int markQueuedWorkerStarted(@Param("id") Long id,
                                @Param("workerId") String workerId,
                                @Param("now") Long now);

    // 带投递时间抢占队列任务，完成后 last_enqueue_time 清空，旧消息无法重复执行。
    @Update("""
            UPDATE core_datasource_sync_task
            SET task_status = 'UnderExecution',
                last_exec_time = #{now},
                worker_id = #{workerId},
                heartbeat_time = #{now},
                retry_count = COALESCE(retry_count, 0) + 1,
                lock_version = COALESCE(lock_version, 0) + 1,
                last_error = NULL
            WHERE id = #{id}
              AND task_status = 'WaitingForExecution'
              AND last_enqueue_time = #{enqueueTime}
            """)
    int markQueuedWorkerStartedByEnqueueTime(@Param("id") Long id,
                                             @Param("workerId") String workerId,
                                             @Param("now") Long now,
                                             @Param("enqueueTime") Long enqueueTime);

    // 长任务按表处理时刷新心跳，避免正常执行被超时恢复误判。
    @Update("""
            UPDATE core_datasource_sync_task
            SET heartbeat_time = #{now}
            WHERE id = #{id}
              AND worker_id IS NOT NULL
            """)
    int touchQueuedTaskHeartbeat(@Param("id") Long id,
                                 @Param("now") Long now);

    // Worker 结束后清理队列执行态，防止恢复任务重复投递已完成触发。
    @Update("""
            UPDATE core_datasource_sync_task
            SET worker_id = NULL,
                heartbeat_time = #{now},
                last_enqueue_time = NULL,
                next_fire_time = NULL,
                last_error = #{lastError},
                lock_version = COALESCE(lock_version, 0) + 1
            WHERE id = #{id}
            """)
    int finishQueuedTask(@Param("id") Long id,
                         @Param("now") Long now,
                         @Param("lastError") String lastError);

    // 超时执行中的任务回到等待态，之后再由正常投递 CAS 写入队列。
    @Update("""
            UPDATE core_datasource_sync_task
            SET task_status = 'WaitingForExecution',
                worker_id = NULL,
                heartbeat_time = NULL,
                last_enqueue_time = NULL,
                next_fire_time = #{now},
                last_error = #{reason},
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
