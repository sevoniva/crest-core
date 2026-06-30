package io.crest.api.dataset;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.dataset.dto.DatasetSyncLogDTO;
import io.crest.api.dataset.dto.DatasetSyncTaskPageVO;
import io.crest.api.dataset.dto.DatasetSyncTaskRequest;
import io.crest.api.dataset.dto.DatasetSyncTaskDTO;
import io.crest.auth.CrestApiPath;
import io.crest.auth.CrestPermit;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

import static io.crest.constant.AuthResourceEnum.DATASET;

@Tag(name = "数据集管理:同步")
@ApiSupport(order = 978)
@CrestApiPath(value = "/dataset-sync", rt = DATASET)
// 定义模块接口契约和数据传输结构
public interface DatasetSyncApi {

    @Operation(summary = "分页查询数据集同步任务")
    @CrestPermit("m:read")
    @PostMapping("tasks/{page}/{pageSize}")
    DatasetSyncTaskPageVO page(@PathVariable("page") Integer page,
                               @PathVariable("pageSize") Integer pageSize,
                               @RequestBody(required = false) DatasetSyncTaskRequest request);

    @Operation(summary = "查询可视化资源依赖的数据集同步状态")
    @CrestPermit("m:read")
    @GetMapping("dependencies/{visualizationId}")
    List<DatasetSyncTaskDTO> dependencies(@PathVariable("visualizationId") Long visualizationId);

    @Operation(summary = "保存数据集同步任务")
    @CrestPermit({"#p0.datasetGroupId+':manage'"})
    @PostMapping("record")
    DatasetSyncTaskDTO save(@RequestBody DatasetSyncTaskDTO task) throws Exception;

    @Operation(summary = "查询数据集同步任务")
    @CrestPermit({"#p0+':read'"})
    @GetMapping("task/{datasetGroupId}")
    DatasetSyncTaskDTO task(@PathVariable("datasetGroupId") Long datasetGroupId);

    @Operation(summary = "立即执行数据集同步")
    @CrestPermit({"#p0+':manage'"})
    @PostMapping("runs/{datasetGroupId}")
    DatasetSyncTaskDTO execute(@PathVariable("datasetGroupId") Long datasetGroupId) throws Exception;

    @Operation(summary = "停止数据集同步")
    @CrestPermit({"#p0+':manage'"})
    @PostMapping("lifecycle/stop/{datasetGroupId}")
    void stop(@PathVariable("datasetGroupId") Long datasetGroupId);

    @Operation(summary = "暂停数据集同步")
    @CrestPermit({"#p0+':manage'"})
    @PostMapping("lifecycle/pause/{datasetGroupId}")
    DatasetSyncTaskDTO pause(@PathVariable("datasetGroupId") Long datasetGroupId);

    @Operation(summary = "恢复数据集同步")
    @CrestPermit({"#p0+':manage'"})
    @PostMapping("lifecycle/resume/{datasetGroupId}")
    DatasetSyncTaskDTO resume(@PathVariable("datasetGroupId") Long datasetGroupId);

    @Operation(summary = "重试数据集同步")
    @CrestPermit({"#p0+':manage'"})
    @PostMapping("lifecycle/retry/{datasetGroupId}")
    DatasetSyncTaskDTO retry(@PathVariable("datasetGroupId") Long datasetGroupId) throws Exception;

    @Operation(summary = "查询数据集同步日志")
    @CrestPermit({"#p0+':read'"})
    @GetMapping("logs/{datasetGroupId}")
    List<DatasetSyncLogDTO> logs(@PathVariable("datasetGroupId") Long datasetGroupId);
}
