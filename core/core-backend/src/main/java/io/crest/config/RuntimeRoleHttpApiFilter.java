package io.crest.config;

import io.crest.runtime.CrestRuntimeRole;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RuntimeRoleHttpApiFilter implements Filter {

    private final Environment environment;

    public RuntimeRoleHttpApiFilter(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        CrestRuntimeRole role = CrestRuntimeRole.from(environment);
        if (role.servesApi() || allowedOperationalPath((HttpServletRequest) request)) {
            chain.doFilter(request, response);
            return;
        }
        ((HttpServletResponse) response).sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    private boolean allowedOperationalPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.contains("/actuator/health") || path.contains("/actuator/prometheus");
    }
}
