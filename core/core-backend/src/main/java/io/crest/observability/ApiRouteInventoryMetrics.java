package io.crest.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
/**
 * API 路由清单指标注册器，用于把已注册路由暴露为观测指标
 */
public class ApiRouteInventoryMetrics implements SmartInitializingSingleton {

    private static final String ROUTE_INFO_METRIC = "crest.api.route.info";

    private final MeterRegistry meterRegistry;
    private final RequestMappingHandlerMapping requestMappingHandlerMapping;
    private final ObservabilityProperties observabilityProperties;

    /**
     * 构造 API 路由指标注册器
     */
    public ApiRouteInventoryMetrics(MeterRegistry meterRegistry,
                                    RequestMappingHandlerMapping requestMappingHandlerMapping,
                                    ObservabilityProperties observabilityProperties) {
        this.meterRegistry = meterRegistry;
        this.requestMappingHandlerMapping = requestMappingHandlerMapping;
        this.observabilityProperties = observabilityProperties;
    }

    /**
     * 在单例初始化完成后注册路由清单指标
     */
    @Override
    public void afterSingletonsInstantiated() {
        ObservabilityProperties.Api api = observabilityProperties.getApi();
        if (api == null || !api.isRouteInventoryEnabled()) {
            return;
        }

        List<RouteMetric> routes = collectRoutes(api.getRoutePrefix());
        for (RouteMetric route : routes) {
            Gauge.builder(ROUTE_INFO_METRIC, route, ignored -> 1)
                    .description("Registered Crest API routes. Value is always 1; labels describe the route.")
                    .tag("method", route.method())
                    .tag("uri", route.uri())
                    .tag("module", route.module())
                    .tag("controller", route.controller())
                    .tag("handler", route.handler())
                    .strongReference(true)
                    .register(meterRegistry);
        }
    }

    /**
     * 收集符合前缀和 API 范围的路由指标信息
     */
    private List<RouteMetric> collectRoutes(String routePrefix) {
        Set<RouteMetric> result = new LinkedHashSet<>();
        requestMappingHandlerMapping.getHandlerMethods().forEach((info, handlerMethod) -> {
            for (String pattern : routePatterns(info)) {
                String uri = normalizeUri(routePrefix, pattern);
                if (!isApiRoute(uri)) {
                    continue;
                }
                Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
                if (methods.isEmpty()) {
                    result.add(toMetric("ANY", uri, handlerMethod));
                    continue;
                }
                methods.forEach(method -> result.add(toMetric(method.name(), uri, handlerMethod)));
            }
        });

        return result.stream()
                .sorted(Comparator.comparing(RouteMetric::uri).thenComparing(RouteMetric::method))
                .toList();
    }

    /**
     * 兼容不同 Spring 路径匹配模式下的路由表达式
     */
    private Set<String> routePatterns(RequestMappingInfo info) {
        Set<String> patterns = new LinkedHashSet<>();
        if (info.getPathPatternsCondition() != null) {
            info.getPathPatternsCondition().getPatternValues().forEach(patterns::add);
        }
        if (info.getPatternsCondition() != null) {
            patterns.addAll(info.getPatternsCondition().getPatterns());
        }
        return patterns;
    }

    /**
     * 将处理器方法转换为指标标签对象
     */
    private RouteMetric toMetric(String method, String uri, HandlerMethod handlerMethod) {
        String controller = handlerMethod.getBeanType().getSimpleName();
        String handler = controller + "." + handlerMethod.getMethod().getName();
        return new RouteMetric(method, uri, classifyModule(uri), controller, handler);
    }

    /**
     * 根据路由首段归类 API 所属模块
     */
    private String classifyModule(String uri) {
        String segment = firstRouteSegment(uri);
        return switch (segment) {
            case "data-visualization", "visualization-subject", "visualization-background", "panel",
                    "outer-params", "linkage", "link-jump", "chart", "chart-data" -> "visualization";
            case "dataset-tree", "dataset-field", "dataset-data", "dataset-sync",
                    "dataset-table-sql-log" -> "dataset";
            case "datasource", "datasource-driver", "engine" -> "datasource";
            case "user", "role", "auth", "org", "menu", "resource" -> "access-control";
            case "sys-parameter", "sys-variable", "typeface", "watermark", "static-resource",
                    "symmetric-key", "public-key", "model" -> "system";
            case "share", "store", "ticket" -> "sharing";
            case "relation" -> "lineage";
            case "export-center" -> "export";
            case "sso", "login", "logout" -> "authentication";
            case "audit-log", "observability", "msg-center", "ai-base" -> "operations";
            default -> "platform";
        };
    }

    /**
     * 提取 API 路由中的第一个业务路径片段
     */
    private String firstRouteSegment(String uri) {
        String normalized = normalizePath(uri);
        String prefix = "/api/v1/";
        if (!normalized.startsWith(prefix)) {
            return "";
        }
        String remainder = normalized.substring(prefix.length());
        int slashIndex = remainder.indexOf('/');
        return slashIndex < 0 ? remainder : remainder.substring(0, slashIndex);
    }

    /**
     * 合并路由前缀和控制器路径
     */
    private String normalizeUri(String routePrefix, String pattern) {
        String path = normalizePath(pattern);
        String prefix = normalizePath(routePrefix);
        if (StringUtils.isBlank(prefix) || "/".equals(prefix) || path.startsWith(prefix + "/")) {
            return path;
        }
        return prefix + path;
    }

    /**
     * 将路径标准化为以斜杠开头的单斜杠形式
     */
    private String normalizePath(String value) {
        if (StringUtils.isBlank(value)) {
            return "/";
        }
        String normalized = value.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized.replaceAll("/{2,}", "/");
    }

    /**
     * 判断路径是否属于需要统计的业务 API
     */
    private boolean isApiRoute(String uri) {
        return uri.startsWith("/api/v1/")
                && !uri.startsWith("/api/v1/actuator/")
                && !uri.startsWith("/api/v1/error")
                && !uri.startsWith("/api/v1/v3/api-docs")
                && !uri.startsWith("/api/v1/swagger-ui")
                && !uri.startsWith("/api/v1/doc.html");
    }

    /**
     * 路由指标标签数据
     */
    private record RouteMetric(String method, String uri, String module, String controller, String handler) {
    }
}
