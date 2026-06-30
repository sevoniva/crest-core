package io.crest.api.visualization;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.visualization.request.VisualizationSubjectRequest;
import io.crest.api.visualization.request.VisualizationWatermarkRequest;
import io.crest.api.visualization.vo.VisualizationSubjectVO;
import io.crest.api.visualization.vo.VisualizationWatermarkVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "可视化管理:水印")
@ApiSupport(order = 994)
// 定义模块接口契约和数据传输结构
public interface VisualizationWatermarkApi {


    @ResponseBody
    @GetMapping("/list")
    @Operation(summary = "查询")
    VisualizationWatermarkVO getWatermarkInfo();

    @ResponseBody
    @PostMapping("/record")
    @Tag(name = "保存")
    @Operation(summary = "保存")
    void saveWatermarkInfo(@RequestBody VisualizationWatermarkRequest watermarkRequest);

}
