package io.crest.api.permissions.embedded.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.permissions.embedded.dto.EmbeddedCreator;
import io.crest.api.permissions.embedded.dto.EmbeddedEditor;
import io.crest.api.permissions.embedded.dto.EmbeddedOrigin;
import io.crest.api.permissions.embedded.dto.EmbeddedResetRequest;
import io.crest.api.permissions.embedded.vo.EmbeddedGridVO;
import io.crest.model.KeywordRequest;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "嵌入式")
@ApiSupport(order = 883, author = "Crest")
// 定义模块接口契约和数据传输结构
public interface EmbeddedApi {

    @Operation(summary = "查询")
    @ApiOperationSupport(order = 1)
    @PostMapping("/page/{goPage}/{pageSize}")
    IPage<EmbeddedGridVO> queryGrid(@PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize, @RequestBody KeywordRequest request);

    @Operation(summary = "创建")
    @ApiOperationSupport(order = 2)
    @PostMapping
    void create(@RequestBody EmbeddedCreator creator);

    @Operation(summary = "编辑")
    @ApiOperationSupport(order = 3)
    @PutMapping
    void edit(@RequestBody EmbeddedEditor editor);

    @Operation(summary = "删除")
    @ApiOperationSupport(order = 4)
    @Parameter(name = "id", description = "ID", required = true, in = ParameterIn.PATH)
    @DeleteMapping("/{id}")
    void delete(@PathVariable("id") Long id);

    @Operation(summary = "批量删除")
    @ApiOperationSupport(order = 4)
    @DeleteMapping("/batch")
    void batchDelete(@RequestBody List<Long> ids);

    @ApiOperationSupport(order = 5)
    @Operation(summary = "重置密钥")
    @PostMapping("/reset")
    void reset(@RequestBody EmbeddedResetRequest request);

    @ApiOperationSupport(order = 6)
    @Operation(summary = "嵌入式应用域名集合", hidden = true)
    @GetMapping("/domains")
    List<String> domainList();

    @Hidden
    @PostMapping("/iframe")
    void initIframe(@RequestBody EmbeddedOrigin origin);

    @ApiOperationSupport(order = 7)
    @Operation(summary = "获取Token参数")
    @GetMapping("/token-args")
    Map<String, Object> getTokenArgs();

    @Hidden
    @GetMapping("/limit-count")
    int getLimitCount();

    @ApiOperationSupport(order = 8)
    @Operation(summary = "注销Token")
    @PostMapping("/logout")
    void logout();
}
