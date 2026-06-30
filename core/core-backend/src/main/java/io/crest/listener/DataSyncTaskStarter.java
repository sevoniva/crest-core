package io.crest.listener;

import io.crest.job.schedule.DataFillingTaskExecutor;
import io.crest.job.schedule.ScheduledTaskExecutor;
import io.crest.job.schedule.DataSyncTaskExecutor;
import io.crest.runtime.ConditionalOnCrestRuntimeRole;
import io.crest.runtime.CrestRuntimeRole;
import io.crest.utils.LogUtil;
import jakarta.annotation.Resource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(value = 4)
@ConditionalOnCrestRuntimeRole({CrestRuntimeRole.ALL, CrestRuntimeRole.SCHEDULER})
public class DataSyncTaskStarter implements ApplicationRunner {

    @Resource
    private ScheduledTaskExecutor taskExecutor;

    @Resource
    private DataFillingTaskExecutor dataFillingTaskExecutor;

    @Resource
    private DataSyncTaskExecutor dataSyncTaskExecutor;

    @Override
    public void run(ApplicationArguments args) {
        try {
            taskExecutor.init();
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e.getCause());
        }
        try {
            dataFillingTaskExecutor.init();
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e.getCause());
        }
        try {
            dataSyncTaskExecutor.init();
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e.getCause());
        }
    }
}
