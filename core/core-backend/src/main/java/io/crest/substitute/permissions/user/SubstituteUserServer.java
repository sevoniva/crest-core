package io.crest.substitute.permissions.user;


import com.baomidou.mybatisplus.core.metadata.IPage;
import io.crest.api.permissions.user.dto.LangSwitchRequest;
import io.crest.api.permissions.user.dto.ModifyPwdRequest;
import io.crest.api.permissions.user.dto.UserCreator;
import io.crest.api.permissions.user.dto.UserEditor;
import io.crest.api.permissions.user.dto.UserGridRequest;
import io.crest.api.permissions.user.dto.EnableSwitchRequest;
import io.crest.api.permissions.user.vo.CurIpVO;
import io.crest.api.permissions.user.vo.CurUserVO;
import io.crest.api.permissions.user.vo.UserFormVO;
import io.crest.api.permissions.user.vo.UserGridVO;
import io.crest.api.permissions.user.vo.UserImportVO;
import io.crest.api.permissions.user.vo.UserItem;
import io.crest.auth.bo.TokenUserBO;
import io.crest.constant.LogOT;
import io.crest.constant.LogST;
import io.crest.exception.CrestException;
import io.crest.i18n.Lang;
import io.crest.log.CrestAudit;
import io.crest.result.ResultCode;
import io.crest.substitute.permissions.user.model.CrestUser;
import io.crest.substitute.permissions.user.model.UserImportRow;
import io.crest.utils.AuthUtils;
import io.crest.utils.CacheUtils;
import io.crest.utils.CommonExcelUtils;
import io.crest.utils.CrestPermissionUtils;
import io.crest.utils.IPUtils;
import io.crest.utils.RsaUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.crest.constant.CacheConstant.UserCacheConstant.USER_COMMUNITY_LANGUAGE;

@Component
@ConditionalOnMissingBean(name = "userServer")
@RestController
@RequestMapping("/user")
// 内置用户 REST 控制器，提供当前用户、用户管理、导入导出和语言切换接口
public class SubstituteUserServer {

    @Resource
    private CrestUserManage crestUserManage;

    // 查询当前登录用户基础上下文
    @CrestAudit(ot = LogOT.READ, st = LogST.USER)
    @GetMapping("/info")
    public CurUserVO info() {
        Long uid = currentUserId();
        CrestUser user = crestUserManage.queryById(uid);
        if (user == null) {
            CrestException.throwException(ResultCode.USER_NOT_EXIST.code(), ResultCode.USER_NOT_EXIST.message());
        }
        CurUserVO result = crestUserManage.toCurrent(user);
        Object langObj = CacheUtils.get(USER_COMMUNITY_LANGUAGE, "de");
        if (ObjectUtils.isNotEmpty(langObj) && StringUtils.isNotBlank(langObj.toString())) {
            result.setLanguage(langObj.toString());
        }
        return result;
    }

    // 查询当前用户个人资料
    @CrestAudit(ot = LogOT.READ, st = LogST.USER)
    @GetMapping("/person-info")
    public UserFormVO personInfo() {
        Long uid = currentUserId();
        UserFormVO userFormVO = crestUserManage.toForm(crestUserManage.queryById(uid));
        userFormVO.setIp(IPUtils.get());
        return userFormVO;
    }

    // 查询当前请求 IP，并返回默认管理员展示信息
    @GetMapping("/ip-info")
    public CurIpVO ipInfo() {
        CurIpVO curIpVO = new CurIpVO();
        curIpVO.setAccount("admin");
        curIpVO.setName("管理员");
        curIpVO.setIp(IPUtils.get());
        return curIpVO;
    }

    // 分页查询用户列表，仅管理员可访问
    @CrestAudit(ot = LogOT.READ, st = LogST.USER)
    @PostMapping("/page/{goPage}/{pageSize}")
    public IPage<UserGridVO> pager(@PathVariable("goPage") int goPage, @PathVariable("pageSize") int pageSize, @RequestBody UserGridRequest request) {
        CrestPermissionUtils.requireAdmin();
        return crestUserManage.pager(goPage, pageSize, request);
    }

    // 查询指定用户详情
    @CrestAudit(ot = LogOT.READ, st = LogST.USER, id = "#p0")
    @GetMapping("/detail/{id}")
    public UserFormVO queryById(@PathVariable("id") Long id) {
        CrestPermissionUtils.requireAdmin();
        return crestUserManage.toForm(crestUserManage.queryById(id));
    }

    // 创建本地用户
    @CrestAudit(ot = LogOT.CREATE, st = LogST.USER)
    @PostMapping
    public Long create(@RequestBody UserCreator creator) {
        CrestPermissionUtils.requireAdmin();
        return crestUserManage.create(creator);
    }

    // 编辑本地用户
    @CrestAudit(ot = LogOT.MODIFY, st = LogST.USER, id = "#p0.id")
    @PutMapping
    public void edit(@RequestBody UserEditor editor) {
        CrestPermissionUtils.requireAdmin();
        crestUserManage.edit(editor);
    }

