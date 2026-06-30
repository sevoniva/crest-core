package io.crest.api.visualization;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.visualization.dto.VisualizationComponentDTO;
import io.crest.api.visualization.dto.VisualizationLinkJumpDTO;
import io.crest.api.visualization.request.VisualizationLinkJumpBaseRequest;
import io.crest.api.visualization.response.VisualizationLinkJumpBaseResponse;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 可视化组件跳转配置接口。
 */
@Tag(name = "可视化管理:跳转")
@ApiSupport(order = 995)
public interface VisualizationLinkJumpApi {


    @GetMapping("/table-fields/by-view/{viewId}")
    @Operation(summary = "查询可跳转字段信息")
    List<DatasetTableFieldDTO> getTableFieldWithViewId(@PathVariable Long viewId);

    @GetMapping("/by-view/{dvId}/{viewId}")
    @Operation(summary = "根据图表ID查询跳转信息")
    VisualizationLinkJumpDTO queryWithViewId(@PathVariable Long dvId, @PathVariable Long viewId);

    @GetMapping("/visualization-jump-info/{dvId}/{resourceTable}")
    @Operation(summary = "根据可视化资源ID查询跳转信息")
    VisualizationLinkJumpBaseResponse queryVisualizationJumpInfo(@PathVariable Long dvId, @PathVariable String resourceTable);

    @PostMapping("/jump-set")
    @Operation(summary = "更新跳转信息")
    void updateJumpSet(@RequestBody VisualizationLinkJumpDTO jumpDTO);

    @PostMapping("/target-visualization-jump-info")
    @Operation(summary = "查询目标跳转信息")
    VisualizationLinkJumpBaseResponse queryTargetVisualizationJumpInfo(@RequestBody VisualizationLinkJumpBaseRequest request);

    @GetMapping({"/view-table-detail-list/{dvId}", "/view-table-detail-list/{dvId}/{resourceTable}"})
    @Operation(summary = "查询跳转明细")
    VisualizationComponentDTO viewTableDetailList(@PathVariable Long dvId, @PathVariable(required = false) String resourceTable);

    @PostMapping("/jump-set-active")
    @Operation(summary = "更新跳转信息可用状态")
    VisualizationLinkJumpBaseResponse updateJumpSetActive(@RequestBody VisualizationLinkJumpBaseRequest request);

    @DeleteMapping("/jump-set")
    @Operation(summary = "删除跳转信息")
    void deleteJumpSet(@RequestBody VisualizationLinkJumpDTO jumpDTO);

}
