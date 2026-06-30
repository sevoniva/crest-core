package io.crest.api.permissions.variable.api;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.permissions.variable.dto.SysVariableDto;
import io.crest.api.permissions.variable.dto.SysVariableValueDto;
import io.crest.auth.CrestApiPath;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static io.crest.constant.AuthResourceEnum.SYSTEM;

@Hidden
@Tag(name = "系统变量")
@ApiSupport(order = 881, author = "Crest")
@CrestApiPath(value = "/sys-variable", rt = SYSTEM)
// 定义模块接口契约和数据传输结构
public interface SysVariablesApi {

    @Operation(summary = "创建变量")
    @PostMapping
    SysVariableDto create(@RequestBody SysVariableDto sysVariableDto);

    @Operation(summary = "编辑变量")
    @PutMapping
    SysVariableDto edit(@RequestBody SysVariableDto sysVariableDto);

    @Operation(summary = "删除变量")
    @DeleteMapping("/{id}")
    void delete(@PathVariable("id") Long id);

    @Operation(summary = "变量详细信息")
    @GetMapping("/detail/{id}")
    SysVariableDto detail(@PathVariable("id") Long id);

    @Operation(summary = "系统变量列表")
    @PostMapping("/list")
    List<SysVariableDto> query(@RequestBody SysVariableDto sysVariableDto);

    @Operation(summary = "创建变量值")
    @PostMapping("/value")
    SysVariableValueDto createValue(@RequestBody SysVariableValueDto sysVariableDto);

    @Operation(summary = "编辑变量值")
    @PutMapping("/value")
    SysVariableValueDto editValue(@RequestBody SysVariableValueDto sysVariableDto);

    @Operation(summary = "删除变量值")
    @DeleteMapping("/value/{id}")
    void deleteValue(@PathVariable("id") String id);

    @Operation(summary = "查看变量值详细信息")
    @GetMapping("/value/selected/{id}")
    List<SysVariableValueDto> selectVariableValue(@PathVariable("id") Long id);

    @Operation(summary = "系统变量值列表")
    @PostMapping("/value/selected/{goPage}/{pageSize}")
    IPage<SysVariableValueDto> selectPage(@PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize, @RequestBody SysVariableValueDto sysVariableValueDto);

    @Operation(summary = "批量删除变量值")
    @DeleteMapping("/value/batch")
    void batchDel(@RequestBody List<Long> ids);

    @Hidden
    Map<Long, Map<String, String>> queryBatchSysVariable(List<Long> uids);

}
