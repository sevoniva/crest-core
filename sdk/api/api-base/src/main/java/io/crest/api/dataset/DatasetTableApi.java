package io.crest.api.dataset;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.dataset.dto.MultFieldValuesRequest;
import io.crest.api.dataset.engine.SQLFunctionDTO;
import io.crest.extensions.datasource.dto.DatasetTableFieldDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "数据集管理:表")
@ApiSupport(order = 977)
// 定义模块接口契约和数据传输结构
public interface DatasetTableApi {

    /**
     * 该接口用于图表计算字段单独保存
     *
     * @param datasetTableFieldDTO
     * @return
     * @throws Exception
     */
    @Operation(summary = "保存字段")
    @PostMapping("record")
    DatasetTableFieldDTO save(@RequestBody DatasetTableFieldDTO datasetTableFieldDTO) throws Exception;

    @Operation(summary = "查询字段")
    @PostMapping("detail/{id}")
    DatasetTableFieldDTO get(@PathVariable Long id);

    @Operation(summary = "获取数据集字段")
    @PostMapping("by-dataset-group/{id}")
    List<DatasetTableFieldDTO> listByDatasetGroup(@PathVariable Long id);

    @Operation(summary = "获取数据集字段map")
    @PostMapping("by-dataset-ids")
    Map<String, List<DatasetTableFieldDTO>> listByDsIds(@RequestBody List<Long> ids);

    @Operation(summary = "删除字段")
    @DeleteMapping("/{id}")
    void delete(@PathVariable Long id);

    @Operation(summary = "获取字段分组")
    @PostMapping("by-dataset-query/{id}")
    Map<String, List<DatasetTableFieldDTO>> listByDQ(@PathVariable Long id);

    @Operation(summary = "获取copilot字段分组")
    @PostMapping("copilot-fields/{id}")
    Map<String, List<DatasetTableFieldDTO>> copilotFields(@PathVariable Long id) throws Exception;

    @Operation(summary = "获取字段")
    @GetMapping("with-permissions/{id}")
    List<DatasetTableFieldDTO> listFieldsWithPermissions(@PathVariable Long id);

    @Operation(summary = "获取枚举值")
    @PostMapping("permission-field-values")
    List<String> multFieldValuesForPermissions(@RequestBody MultFieldValuesRequest multFieldValuesRequest) throws Exception;

    @Operation(summary = "获取计算字段函数")
    @PostMapping("functions")
    List<SQLFunctionDTO> functions();

    @Operation(summary = "删除图表计算字段", hidden = true)
    @DeleteMapping("charts/{id}")
    void deleteChartFields(@PathVariable Long id);
}
