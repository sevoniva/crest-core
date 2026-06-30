package io.crest.dataset.server;

import io.crest.api.dataset.DatasetSyncApi;
import io.crest.api.dataset.dto.DatasetSyncLogDTO;
import io.crest.api.dataset.dto.DatasetSyncTaskPageVO;
import io.crest.api.dataset.dto.DatasetSyncTaskRequest;
import io.crest.api.dataset.dto.DatasetSyncTaskDTO;
import io.crest.dataset.sync.DatasetSyncManage;
import io.crest.dataset.sync.DatasetSyncTaskManage;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dataset-sync")
/**
 * 数据集同步任务接口控制器，负责任务配置、执行和日志查询
 */
public class DatasetSyncServer implements DatasetSyncApi {

    @Resource
    private DatasetSyncTaskManage taskManage;
    @Resource
    private DatasetSyncManage syncManage;

    /**
     * 分页查询数据集同步任务
     */
    @Override
    public DatasetSyncTaskPageVO page(Integer page, Integer pageSize, DatasetSyncTaskRequest request) {
        return taskManage.page(page, pageSize, request);
    }

    /**
     * 查询指定可视化资源依赖的同步任务
     */
    @Override
    public List<DatasetSyncTaskDTO> dependencies(Long visualizationId) {
        return taskManage.dependencies(visualizationId);
    }

    /**
     * 保存数据集同步任务配置
     */
    @Override
    public DatasetSyncTaskDTO save(DatasetSyncTaskDTO task) {
        return taskManage.save(task);
    }

    /**
     * 查询指定数据集分组的同步任务
     */
    @Override
    public DatasetSyncTaskDTO task(Long datasetGroupId) {
        return taskManage.task(datasetGroupId);
    }

    /**
     * 立即执行指定数据集分组的同步任务
     */
    @Override
    public DatasetSyncTaskDTO execute(Long datasetGroupId) throws Exception {
        return syncManage.executeNow(datasetGroupId);
    }

    /**
     * 停止指定数据集分组的同步任务
     */
    @Override
    public void stop(Long datasetGroupId) {
        taskManage.stop(datasetGroupId);
    }

    /**
     * 暂停指定数据集分组的同步任务
     */
    @Override
    public DatasetSyncTaskDTO pause(Long datasetGroupId) {
        return taskManage.pause(datasetGroupId);
    }

    /**
     * 恢复指定数据集分组的同步任务
     */
    @Override
    public DatasetSyncTaskDTO resume(Long datasetGroupId) {
        return taskManage.resume(datasetGroupId);
    }

    /**
     * 重试指定数据集分组的同步任务
     */
    @Override
    public DatasetSyncTaskDTO retry(Long datasetGroupId) throws Exception {
        return syncManage.executeNow(datasetGroupId);
    }

    /**
     * 查询指定数据集分组的同步日志
     */
    @Override
    public List<DatasetSyncLogDTO> logs(Long datasetGroupId) {
        return taskManage.logs(datasetGroupId);
    }
}
