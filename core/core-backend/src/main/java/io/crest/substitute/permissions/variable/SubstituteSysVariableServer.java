package io.crest.substitute.permissions.variable;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.crest.api.permissions.variable.api.SysVariablesApi;
import io.crest.api.permissions.variable.dto.SysVariableDto;
import io.crest.api.permissions.variable.dto.SysVariableValueDto;
import io.crest.exception.CrestException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController("sysVariableServer")
@RequestMapping("/sys-variable")
// 提供系统变量接口的兼容实现
public class SubstituteSysVariableServer implements SysVariablesApi {

    // 拒绝创建系统变量
    @Override
    @PostMapping
    public SysVariableDto create(@RequestBody SysVariableDto sysVariableDto) {
        throwUnsupported();
        return sysVariableDto;
    }

    // 拒绝编辑系统变量
    @Override
    @PutMapping
    public SysVariableDto edit(@RequestBody SysVariableDto sysVariableDto) {
        throwUnsupported();
        return sysVariableDto;
    }

    // 拒绝删除系统变量
    @Override
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        throwUnsupported();
    }

    // 拒绝查询系统变量详情
    @Override
    @GetMapping("/detail/{id}")
    public SysVariableDto detail(@PathVariable("id") Long id) {
        throwUnsupported();
        return null;
    }

    // 返回空系统变量列表
    @Override
    @PostMapping("/list")
    public List<SysVariableDto> query(@RequestBody(required = false) SysVariableDto sysVariableDto) {
        return Collections.emptyList();
    }

    // 拒绝创建系统变量值
    @Override
    @PostMapping("/value")
    public SysVariableValueDto createValue(@RequestBody SysVariableValueDto sysVariableDto) {
        throwUnsupported();
        return sysVariableDto;
    }

    // 拒绝编辑系统变量值
    @Override
    @PutMapping("/value")
    public SysVariableValueDto editValue(@RequestBody SysVariableValueDto sysVariableDto) {
        throwUnsupported();
        return sysVariableDto;
    }

    // 拒绝删除系统变量值
    @Override
    @DeleteMapping("/value/{id}")
    public void deleteValue(@PathVariable("id") String id) {
        throwUnsupported();
    }

    // 返回空系统变量值列表
    @Override
    @GetMapping("/value/selected/{id}")
    public List<SysVariableValueDto> selectVariableValue(@PathVariable("id") Long id) {
        return Collections.emptyList();
    }

    // 返回空系统变量值分页
    @Override
    @PostMapping("/value/selected/{goPage}/{pageSize}")
    public IPage<SysVariableValueDto> selectPage(@PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize, @RequestBody(required = false) SysVariableValueDto sysVariableValueDto) {
        return new Page<SysVariableValueDto>(goPage, pageSize).setRecords(Collections.emptyList());
    }

    // 拒绝批量删除系统变量值
    @Override
    @DeleteMapping("/value/batch")
    public void batchDel(@RequestBody List<Long> ids) {
        throwUnsupported();
    }

    // 返回空用户系统变量映射
    @Override
    public Map<Long, Map<String, String>> queryBatchSysVariable(List<Long> uids) {
        return Collections.emptyMap();
    }

    // 抛出当前版本不支持系统变量管理的异常
    private void throwUnsupported() {
        CrestException.throwException("当前版本不支持自定义系统变量管理");
    }
}
