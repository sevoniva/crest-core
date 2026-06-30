package io.crest.api.threshold;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.threshold.dto.*;
import io.crest.api.threshold.vo.ThresholdGridVO;
import io.crest.api.threshold.vo.ThresholdInstanceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "阈值告警")
@ApiSupport(order = 888, author = "Crest")
// 定义模块接口契约和数据传输结构
public interface ThresholdApi {

    @Operation(summary = "保存")
    @PostMapping("/record")
    void save(@RequestBody ThresholdCreator creator);

    @Operation(summary = "修改")
    @PutMapping
    void edit(@RequestBody ThresholdCreator creator);

    @Operation(summary = "查询列表")
    @Parameters({
            @Parameter(name = "goPage", description = "目标页码", required = true, in = ParameterIn.PATH),
            @Parameter(name = "pageSize", description = "每页容量", required = true, in = ParameterIn.PATH),
            @Parameter(name = "request", description = "过滤条件", required = true)
    })
    @PostMapping("/page/{goPage}/{pageSize}")
    IPage<ThresholdGridVO> pager(@PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize, @RequestBody ThresholdGridRequest request);

    @Operation(summary = "查询表单")
    @GetMapping("/form-info/{id}/{resourceTable}")
    ThresholdCreator formInfo(@PathVariable("id") Long id, @PathVariable("resourceTable") String resourceTable);

    @Operation(summary = "切换可用")
    @PostMapping("/switch")
    void switchEnable(@RequestBody ThresholdSwitchRequest request);

    @Operation(summary = "删除")
    @DeleteMapping("/{resourceTable}")
    void delete(@RequestBody List<Long> idList, @PathVariable("resourceTable") String resourceTable);

    @Operation(summary = "批量设置接收人")
    @PostMapping("/batch-recipients")
    void batchReci(@RequestBody ThresholdBatchReciRequest request);

    @Operation(summary = "查询实例列表")
    @Parameters({
            @Parameter(name = "goPage", description = "目标页码", required = true, in = ParameterIn.PATH),
            @Parameter(name = "pageSize", description = "每页容量", required = true, in = ParameterIn.PATH),
            @Parameter(name = "request", description = "过滤条件", required = true)
    })
    @PostMapping("/instances/page/{goPage}/{pageSize}")
    IPage<ThresholdInstanceVO> instancePager(@PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize, @RequestBody ThresholdInstanceRequest request);

    @Operation(summary = "预览信息")
    @PostMapping("/preview")
    String preview(@RequestBody ThresholdPreviewRequest request);

    @Operation(summary = "视图是否设置了阈值告警")
    @GetMapping("/any-threshold/{chartId}/{resourceTable}")
    boolean anyThreshold(@PathVariable("chartId") Long chartId, @PathVariable("resourceTable") String resourceTable);

    @Operation(summary = "根据视图ID删除")
    @DeleteMapping("/charts/{chartId}/{resourceTable}")
    void deleteWithChart(@PathVariable("chartId") Long chartId, @PathVariable("resourceTable") String resourceTable);
}
