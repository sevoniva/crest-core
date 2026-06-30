package io.crest.api.permissions.user.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import io.crest.api.permissions.login.dto.MfaLoginDTO;
import io.crest.api.permissions.login.vo.MfaQrVO;
import io.crest.api.permissions.role.dto.UserRequest;
import io.crest.api.permissions.user.dto.*;
import io.crest.api.permissions.user.vo.*;
import io.crest.auth.CrestApiPath;
import io.crest.auth.CrestPermit;
import io.crest.auth.vo.TokenVO;
import io.crest.model.KeywordRequest;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static io.crest.constant.AuthResourceEnum.USER;


@Tag(name = "用户")
@ApiSupport(order = 888, author = "Crest")
@CrestApiPath(value = "/user", rt = USER)
// 定义模块接口契约和数据传输结构
public interface UserApi {

    @Operation(summary = "查询用户列表")
    @Parameters({
            @Parameter(name = "goPage", description = "目标页码", required = true, in = ParameterIn.PATH),
            @Parameter(name = "pageSize", description = "每页容量", required = true, in = ParameterIn.PATH),
            @Parameter(name = "request", description = "过滤条件", required = true)
    })
    @CrestPermit("m:read")
    @PostMapping("/page/{goPage}/{pageSize}")
    IPage<UserGridVO> pager(@PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize, @RequestBody UserGridRequest request);

    @Operation(summary = "查询用户详情")
    @Parameter(name = "id", description = "ID", required = true, in = ParameterIn.PATH)
    @CrestPermit({"m:read", "#p0 + ':read'"})
    @GetMapping("/detail/{id}")
    UserFormVO queryById(@PathVariable("id") Long id);


    @Operation(summary = "查询个人信息")
    @GetMapping("/person-info")
    UserFormVO personInfo();

    @Operation(summary = "查询用户系统变量信息")
    @GetMapping("/person-sys-variable-info/{id}")
    UserGridVO personSysVariableInfo(@PathVariable("id") Long id);

    @Operation(summary = "查询客户端IP信息")
    @GetMapping("/ip-info")
    CurIpVO ipInfo();

    @Operation(summary = "创建")
    @CrestPermit("m:read")
    @PostMapping
    Long create(@RequestBody UserCreator creator);

    @Operation(summary = "创建第三方用户")
    @CrestPermit("m:read")
    @PostMapping("/platform-record")
    void createPlatform(@RequestBody PlatformUserCreator creator);

    @Operation(summary = "编辑")
    @CrestPermit({"m:read", "#p0.id + ':manage'"})
    @PutMapping
    void edit(@RequestBody UserEditor editor);

    @Operation(summary = "变更个人信息")
    @PostMapping("/person-edit")
    void personEdit(@RequestBody UserEditor editor);

    @Operation(summary = "删除")
    @Parameter(name = "id", description = "ID", required = true, in = ParameterIn.PATH)
    @CrestPermit({"m:read", "#p0 + ':manage'"})
    @DeleteMapping("/{id}")
    void delete(@PathVariable("id") Long id);

    @Operation(summary = "批量删除")
    @CrestPermit({"m:read", "#p0 + ':manage'"})
    @DeleteMapping("/batch")
    void batchDel(@RequestBody List<Long> ids);

    @Operation(summary = "角色可绑用户")
    @PostMapping("/role/option")
    List<UserItemVO> optionForRole(@RequestBody UserRequest request);

    @Operation(summary = "组织内用户")
    @GetMapping("/org/option")
    List<UserItemVO> optionForOrg();

    @Operation(summary = "角色已绑用户")
    @Parameters({
            @Parameter(name = "goPage", description = "目标页码", required = true, in = ParameterIn.PATH),
            @Parameter(name = "pageSize", description = "每页容量", required = true, in = ParameterIn.PATH),
            @Parameter(name = "request", description = "过滤条件", required = true)
    })
    @PostMapping("/role/selected/{goPage}/{pageSize}")
    IPage<UserItemVO> selectedForRole(@PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize, @RequestBody UserRequest request);

    @Operation(summary = "切换组织")
    @Parameter(name = "oId", description = "目标组织ID", required = true, in = ParameterIn.PATH)
    @PostMapping("/switch/{oId}")
    TokenVO switchOrg(@PathVariable("oId") Long oId);

    @Operation(summary = "获取当前登录人信息")
    @GetMapping("/info")
    CurUserVO info();

    @Operation(summary = "查询当前组织内用户")
    @PostMapping("/by-current-org")
    List<UserItem> byCurOrg(@RequestBody KeywordRequest request);

