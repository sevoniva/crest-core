package io.crest.api.exportCenter;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.auth.CrestPermit;
import io.crest.model.ExportTaskDTO;
import io.crest.auth.CrestApiPath;
import io.crest.result.ResultMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static io.crest.constant.AuthResourceEnum.DATASOURCE;

/**
 * 定义导出中心任务查询、下载和维护接口
 */
@Tag(name = "数据导出中心")
@ApiSupport(order = 971)
public interface ExportCenterApi {


    /**
     * 查询各状态导出任务数量
     */
    @PostMapping("/export-tasks/records")
    public Map<String, Long> exportTasks();

    /**
     * 分页查询指定状态的导出任务
     */
    @CrestPermit("m:read")
    @PostMapping("/export-tasks/{status}/{goPage}/{pageSize}")
    IPage<ExportTaskDTO> pager(@PathVariable("goPage") String goPage, @PathVariable("pageSize") String pageSize, @PathVariable String status);

    /**
     * 删除指定导出任务记录
     */
    @Operation(summary = "删除单条记录")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id);

    /**
     * 批量删除导出任务记录
     */
    @Operation(summary = "批量删除")
    @DeleteMapping
    public void delete(@RequestBody List<String> ids);

    /**
     * 按类型删除全部导出任务记录
     */
    @Operation(summary = "删除")
    @DeleteMapping("/all/{type}")
    public void deleteAll(@PathVariable String type);

    /**
     * 下载导出任务生成的文件
     */
    @Operation(summary = "下载")
    @GetMapping("/download/{id}")
    public void download(@PathVariable String id, @RequestParam(required = false) String ticket, HttpServletResponse response) throws Exception;

    /**
     * 生成导出文件下载票据地址
     */
    @Operation(summary = "生成下载Url")
    @GetMapping("/download-tickets/{id}")
    public ResultMessage generateDownloadUri(@PathVariable String id) throws Exception;

    /**
     * 重试指定导出任务
     */
    @Operation(summary = "重试")
    @PostMapping("/retry/{id}")
    public void retry(@PathVariable String id);

    /**
     * 查询当前导出行数限制
     */
    @PostMapping("/export-limit")
    public String exportLimit();

}
