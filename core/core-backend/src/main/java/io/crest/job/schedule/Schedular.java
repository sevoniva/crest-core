package io.crest.job.schedule;

import io.crest.datasource.server.DatasourceServer;
import io.crest.lock.CrestRedisLockService;
import io.crest.runtime.ConditionalOnCrestRuntimeRole;
import io.crest.runtime.CrestRuntimeRole;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@ConditionalOnCrestRuntimeRole({CrestRuntimeRole.ALL, CrestRuntimeRole.SCHEDULER})
public class Schedular {

    @Resource
    private DatasourceServer datasourceServer;
    @Resource
    private CrestRedisLockService lockService;

    @Scheduled(cron = "0 0/3 * * * ?")
    public void updateStopJobStatus() {
        lockService.runWithLock("scheduled:update-stop-job-status", datasourceServer::updateStopJobStatus);
    }

}
