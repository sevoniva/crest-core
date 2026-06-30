package io.crest.auth.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.exceptions.JWTVerificationException;
import io.crest.auth.bo.TokenUserBO;
import io.crest.constant.AuthConstant;
import io.crest.exception.CrestException;
import io.crest.result.ResultCode;
import io.crest.result.ResultMessage;
import io.crest.utils.*;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@SuppressWarnings("deprecation")
// 定义过滤条件的数据结构和匹配信息
public class TokenFilter implements Filter {
    private static final String headName = "CREST-GATEWAY-FLAG";

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String method = request.getMethod();
        if (!Strings.CS.equalsAny(method, "GET", "POST", "PUT", "OPTIONS", "DELETE")) {
            HttpServletResponse res = (HttpServletResponse) servletResponse;
            res.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        if (Strings.CI.equals("OPTIONS", method)) {
            String origin = request.getHeader("Origin");
            if (StringUtils.isBlank(origin)) {
                HttpServletResponse res = (HttpServletResponse) servletResponse;
                res.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        String requestURI = request.getRequestURI();
        if (Boolean.TRUE.equals(request.getAttribute(AuthConstant.PROMETHEUS_AUTHENTICATED_ATTR))) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        boolean match = false;
        try {
            match = WhitelistUtils.match(requestURI);
        } catch (CrestException e) {
            HttpServletResponse res = (HttpServletResponse) servletResponse;
            ResultMessage resultMessage = new ResultMessage(e.getCode(), e.getMessage());
            ResponseEntity<ResultMessage> entity = new ResponseEntity<>(resultMessage, HttpStatus.UNAUTHORIZED);
            sendResponseEntity(res, entity);
            LogUtil.error(e.getMessage(), e);
            return;
        }
        if (match) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        try {
            if (ModelUtils.isDesktop()) {
                UserUtils.setDesktopUser();
                filterChain.doFilter(servletRequest, servletResponse);
                return;
            }
            String executeVersion = null;
            if (StringUtils.isNotBlank(executeVersion = VersionUtil.getRandomVersion())) {
                Objects.requireNonNull(ServletUtils.response()).addHeader(AuthConstant.CREST_EXECUTE_VERSION, executeVersion);
            }
            String linkToken = ServletUtils.getHead(AuthConstant.LINK_TOKEN_KEY);
            if (StringUtils.isNotBlank(linkToken)) {
                if (StringUtils.length(linkToken) < 100) {
                    CrestException.throwException("token is invalid");
                }
                DecodedJWT unverifiedJwt = SignedTokenUtils.decodeUnverifiedForSecretLookup(linkToken);
                Long userId = unverifiedJwt.getClaim("uid").asLong();
                Long resourceId = unverifiedJwt.getClaim("resourceId").asLong();
                if (ObjectUtils.isEmpty(userId)) {
                    CrestException.throwException("link token格式错误！");
                }
                if (ObjectUtils.isEmpty(resourceId)) {
                    CrestException.throwException("link token格式错误！");
                }

                Object shareSecretManage = CommonBeanFactory.getBean("shareSecretManage");
                Method getSecretMethod = CrestReflectionUtils.findMethod(shareSecretManage.getClass(), "getSecret");
                Object pwdObj = ReflectionUtils.invokeMethod(getSecretMethod, shareSecretManage, resourceId, userId);
                String linkSecret = pwdObj.toString();

                DecodedJWT verifiedJwt = SignedTokenUtils.verify(linkToken, linkSecret);
                Long oid = verifiedJwt.getClaim("oid").asLong();
                resourceId = verifiedJwt.getClaim("resourceId").asLong();

                request.setAttribute(AuthConstant.LINK_RESOURCE_ID_ATTR, resourceId);
                UserUtils.setUserInfo(new TokenUserBO(userId, oid));
                filterChain.doFilter(servletRequest, servletResponse);
                return;
            }
            String token = ServletUtils.getToken();
            TokenUserBO userBO = TokenUtils.validate(token);
            UserUtils.setUserInfo(userBO);
            filterChain.doFilter(servletRequest, servletResponse);
        } catch (CrestException | JWTVerificationException e) {
            HttpServletResponse res = (HttpServletResponse) servletResponse;
            String message = "登录已失效，请重新登录";
            ResultMessage resultMessage = new ResultMessage(ResultCode.USER_NOT_LOGGED_IN.code(), message);
            ResponseEntity<ResultMessage> entity = new ResponseEntity<>(resultMessage, HttpStatus.UNAUTHORIZED);
            res.addHeader(headName, URLEncoder.encode(message, StandardCharsets.UTF_8));
            sendResponseEntity(res, entity);
            LogUtil.debug(message + ": " + requestURI);
        } finally {
            UserUtils.removeUser();
        }
    }

    private void sendResponseEntity(HttpServletResponse httpResponse, ResponseEntity<ResultMessage> responseEntity) throws IOException {
        HttpStatusCode statusCode = responseEntity.getStatusCode();
        httpResponse.setStatus(statusCode.value());
        httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        httpResponse.setContentType("application/json;charset=UTF-8");
        HttpHeaders headers = responseEntity.getHeaders();
        if (ObjectUtils.isNotEmpty(headers)) {
            headers.forEach((key, value) -> httpResponse.addHeader(key, value.toString()));
        }
        httpResponse.getWriter().write(Objects.requireNonNull(JsonUtil.toJSONString(responseEntity.getBody()).toString()));
    }

}
