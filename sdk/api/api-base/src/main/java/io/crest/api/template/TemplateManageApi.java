package io.crest.api.template;

import io.crest.api.template.dto.TemplateManageDTO;
import io.crest.api.template.request.TemplateManageBatchRequest;
import io.crest.api.template.request.TemplateManageRequest;
import io.crest.api.template.vo.VisualizationTemplateVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "模版管理:基础")
// 定义模块接口契约和数据传输结构
public interface TemplateManageApi {

    @PostMapping("/templates")
    @Operation(summary = "模版列表")
    List<TemplateManageDTO> templateList(@RequestBody TemplateManageRequest request);

    @PostMapping("/record")
    @Operation(summary = "保存")
    TemplateManageDTO save(@RequestBody TemplateManageRequest request);

    @DeleteMapping("/{id}/{categoryId}")
    @Operation(summary = "删除")
    void delete(@PathVariable String id,@PathVariable String categoryId);

    @DeleteMapping("/categories/{id}")
    @Operation(summary = "删除分类")
    String deleteCategory(@PathVariable String id);

    @GetMapping("/detail/{templateId}")
    @Operation(summary = "明细查询")
    VisualizationTemplateVO findOne(@PathVariable String templateId) throws Exception;

    @PostMapping("/categories/by-template-ids")
    @Operation(summary = "明细查询")
    List<String> findCategoriesByTemplateIds(@RequestBody TemplateManageRequest request) throws Exception;


    @PostMapping("/list")
    @Operation(summary = "查询")
    List<TemplateManageDTO> find(@RequestBody TemplateManageRequest request);

    @PostMapping("/categories")
    @Operation(summary = "分类明细查询")
    List<TemplateManageDTO> findCategories(@RequestBody TemplateManageRequest request);

    @PostMapping("/name-check")
    @Operation(summary = "模版名称校验")
    String nameCheck(@RequestBody TemplateManageRequest request);

    @PostMapping("/category-template-name-check")
    @Operation(summary = "分类名称校验")
    String categoryTemplateNameCheck(@RequestBody TemplateManageRequest request);

    @PostMapping("/category-template-names-check")
    @Operation(summary = "分类名称批量校验")
    String checkCategoryTemplateBatchNames(@RequestBody TemplateManageRequest request);

    @PostMapping("/batch")
    @Operation(summary = "批量更新")
    void batchUpdate(@RequestBody TemplateManageBatchRequest request);

    @DeleteMapping("/batch")
    @Operation(summary = "批量删除")
    void batchDelete(@RequestBody TemplateManageBatchRequest request);

}
