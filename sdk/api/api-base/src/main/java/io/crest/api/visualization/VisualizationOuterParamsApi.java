package io.crest.api.visualization;

import io.crest.api.dataset.vo.CoreDatasetGroupVO;
import io.crest.api.visualization.dto.VisualizationOuterParamsDTO;
import io.crest.api.visualization.response.VisualizationOuterParamsBaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "可视化管理:外部参数")
// 定义模块接口契约和数据传输结构
public interface VisualizationOuterParamsApi {


    @GetMapping("/by-visualization/{dvId}")
    @Operation(summary = "查询")
    VisualizationOuterParamsDTO queryWithVisualizationId(@PathVariable("dvId") String dvId);

    @PostMapping("/outer-params-settings")
    @Operation(summary = "更新")
    void updateOuterParamsSet(@RequestBody VisualizationOuterParamsDTO OuterParamsDTO);

    @GetMapping("/outer-params-info/{dvId}")
    @Operation(summary = "查询基础信息")
    VisualizationOuterParamsBaseResponse getOuterParamsInfo(@PathVariable("dvId") String dvId);

    @GetMapping("/datasources/by-visualization/{dvId}")
    @Operation(summary = "查询涉及数据集基础信息")
    List<CoreDatasetGroupVO> queryDsWithVisualizationId(@PathVariable("dvId") String dvId);
}
