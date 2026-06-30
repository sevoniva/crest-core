package io.crest.job.schedule;


import io.crest.datasource.server.DatasourceServer;
import io.crest.utils.CommonBeanFactory;
import io.crest.utils.LogUtil;
import jakarta.annotation.Resource;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;


@Component
public class CheckDsStatusJob implements Job {
    @Resource
    private DatasourceServer datasourceServer;

    public CheckDsStatusJob() {
        datasourceServer = (DatasourceServer) CommonBeanFactory.getBean(DatasourceServer.class);
    }

    @Override
    // 执行当前业务请求并写入处理结果
    public void execute(JobExecutionContext context) throws JobExecutionException {
        LogUtil.info("Begin to check ds status...");
        datasourceServer.updateDatasourceStatus();
    }

}
