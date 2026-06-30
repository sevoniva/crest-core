package io.crest.api.visualization;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.visualization.request.VisualizationStoreRequest;
import io.crest.api.visualization.request.VisualizationWorkbranchQueryRequest;
import io.crest.api.visualization.vo.VisualizationStoreVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@Tag(name = "可视化管理:收藏")
@ApiSupport(order = 994)
// 定义模块接口契约和数据传输结构
public interface VisualizationStoreApi {

    @PostMapping("/runs")
    @Operation(summary = "变更收藏信息")
    void execute(@RequestBody VisualizationStoreRequest request);

    @PostMapping("/list")
    @Operation(summary = "查询收藏资源信息")
    List<VisualizationStoreVO> query(@RequestBody VisualizationWorkbranchQueryRequest request);
    @GetMapping("/favorited/{id}")
    @Operation(summary = "收藏")
    boolean favorited(@PathVariable("id") Long id);
}