    @Operation(summary = "用户数量", hidden = true)
    @Hidden
    @GetMapping("/user-count")
    int userCount();

    @Operation(summary = "切换语言")
    @PostMapping("/switch-language")
    void switchLanguage(@RequestBody LangSwitchRequest request);

    @Operation(summary = "下载批量导入模版")
    @PostMapping("/excel-template")
    void excelTemplate();

    @Operation(summary = "批量导入")
    @PostMapping("/batch-import")
    UserImportVO batchImport(@RequestPart(value = "file") MultipartFile file);


    @Operation(summary = "下载批量导入失败记录")
    @Parameter(name = "key", description = "导入结果key", required = true, in = ParameterIn.PATH)
    @GetMapping("/error-record/{key}")
    void errorRecord(@PathVariable("key") String key);

    @Operation(summary = "清理批量导入失败记录")
    @Parameter(name = "key", description = "导入结果key", required = true, in = ParameterIn.PATH)
    @GetMapping("/clear-error-record/{key}")
    void clearErrorRecord(@PathVariable("key") String key);

    @Operation(summary = "查询默认密码")
    @CrestPermit({"m:read"})
    @GetMapping("/default-password")
    String defaultPwd();

    @Operation(summary = "重置本地密码")
    @Parameter(name = "id", description = "用户ID", required = true, in = ParameterIn.PATH)
    @CrestPermit({"m:read", "#p0 + ':manage'"})
    @PostMapping("/reset-password/{id}")
    String resetPwd(@PathVariable("id") Long id);

    @Operation(summary = "切换用户状态")
    @CrestPermit({"m:read", "#p0.id + ':manage'"})
    @PostMapping("/enable")
    void enable(@RequestBody EnableSwitchRequest request);

    @Operation(summary = "修改个人密码")
    @PostMapping("/modify-password")
    void modifyPwd(@RequestBody ModifyPwdRequest request);

    @Hidden
    @GetMapping("/first-echelon/{limit}")
    List<Long> firstEchelon(@PathVariable("limit") Long limit);

    @Operation(summary = "根据账号查询用户")
    @GetMapping("/account/{account}")
    CurUserVO queryByAccount(@PathVariable("account") String account);

    @Hidden
    @PostMapping("/all")
    List<UserItem> allUser(@RequestBody KeywordRequest request);

    @Hidden
    @PostMapping("/admin/bind")
    void adminBind(@RequestBody AdminBindRequest request);

    @Hidden
    @PostMapping("/bind")
    void bind(@RequestBody UserBindRequest request);

    @Operation(summary = "解除绑定")
    @PostMapping("/unbind/{origin}")
    void unBind(@PathVariable("origin") Integer origin);

    @Operation(summary = "绑定状态")
    @GetMapping("/bind-status")
    List<Integer> bindStatus();

    @Hidden
    @GetMapping("/recipients")
    List<Map<String, Object>> recipients(@RequestBody UserReciRequest request);

    @Hidden
    @GetMapping("/org-admin")
    boolean orgAdmin();

    @Hidden
    @GetMapping("/default-org-admin")
    boolean defaultOrgAdmin();

    @Hidden
    @PostMapping("/sub-org-user")
    List<UserItem> subOrgUser(@RequestBody List<Long> oidList);

    List<Long> recipientsUserIds(UserReciRequest request);

    List<Long> getUserIdByAccount(String account);

    List<Long> getUserIdByName(String name);

    List<Map<String, Object>> listUserInfosByIds(List<Long> ids);

    @Operation(summary = "MFA二维码信息")
    @GetMapping("/mfa-qr")
    MfaQrVO mfaQr();

    @Operation(summary = "MFA绑定状态")
    @GetMapping("/mfabound")
    Boolean mfaBound();

    @Operation(summary = "绑定MFA")
    @PostMapping("/mfa-bind")
    void mfaBind(@RequestBody MfaLoginDTO dto);

    @Operation(summary = "解绑MFA")
    @PostMapping("/mfa-unbind/{code}")
    String mfaUnbind(@PathVariable("code") String code);

    @Operation(summary = "重置MFA绑定状态")
    @PostMapping("/mfa-reset/{id}")
    void resetBind(@PathVariable("id") Long id);

    @Hidden
    @GetMapping("/lang")
    String userLang();


    @Hidden
    List<UserReciVO> getFormatRecipient(Long oid, List<Long> uidList, List<Long> ridList);

}
