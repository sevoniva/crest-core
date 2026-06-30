package io.crest.api.permissions.apikey.api;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.permissions.apikey.dto.ApikeyEnableEditor;
import io.crest.api.permissions.apikey.vo.ApiKeyVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "API Key")
@ApiSupport(order = 884, author = "Crest")
// 定义模块接口契约和数据传输结构
public interface ApiKeyApi {

    @Operation(summary = "生成")
    @ApiOperationSupport(order = 1)
    @PostMapping("/generate")
    void generate();

    @Operation(summary = "查询")
    @ApiOperationSupport(order = 2)
    @GetMapping("/list")
    List<ApiKeyVO> query();

    @Operation(summary = "切换状态")
    @ApiOperationSupport(order = 3)
    @PostMapping("/switch")
    void switchEnable(@RequestBody ApikeyEnableEditor editor);

    @Operation(summary = "删除")
    @ApiOperationSupport(order = 4)
    @Parameter(name = "id", description = "ID", required = true, in = ParameterIn.PATH)
    @DeleteMapping("/{id}")
    void delete(@PathVariable("id") Long id);
}
