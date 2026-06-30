package io.crest.api.chart;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.chart.vo.ChartBaseVO;
import io.crest.api.chart.vo.ViewSelectorVO;
import io.crest.extensions.view.dto.ChartViewDTO;
import io.crest.extensions.view.dto.ChartViewFieldDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "图表管理:查看")
@ApiSupport(order = 988)
// 定义模块接口契约和数据传输结构
public interface ChartViewApi {
    @Operation(summary = "查询图表详情并同时计算数据", hidden = true)
    @PostMapping("detail/{id}")
    ChartViewDTO getData(@PathVariable Long id) throws Exception;

    @Operation(summary = "获取图表字段")
    @PostMapping("by-dataset-query/{id}/{chartId}")
    Map<String, List<ChartViewFieldDTO>> listByDQ(@PathVariable Long id, @PathVariable Long chartId, @RequestBody ChartViewDTO dto);

    @Operation(summary = "保存图表")
    @PostMapping("record")
    ChartViewDTO save(@RequestBody ChartViewDTO dto) throws Exception;

    @Operation(summary = "检查是否同数据集")
    @GetMapping("/same-dataset-check/{viewIdSource}/{viewIdTarget}")
    String checkSameDataSet(@PathVariable String viewIdSource, @PathVariable String viewIdTarget);

    @Operation(summary = "查询图表详情")
    @PostMapping("detail/{id}/{resourceTable}")
    ChartViewDTO getDetail(@PathVariable Long id, @PathVariable String resourceTable);

    @Operation(summary = "查询仪表板下视图项")
    @GetMapping("/view-options/{resourceId}")
    List<ViewSelectorVO> viewOption(@PathVariable("resourceId") Long resourceId);

    @Operation(summary = "视图复制字段")
    @PostMapping("fields/copy/{id}/{chartId}")
    void copyField(@PathVariable Long id, @PathVariable Long chartId);

    @Operation(summary = "视图删除字段")
    @DeleteMapping("/fields/{id}")
    void fieldRemoval(@PathVariable Long id);

    @Operation(summary = "清空当前视图计算字段")
    @DeleteMapping("/chart-fields/{chartId}")
    void chartFieldRemoval(@PathVariable Long chartId);

    @Operation(summary = "视图头部信息")
    @GetMapping("/base-info/{id}/{resourceTable}")
    ChartBaseVO chartBaseInfo(@PathVariable("id") Long id, @PathVariable String resourceTable);
}
