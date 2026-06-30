package io.crest.api.permissions.dataset.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.crest.api.permissions.dataset.dto.DataSetColumnPermissionsDTO;
import io.crest.auth.CrestApiPath;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static io.crest.constant.AuthResourceEnum.DATASET;

@Hidden
@Tag(name = "列权限")
@CrestApiPath(value = "/dataset/column-permissions", rt = DATASET)
/**
 * 数据集列权限接口定义
 */
public interface ColumnPermissionsApi {

    /**
     * 分页查询指定数据集的列权限配置
     */
    @Operation(summary = "查询列权限列表")
    @GetMapping("/page/{datasetId}/{goPage}/{pageSize}")
    public IPage<DataSetColumnPermissionsDTO> columnPermissions(@PathVariable("datasetId") Long datasetId, @PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize);

    /**
     * 保存列权限配置
     */
    @Operation(summary = "保存")
    @PostMapping("record")
    public void save(@RequestBody DataSetColumnPermissionsDTO dataSetColumnPermissionsDTO);

    /**
     * 删除列权限配置
     */
    @Operation(summary = "删除")
    @DeleteMapping
    public void delete(@RequestBody DataSetColumnPermissionsDTO dataSetColumnPermissionsDTO);

    /**
     * 查询列权限配置详情
     */
    @Operation(summary = "获取详细信息")
    @PostMapping("/info")
    public DataSetColumnPermissionsDTO DataSetColumnPermissionInfo(@RequestBody DataSetColumnPermissionsDTO request);

    /**
     * 按条件查询列权限配置列表
     */
    public List<DataSetColumnPermissionsDTO> list(@RequestBody DataSetColumnPermissionsDTO request);

}
