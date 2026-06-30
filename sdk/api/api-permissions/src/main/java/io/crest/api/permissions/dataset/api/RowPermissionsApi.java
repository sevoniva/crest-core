package io.crest.api.permissions.dataset.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.crest.api.permissions.dataset.dto.*;
import io.crest.api.permissions.user.vo.UserFormVO;
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

/**
 * 定义数据集行权限的查询、维护和授权对象接口
 */
@Hidden
@Tag(name = "行权限")
@CrestApiPath(value = "/dataset/row-permissions", rt = DATASET)
public interface RowPermissionsApi {

    /**
     * 分页查询指定数据集的行权限规则
     */
    @Operation(summary = "查询行权限列表")
    @GetMapping("/page/{datasetId}/{goPage}/{pageSize}")
    public IPage<DataSetRowPermissionsTreeDTO> rowPermissions(@PathVariable("datasetId") Long datasetId, @PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize);

    /**
     * 保存数据集行权限规则
     */
    @Operation(summary = "保存")
    @PostMapping("record")
    public void save(@RequestBody DataSetRowPermissionsTreeDTO datasetRowPermissions);

    /**
     * 删除数据集行权限规则
     */
    @Operation(summary = "删除")
    @DeleteMapping
    public void delete(@RequestBody DataSetRowPermissionsTreeDTO datasetRowPermissions);

    /**
     * 查询可授权的用户、角色或组织对象
     */
    @Operation(summary = "授权对象")
    @GetMapping("/authorized-objects/{datasetId}/{type}")
    public List<Item> authObjs(@PathVariable("datasetId") Long datasetId, @PathVariable("type") String type);

    /**
     * 查询行权限规则详情
     */
    @Operation(summary = "获取详细信息")
    @PostMapping("/row-permission-info")
    public DataSetRowPermissionsTreeDTO dataSetRowPermissionInfo(@RequestBody DataSetRowPermissionsTreeDTO request);

    /**
     * 查询行权限白名单用户
     */
    @Operation(summary = "白名单")
    @PostMapping("/allowlist-users")
    public List<UserFormVO> whiteListUsers(@RequestBody WhiteListUsersRequest request);

    /**
     * 根据用户标识查询授权用户信息
     */
    public UserFormVO getUserById(Long id);

    /**
     * 查询符合条件的行权限规则列表
     */
    public List<DataSetRowPermissionsTreeDTO> list(DatasetRowPermissionsTreeRequest dataSetRowPermissionsTreeDTO) ;
}
