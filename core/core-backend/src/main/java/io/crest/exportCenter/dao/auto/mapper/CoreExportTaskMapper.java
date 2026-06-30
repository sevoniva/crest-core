package io.crest.exportCenter.dao.auto.mapper;

import io.crest.exportCenter.dao.auto.entity.CoreExportTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 导出任务 Mapper。
 */
@Mapper
public interface CoreExportTaskMapper extends BaseMapper<CoreExportTask> {

    // Worker 执行前通过数据库 CAS 抢占任务，避免 Redis 重复消息导致并发执行。
    @Update("""
            UPDATE core_export_task
            SET export_status = 'IN_PROGRESS',
                export_progress = '0',
                msg = NULL,
                last_error = NULL,
                file_size = NULL,
                file_size_unit = NULL,
                worker_id = #{workerId},
                heartbeat_time = #{now},
                retry_count = COALESCE(retry_count, 0) + 1,
                lock_version = COALESCE(lock_version, 0) + 1,
                export_machine_name = #{machineName}
            WHERE id = #{id}
              AND export_status = 'PENDING'
            """)
    int claimPendingTask(@Param("id") String id,
                         @Param("workerId") String workerId,
                         @Param("machineName") String machineName,
                         @Param("now") Long now);

    // 只重投已进入队列链路的任务，避免误执行旧版外部 API 仅入库任务。
    @Update("""
            UPDATE core_export_task
            SET last_enqueue_time = #{now},
                next_fire_time = #{now},
                lock_version = COALESCE(lock_version, 0) + 1
            WHERE id = #{id}
              AND export_status = 'PENDING'
              AND last_enqueue_time IS NOT NULL
              AND last_enqueue_time < #{enqueueBefore}
            """)
    int markPendingTaskEnqueued(@Param("id") String id,
                                @Param("now") Long now,
                                @Param("enqueueBefore") Long enqueueBefore);

    // 超时任务回到待执行状态，由后续 CAS 抢占保证同一任务只会被一个 Worker 执行。
    @Update("""
            UPDATE core_export_task
            SET export_status = 'PENDING',
                export_progress = '0',
                worker_id = NULL,
                next_fire_time = #{now},
                msg = #{reason},
                last_error = #{reason},
                lock_version = COALESCE(lock_version, 0) + 1
            WHERE id = #{id}
              AND export_status = 'IN_PROGRESS'
              AND (heartbeat_time IS NULL OR heartbeat_time < #{staleBefore})
            """)
    int resetStaleInProgressTask(@Param("id") String id,
                                 @Param("staleBefore") Long staleBefore,
                                 @Param("now") Long now,
                                 @Param("reason") String reason);
}
