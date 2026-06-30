package io.crest.listener;

import io.crest.datasource.dao.auto.entity.CoreDatasourceTask;
import io.crest.dataset.dao.auto.entity.CoreDatasetSyncTask;
import io.crest.dataset.sync.DatasetSyncTaskManage;
import io.crest.datasource.manage.DataSourceManage;
import io.crest.datasource.manage.DatasourceSyncManage;
import io.crest.datasource.manage.EngineManage;
import io.crest.datasource.provider.CalciteProvider;
import io.crest.datasource.server.DatasourceServer;
import io.crest.datasource.server.DatasourceTaskServer;
import io.crest.runtime.CrestRuntimeRole;
import io.crest.system.dao.auto.entity.CoreSysSetting;
import io.crest.system.manage.SysParameterManage;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
@Order(value = 2)
public class DataSourceInitStartListener implements ApplicationListener<ApplicationReadyEvent> {
    @Resource
    private DatasourceSyncManage datasourceSyncManage;
    @Resource
    private DatasourceServer datasourceServer;
    @Resource
    private DataSourceManage dataSourceManage;
    @Resource
    private DatasourceTaskServer datasourceTaskServer;
    @Resource
    private DatasetSyncTaskManage datasetSyncTaskManage;
    @Resource
    private CalciteProvider calciteProvider;
    @Resource
    private EngineManage engineManage;
    @Resource
    private SysParameterManage sysParameterManage;
    @Resource
    private Environment environment;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        try {
            engineManage.initSimpleEngine();
        } catch (Exception e) {
            io.crest.utils.LogUtil.error(e.getMessage(), e);
        }
        if (shouldPreloadDatasourcePool()) {
            try {
                calciteProvider.initConnectionPool();
            } catch (Exception e) {
                io.crest.utils.LogUtil.error(e.getMessage(), e);
            }
        }
        if (!CrestRuntimeRole.from(environment).runsScheduler()) {
            return;
        }
        List<CoreDatasourceTask> list = datasourceTaskServer.listAll();
        for (CoreDatasourceTask task : list) {
            try {
                if (!Strings.CI.equals(task.getSyncRate(), DatasourceTaskServer.ScheduleType.RIGHTNOW.toString())) {
                    if (task.getEndTime() != null && task.getEndTime() > 0) {
                        if (task.getEndTime() > System.currentTimeMillis()) {
                            datasourceSyncManage.addSchedule(task);
                        } else {
                            datasourceSyncManage.deleteSchedule(task);
                        }
                    } else {
                        datasourceSyncManage.addSchedule(task);
                    }
                }
            } catch (Exception e) {
                io.crest.utils.LogUtil.error(e.getMessage(), e);
            }
        }

        List<CoreDatasetSyncTask> datasetSyncTasks = List.of();
        try {
            datasetSyncTaskManage.recoverInterruptedTasks();
            datasetSyncTasks = datasetSyncTaskManage.listAll();
        } catch (Exception e) {
            io.crest.utils.LogUtil.error(e.getMessage(), e);
        }
        for (CoreDatasetSyncTask task : datasetSyncTasks) {
            try {
                if (datasetSyncTaskManage.shouldRestoreSchedule(task)) {
                    if (task.getEndTime() != null && task.getEndTime() > 0) {
                        if (task.getEndTime() > System.currentTimeMillis()) {
                            datasetSyncTaskManage.addSchedule(task);
                        } else {
                            datasetSyncTaskManage.deleteSchedule(task);
                        }
                    } else {
                        datasetSyncTaskManage.addSchedule(task);
                    }
                }
            } catch (Exception e) {
                io.crest.utils.LogUtil.error(e.getMessage(), e);
            }
        }

        try {
            List<CoreSysSetting> coreSysSettings = sysParameterManage.groupList("basic.");
            datasourceServer.addJob(coreSysSettings);
        } catch (Exception e) {
            io.crest.utils.LogUtil.error(e.getMessage(), e);
        }
        dataSourceManage.encryptDsConfig();
    }

    /**
     * 生产多副本下默认关闭启动预热，避免每个 Pod 同时占用业务库连接。
     */
    private boolean shouldPreloadDatasourcePool() {
        String explicit = StringUtils.defaultIfBlank(environment.getProperty("crest.datasource.pool.preload.enabled"),
                environment.getProperty("CREST_DATASOURCE_POOL_PRELOAD_ENABLED"));
        if (StringUtils.isNotBlank(explicit)) {
            return Boolean.parseBoolean(explicit.trim());
        }
        boolean productionMode = environment.getProperty("crest.production-mode", Boolean.class,
                environment.getProperty("CREST_PRODUCTION_MODE", Boolean.class, false));
        return !productionMode && CrestRuntimeRole.from(environment) == CrestRuntimeRole.ALL;
    }

}
