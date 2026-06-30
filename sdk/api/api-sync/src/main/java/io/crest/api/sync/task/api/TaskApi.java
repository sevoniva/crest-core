package io.crest.api.sync.task.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.sync.task.dto.TaskGridRequest;
import io.crest.api.sync.task.dto.TaskInfoDTO;
import io.crest.api.sync.task.vo.TaskInfoVO;
import io.crest.auth.CrestApiPath;
import io.crest.exception.CrestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static io.crest.constant.AuthResourceEnum.TASK;

/**
 * 数据同步任务管理接口。
 */
@Tag(name = "同步管理:任务管理")
@ApiSupport(order = 888, author = "Crest")
@CrestApiPath(value = "/sync/task", rt = TASK)
public interface TaskApi {

    @Operation(hidden = true)
    @PostMapping("/page/{goPage}/{pageSize}")
    IPage<TaskInfoVO> pager(@PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize, @RequestBody TaskGridRequest request);

    @Operation(hidden = true)
    @PostMapping
    void add(@RequestBody TaskInfoDTO jobInfo) throws CrestException;

    @Operation(hidden = true)
    @PutMapping
    void update(@RequestBody TaskInfoDTO jobInfo) throws CrestException;

    @Operation(hidden = true)
    @DeleteMapping("/{id}")
    void remove(@PathVariable(value = "id") String id) throws CrestException;

    @Operation(hidden = true)
    @GetMapping("lifecycle/start/{id}")
    void startJob(@PathVariable(value = "id") String id) throws CrestException;

    @Operation(hidden = true)
    @GetMapping("lifecycle/stop/{id}")
    void stopJob(@PathVariable(value = "id") String id) throws CrestException;

    @Operation(hidden = true)
    @GetMapping("/detail/{id}")
    TaskInfoVO getOneById(@PathVariable(value = "id") String id) throws CrestException;

    @Operation(summary = "执行一次任务")
    @GetMapping("/runs/{id}")
    void execute(@PathVariable(value = "id") String id) throws CrestException;

    @Operation(hidden = true)
    @DeleteMapping("/batch")
    void batchDelete(@RequestBody List<String> ids) throws CrestException;

    @Operation(hidden = true)
    @GetMapping("/count")
    Long count() throws CrestException;

    @GetMapping("/root-path/{id}")
    String query2Root(@PathVariable("id") Long id);

    @GetMapping("/log-resource/{id}")
    Long getLogResourceId(@PathVariable("id") String id);

}