    // 删除本地用户
    @CrestAudit(ot = LogOT.DELETE, st = LogST.USER, id = "#p0")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable("id") Long id) {
        CrestPermissionUtils.requireAdmin();
        crestUserManage.delete(id);
    }

    // 切换用户启用状态
    @CrestAudit(ot = LogOT.MODIFY, st = LogST.USER, id = "#p0.id")
    @PostMapping("/enable")
    public void enable(@RequestBody EnableSwitchRequest request) {
        CrestPermissionUtils.requireAdmin();
        crestUserManage.enable(request);
    }

    // 查询系统默认初始密码
    @GetMapping("/default-password")
    public String defaultPwd() {
        CrestPermissionUtils.requireAdmin();
        return crestUserManage.defaultPwd();
    }

    // 重置指定用户密码
    @CrestAudit(ot = LogOT.MODIFY, st = LogST.USER, id = "#p0")
    @PostMapping("/reset-password/{id}")
    public String resetPwd(@PathVariable("id") Long id) {
        CrestPermissionUtils.requireAdmin();
        return crestUserManage.resetPwd(id);
    }

    // 修改当前用户或指定用户密码
    @CrestAudit(ot = LogOT.MODIFY, st = LogST.USER)
    @PostMapping("/modify-password")
    public void modifyPwd(@RequestBody ModifyPwdRequest request) {
        Long uid = ObjectUtils.isEmpty(request.getUid()) ? AuthUtils.getUser().getUserId() : request.getUid();
        crestUserManage.modifyPwd(uid, RsaUtils.decryptStr(request.getPwd()), RsaUtils.decryptStr(request.getNewPwd()));
    }

    // 按账号查询用户上下文
    @CrestAudit(ot = LogOT.READ, st = LogST.USER)
    @GetMapping("/account/{account}")
    public CurUserVO queryByAccount(@PathVariable("account") String account) {
        return crestUserManage.toCurrent(crestUserManage.queryByAccount(account));
    }

    // 查询当前组织下可选用户
    @PostMapping("/by-current-org")
    public List<UserItem> byCurOrg(@RequestBody(required = false) UserGridRequest request) {
        return crestUserManage.usersByCurrentOrg(request == null ? null : request.getKeyword());
    }

    // 批量导入用户
    @PostMapping("/batch-import")
    @CrestAudit(ot = LogOT.UPLOADFILE, st = LogST.USER)
    public UserImportVO batchImport(@RequestParam("file") MultipartFile file) throws Exception {
        CrestPermissionUtils.requireAdmin();
        return crestUserManage.importUsers(file);
    }

    // 批量删除用户
    @DeleteMapping("/batch")
    @CrestAudit(ot = LogOT.DELETE, st = LogST.USER)
    public void batchDel(@RequestBody List<Long> ids) {
        CrestPermissionUtils.requireAdmin();
        if (ids != null) {
            ids.forEach(crestUserManage::delete);
        }
    }

    // 下载用户导入模板
    @PostMapping("/excel-template")
    @CrestAudit(ot = LogOT.DOWNLOAD, st = LogST.USER)
    public void excelTemplate(HttpServletResponse response) throws Exception {
        CrestPermissionUtils.requireAdmin();
        UserImportRow row = new UserImportRow();
        row.setAccount("user01");
        row.setName("张三");
        row.setEmail("user01@example.com");
        row.setPhone("13800000000");
        CommonExcelUtils.writeExcel(response, List.of(row), UserImportRow.class, List.of(), "user-import-template", "用户导入模板");
    }

    // 下载导入失败记录说明
    @GetMapping("/error-record/{key}")
    public void errorRecord(@PathVariable("key") String key, HttpServletResponse response) throws Exception {
        response.setContentType("text/plain;charset=UTF-8");
        response.getOutputStream().write("导入失败记录请根据返回数量检查源文件。".getBytes(StandardCharsets.UTF_8));
    }

    // 清理导入失败记录占位接口
    @GetMapping("/clear-error-record/{key}")
    public void clearErrorRecord(@PathVariable("key") String key) {
    }

    // 切换当前用户界面语言
    @CrestAudit(ot = LogOT.MODIFY, st = LogST.USER)
    @PostMapping("/switch-language")
    public void switchLanguage(@RequestBody LangSwitchRequest request) {
        String lang = request.getLang();
        if (Strings.CI.equals(Lang.zh_CN.getDesc(), lang)) {
            lang = Lang.zh_CN.getDesc();
        } else if (Strings.CI.equalsAny(lang, "en", "tw")) {
            lang = lang.toLowerCase();
        } else {
            CrestException.throwException("无效language");
        }
        CacheUtils.put(USER_COMMUNITY_LANGUAGE, "de", lang);
    }

    // 获取当前登录用户 ID，未登录时抛出标准未登录错误
    private Long currentUserId() {
        TokenUserBO user = AuthUtils.getUser();
        if (user == null || user.getUserId() == null) {
            CrestException.throwException(ResultCode.USER_NOT_LOGGED_IN.code(), ResultCode.USER_NOT_LOGGED_IN.message());
        }
        return user.getUserId();
    }
}
