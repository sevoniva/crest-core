package io.crest.auth.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;

import io.crest.utils.CommunityUtils;
import io.crest.utils.CrestPermissionUtils;

// 定义过滤条件的数据结构和匹配信息
public class CommunityTokenFilter implements Filter {

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        try {
            CommunityUtils.setInfo(CrestPermissionUtils.communityScopeSql());
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            CommunityUtils.removeInfo();
        }
    }
}
