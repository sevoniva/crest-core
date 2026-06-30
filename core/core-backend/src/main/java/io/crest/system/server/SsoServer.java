package io.crest.system.server;

import io.crest.api.system.SsoApi;
import io.crest.api.system.request.SsoConfigRequest;
import io.crest.api.system.vo.SsoConfigVO;
import io.crest.api.system.vo.SsoStatusVO;
import io.crest.auth.vo.TokenVO;
import io.crest.constant.LogOT;
import io.crest.constant.LogST;
import io.crest.log.CrestAudit;
import io.crest.system.manage.SsoManage;
import io.crest.utils.CrestPermissionUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供单点登录状态、配置、登录回调和令牌兑换接口
 */
@RestController
@RequestMapping("/sso")
public class SsoServer implements SsoApi {

    @Resource
    private SsoManage ssoManage;

    /**
     * 查询单点登录启用状态
     */
    @Override
    public SsoStatusVO status() {
        return ssoManage.status();
    }

    /**
     * 查询单点登录配置
     */
    @Override
    public SsoConfigVO config(HttpServletRequest request) {
        CrestPermissionUtils.requireAdmin();
        return ssoManage.config(request);
    }

    /**
     * 保存单点登录配置
     */
    @Override
    @CrestAudit(ot = LogOT.MODIFY, st = LogST.DATA)
    public void save(SsoConfigRequest request) {
        CrestPermissionUtils.requireAdmin();
        ssoManage.save(request);
    }

    /**
     * 校验单点登录配置
     */
    @Override
    @CrestAudit(ot = LogOT.READ, st = LogST.DATA)
    public void validate(SsoConfigRequest request) {
        CrestPermissionUtils.requireAdmin();
        ssoManage.validate(request);
    }

    /**
     * 发起单点登录跳转
     */
    @Override
    @CrestAudit(ot = LogOT.LOGIN, st = LogST.USER)
    public void login(String redirect, HttpServletRequest request, HttpServletResponse response) {
        ssoManage.login(redirect, request, response);
    }

    /**
     * 处理单点登录回调
     */
    @Override
    @CrestAudit(ot = LogOT.LOGIN, st = LogST.USER)
    public void callback(String code, String state, String error, String errorDescription,
                         HttpServletRequest request, HttpServletResponse response) {
        ssoManage.callback(code, state, error, errorDescription, request, response);
    }

    /**
     * 使用登录票据兑换系统令牌
     */
    @Override
    @CrestAudit(ot = LogOT.LOGIN, st = LogST.USER)
    public TokenVO token(String ticket) {
        return ssoManage.token(ticket);
    }
}
