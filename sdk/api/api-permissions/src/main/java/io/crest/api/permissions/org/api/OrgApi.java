package io.crest.api.permissions.org.api;

import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.permissions.org.dto.OrgCreator;
import io.crest.api.permissions.org.dto.OrgEditor;
import io.crest.api.permissions.org.dto.OrgLazyRequest;
import io.crest.api.permissions.org.dto.OrgRequest;
import io.crest.api.permissions.org.vo.*;
import io.crest.auth.CrestApiPath;
import io.crest.auth.CrestPermit;
import io.crest.model.KeywordRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static io.crest.constant.AuthResourceEnum.ORG;

@Tag(name = "组织")
@ApiSupport(order = 886, author = "Crest")
@CrestApiPath(value = "/org", rt = ORG)
// 定义模块接口契约和数据传输结构
public interface OrgApi {

    @Operation(summary = "查询组织树")
    @PostMapping("/page/tree")
    @CrestPermit("m:read")
    List<OrgPageVO> pageTree(@RequestBody OrgRequest request);

    @Operation(summary = "懒加载组织树")
    @PostMapping("/page/lazy-tree")
    @CrestPermit("m:read")
    LazyTreeVO lazyPageTree(@RequestBody OrgLazyRequest request);

    @Operation(summary = "创建")
    @CrestPermit({"m:read"})
    @PostMapping("/page")
    Long create(@RequestBody OrgCreator creator);

    @Operation(summary = "编辑")
    @CrestPermit({"m:read", "#p0.id+':manage'"})
    @PutMapping("/page")
    void edit(@RequestBody OrgEditor editor);

    @Operation(summary = "删除")
    @Parameter(name = "id", description = "ID", required = true, in = ParameterIn.PATH)
    @DeleteMapping("/page/{id}")
    @CrestPermit({"m:read", "#p0+':manage'"})
    void delete(@PathVariable("id") Long id);

    @Operation(summary = "查询权限内组织树")
    @PostMapping("/mounted")
    List<MountedVO> mounted(@RequestBody KeywordRequest request);

    @Operation(summary = "查询权限内组织树(懒加载)")
    @PostMapping("/lazy-mounted")
    LazyMountedVO lazyMounted(@RequestBody OrgLazyRequest request);

    @Operation(summary = "", hidden = true)
    @GetMapping("/resource-exists/{oid}")
    boolean resourceExist(@PathVariable("oid") Long oid);

    @Operation(hidden = true)
    @GetMapping("/detail/{oid}")
    OrgDetailVO detail(@PathVariable("oid") Long oid);

    @Operation(hidden = true)
    @GetMapping("/sub-orgs")
    List<String> subOrgs();
}
