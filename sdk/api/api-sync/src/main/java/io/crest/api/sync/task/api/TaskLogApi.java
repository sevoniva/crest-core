package io.crest.api.sync.task.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.crest.api.sync.task.dto.TaskLogGridRequest;
import io.crest.api.sync.task.vo.LogResultVO;
import io.crest.api.sync.task.vo.TaskLogVO;
import io.crest.auth.CrestApiPath;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Hidden;

import java.util.Map;

import static io.crest.constant.AuthResourceEnum.TASK;

/**
 * 数据同步任务日志接口。
 */
@Hidden
@CrestApiPath(value = "/sync/task/log", rt = TASK)
public interface TaskLogApi {
    @PostMapping("/page/{goPage}/{pageSize}")
    IPage<TaskLogVO> pager(@PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize, @RequestBody TaskLogGridRequest request);

    @GetMapping("/detail/{logId}/{fromLineNum}")
    LogResultVO logDetail(@PathVariable("logId") String logId, @PathVariable("fromLineNum") int fromLineNum);

    @PostMapping("/record")
    void saveLog(@RequestBody TaskLogVO logDetail);

    @PutMapping
    void updateLog(@RequestBody TaskLogVO logDetail);

    @DeleteMapping("/jobs/{jobId}")
    void deleteByJobId(@PathVariable("jobId") String jobId);

    @DeleteMapping("/{logId}")
    void deleteById(@PathVariable("logId") String logId);

    @PostMapping("/clear")
    void clearJobLog(@RequestBody TaskLogVO taskLogVO);

    @PostMapping("termination/{logId}")
    void terminationTask(@PathVariable("logId") String logId);

    @GetMapping("/log-resource")
    Long getLogResourceId(@PathVariable("params") Map<String, String> params);

    @GetMapping("/root-path")
    String query2Root(@PathVariable("params") Map<String, String> params);

}
