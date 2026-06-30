package io.crest.job.schedule;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 报表和填报调度队列状态 Mapper。
 */
@Mapper
public interface ScheduledTaskQueueStateMapper {

    @Insert("""
            INSERT INTO core_scheduled_task_queue_state (
                state_id, task_type, task_id, payload_hash, status, enqueue_time,
                retry_count, lock_version
            ) VALUES (
                #{stateId}, #{taskType}, #{taskId}, #{payloadHash}, 'PENDING', #{enqueueTime},
                0, 0
            )
            """)
    int insertPending(@Param("stateId") String stateId,
                      @Param("taskType") String taskType,
                      @Param("taskId") String taskId,
                      @Param("payloadHash") String payloadHash,
                      @Param("enqueueTime") Long enqueueTime);

    @Select("""
            SELECT status
            FROM core_scheduled_task_queue_state
            WHERE state_id = #{stateId}
            """)
    String selectStatus(@Param("stateId") String stateId);

    // Worker 执行前抢占待执行记录，数据库状态是 Redis 重复消息之外的最终防重依据。
    @Update("""
            UPDATE core_scheduled_task_queue_state
            SET status = 'IN_PROGRESS',
                worker_id = #{workerId},
                start_time = #{now},
                heartbeat_time = #{now},
                retry_count = COALESCE(retry_count, 0) + 1,
                lock_version = COALESCE(lock_version, 0) + 1,
                last_error = NULL
            WHERE state_id = #{stateId}
              AND status = 'PENDING'
            """)
    int claimPending(@Param("stateId") String stateId,
                     @Param("workerId") String workerId,
                     @Param("now") Long now);

    // Worker 异常退出后，超时执行记录允许重新回到待执行状态。
    @Update("""
            UPDATE core_scheduled_task_queue_state
            SET status = 'PENDING',
                worker_id = NULL,
                heartbeat_time = NULL,
                last_error = #{reason},
                lock_version = COALESCE(lock_version, 0) + 1
            WHERE state_id = #{stateId}
              AND status = 'IN_PROGRESS'
              AND (heartbeat_time IS NULL OR heartbeat_time < #{staleBefore})
            """)
    int resetStaleInProgress(@Param("stateId") String stateId,
                             @Param("staleBefore") Long staleBefore,
                             @Param("reason") String reason);

    @Update("""
            UPDATE core_scheduled_task_queue_state
            SET status = 'COMPLETED',
                end_time = #{now},
                heartbeat_time = #{now},
                worker_id = NULL,
                lock_version = COALESCE(lock_version, 0) + 1,
                last_error = NULL
            WHERE state_id = #{stateId}
            """)
    int complete(@Param("stateId") String stateId,
                 @Param("now") Long now);

    @Update("""
            UPDATE core_scheduled_task_queue_state
            SET status = 'FAILED',
                end_time = #{now},
                heartbeat_time = #{now},
                worker_id = NULL,
                last_error = #{reason},
                lock_version = COALESCE(lock_version, 0) + 1
            WHERE state_id = #{stateId}
            """)
    int fail(@Param("stateId") String stateId,
             @Param("now") Long now,
             @Param("reason") String reason);

    @Update("""
            UPDATE core_scheduled_task_queue_state
            SET status = 'ENQUEUE_FAILED',
                end_time = #{now},
                heartbeat_time = #{now},
                worker_id = NULL,
                last_error = #{reason},
                lock_version = COALESCE(lock_version, 0) + 1
            WHERE state_id = #{stateId}
              AND status = 'PENDING'
            """)
    int markEnqueueFailed(@Param("stateId") String stateId,
                          @Param("now") Long now,
                          @Param("reason") String reason);

    @Update("""
            UPDATE core_scheduled_task_queue_state
            SET status = 'PENDING',
                payload_hash = #{payloadHash},
                enqueue_time = #{enqueueTime},
                start_time = NULL,
                end_time = NULL,
                heartbeat_time = NULL,
                worker_id = NULL,
                last_error = NULL,
                lock_version = COALESCE(lock_version, 0) + 1
            WHERE state_id = #{stateId}
              AND status = 'ENQUEUE_FAILED'
            """)
    int reopenEnqueueFailed(@Param("stateId") String stateId,
                            @Param("payloadHash") String payloadHash,
                            @Param("enqueueTime") Long enqueueTime);

    @Update("""
            UPDATE core_scheduled_task_queue_state
            SET status = 'PENDING',
                worker_id = NULL,
                heartbeat_time = NULL,
                last_error = #{reason},
                lock_version = COALESCE(lock_version, 0) + 1
            WHERE state_id = #{stateId}
            """)
    int retryLater(@Param("stateId") String stateId,
                   @Param("reason") String reason);
}
