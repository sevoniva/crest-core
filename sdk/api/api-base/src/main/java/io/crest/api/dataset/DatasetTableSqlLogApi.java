package io.crest.api.dataset;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.dataset.dto.SqlLogDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "数据集管理:SQL日志")
@ApiSupport(order = 977)
// 定义模块接口契约和数据传输结构
public interface DatasetTableSqlLogApi {
    @Operation(summary = "保存")
    @PostMapping("record")
    void save(@RequestBody SqlLogDTO sqlLogDTO) throws Exception;

    @Operation(summary = "查看SQL片段执行记录")
    @PostMapping("by-table")
    List<SqlLogDTO> listByTableId(@RequestBody SqlLogDTO sqlLogDTO) throws Exception;

    @Operation(summary = "删除日志")
    @DeleteMapping("/tables/{id}")
    void tableRemoval(@PathVariable String id) throws Exception;
}
