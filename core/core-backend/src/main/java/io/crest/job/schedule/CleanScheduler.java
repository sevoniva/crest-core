package io.crest.job.schedule;

import io.crest.datasource.server.DatasourceTaskServer;
import io.crest.exportCenter.manage.ExportCenterManage;
import io.crest.lock.CrestRedisLockService;
import io.crest.runtime.ConditionalOnCrestRuntimeRole;
import io.crest.runtime.CrestRuntimeRole;
import io.crest.utils.LogUtil;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnCrestRuntimeRole({CrestRuntimeRole.ALL, CrestRuntimeRole.SCHEDULER})
public class CleanScheduler {

    @Resource(name = "exportCenterManage")
    private ExportCenterManage exportCenterManage;
    @Resource(name = "datasourceTaskServer")
    private DatasourceTaskServer datasourceTaskServer;
    @Resource
    private CrestRedisLockService lockService;

    @Scheduled(cron = "0 0 0 * * ?")
    public void clean() {
        lockService.runWithLock("scheduled:clean-export-files", () -> {
            LogUtil.info("Start to execute export file cleaner ...");
            exportCenterManage.cleanLog();
            LogUtil.info("Execute export file cleaner success");
        });
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanSyncLog() {
        lockService.runWithLock("scheduled:clean-sync-log", () -> {
            LogUtil.info("Start to clean sync log ...");
            datasourceTaskServer.cleanLog();
            LogUtil.info("End to clean sync log.");
        });
    }
}
