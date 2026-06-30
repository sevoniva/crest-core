package io.crest.api.permissions.auth.api;


import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.permissions.auth.dto.*;
import io.crest.api.permissions.auth.vo.PermissionValVO;
import io.crest.api.permissions.auth.vo.ResourceNodeVO;
import io.crest.model.BusiNodeRequest;
import io.crest.model.BusiNodeVO;
import io.crest.model.ExportTaskDTO;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "内部资源交互")
@ApiSupport(order = 998)
@Hidden
// 定义模块接口契约和数据传输结构
public interface InteractiveAuthApi {

    @Operation(summary = "查询菜单ID")
    @ApiOperationSupport(order = 1)
    @GetMapping("/menu-ids")
    List<Long> menuIds();


    @Operation(summary = "查询资源树")
    @ApiOperationSupport(order = 2)
    @PostMapping("/resource")
    List<BusiNodeVO> resource(@RequestBody BusiNodeRequest request);

    /**
     * 下面3个接口为内部调用接口不对外开放
     *
     * @param creator
     */

    @Operation(summary = "同步保存资源")
    @ApiOperationSupport(order = 3)
    @PostMapping("/resource")
    void saveResource(@RequestBody BusiResourceCreator creator);

    @Operation(summary = "同步更新资源")
    @ApiOperationSupport(order = 4)
    @PutMapping("/resource")
    void editResource(@RequestBody BusiResourceEditor editor);

    @Operation(summary = "同步删除资源")
    @ApiOperationSupport(order = 5)
    @DeleteMapping("/resource/{id}")
    void delResource(@PathVariable("id") Long id);

    @Operation(summary = "删除检测")
    @ApiOperationSupport(order = 6)
    @GetMapping("/resource/deletion-impact/{id}")
    boolean checkDel(@PathVariable("id") Long id);

    @Operation(summary = "移动资源")
    @ApiOperationSupport(order = 7)
    @PostMapping("/resource-move")
    void moveResource(@RequestBody BusiResourceMover mover);

    @Operation(summary = "权限校验")
    @ApiOperationSupport(order = 8)
    @PostMapping("/auth-check")
    void checkAuth(@RequestBody BusiPerCheckDTO checkDTO);

    @Operation(summary = "权限查询")
    @ApiOperationSupport(order = 9)
    @PostMapping("/authorization/{id}")
    PermissionValVO queryAuth(@PathVariable("id") Long id);

    @GetMapping("/root-path/{id}/{flag}/{logOT}")
    List<ResourceNodeVO> query2Root(@PathVariable("id") Long id, @PathVariable("flag") Integer flag, Integer logOT);

    @GetMapping("/empty-check")
    boolean checkEmpty();

    @GetMapping("/org-name-for-resource")
    String OrgNameForResource(ExportTaskDTO exportTaskDTO);

    void editResourceExtraFlag(BusiResourceEditor editor);

    @PostMapping("/batch-authorize")
    void batchAuthorize(@RequestBody BusiBatchAuthorizeRequest request);

    @Hidden
    @PostMapping("/revert")
    void revert();
}
