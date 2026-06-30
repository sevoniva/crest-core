package io.crest.api.visualization;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.visualization.request.VisualizationSubjectRequest;
import io.crest.api.visualization.vo.VisualizationSubjectVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "可视化管理:主题")
@ApiSupport(order = 993)
// 定义模块接口契约和数据传输结构
public interface VisualizationSubjectApi {

    @PostMapping("/list")
    @Operation(summary = "查询")
    List<VisualizationSubjectVO> query(@RequestBody VisualizationSubjectRequest request);

    @PostMapping("/subjects-with-groups")
    @Operation(summary = "分组查询")
    List<VisualizationSubjectVO> querySubjectWithGroup(@RequestBody VisualizationSubjectRequest request);

    @PutMapping
    @Operation(summary = "更新")
    void update(@RequestBody VisualizationSubjectRequest request);

    @DeleteMapping("/{id}")
    @Operation(summary = "删除")
    void delete(@PathVariable("id") String id);

}
