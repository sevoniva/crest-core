package io.crest.api.visualization;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.commons.BaseRspModel;
import io.crest.api.visualization.request.VisualizationLinkageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 可视化组件联动配置接口。
 */

@Tag(name = "可视化管理:联动")
@ApiSupport(order = 996)
public interface VisualizationLinkageApi {


    @PostMapping("/linkage-gather")
    @Operation(summary = "查询联动信息")
    Map viewLinkageGather(@RequestBody VisualizationLinkageRequest request);

    @PostMapping("/linkage-gather-array")
    @Operation(summary = "查询联动信息数组")
    List viewLinkageGatherArray(@RequestBody VisualizationLinkageRequest request);

    @PostMapping("/linkage")
    @Operation(summary = "保存联动信息")
    BaseRspModel saveLinkage(@RequestBody VisualizationLinkageRequest request);

    @GetMapping("/visualization-linkage-info/{dvId}/{resourceTable}")
    @Operation(summary = "根据资源ID查询联动信息")
    Map<String, List<String>> getVisualizationAllLinkageInfo(@PathVariable Long dvId,@PathVariable String resourceTable);

    @PostMapping("/linkage-active-state")
    @Operation(summary = "修改联动信息可用状态")
    Map updateLinkageActive(@RequestBody VisualizationLinkageRequest request);


    @DeleteMapping("/linkage")
    @Operation(summary = "删除图表联动信息")
    void deleteLinkage(@RequestBody VisualizationLinkageRequest request);


}
