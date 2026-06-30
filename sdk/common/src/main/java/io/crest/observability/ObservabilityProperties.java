package io.crest.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 可观测性配置属性，承载 Prometheus、Grafana 和诊断 API 开关
@ConfigurationProperties(prefix = "crest.observability")
public class ObservabilityProperties {

    private final Prometheus prometheus = new Prometheus();

    private final Grafana grafana = new Grafana();

    private Api api = new Api();

    // 获取 Prometheus 指标配置
    public Prometheus getPrometheus() {
        return prometheus;
    }

    // 获取 Grafana 展示配置
    public Grafana getGrafana() {
        return grafana;
    }

    // 获取诊断 API 配置
    public Api getApi() {
        return api;
    }

    // 设置诊断 API 配置
    public void setApi(Api api) {
        this.api = api;
    }

    // Prometheus 指标暴露配置
    public static class Prometheus {
        private boolean enabled;
        private String endpoint = "/api/v1/actuator/prometheus";
        private String token;
        private int maxUriTags = 200;

        // 是否启用 Prometheus 指标端点
        public boolean isEnabled() {
            return enabled;
        }

        // 设置是否启用 Prometheus 指标端点
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        // 获取指标端点路径
        public String getEndpoint() {
            return endpoint;
        }

        // 设置指标端点路径
        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        // 获取指标访问令牌
        public String getToken() {
            return token;
        }

        // 设置指标访问令牌
        public void setToken(String token) {
            this.token = token;
        }

        // 获取允许记录的最大 URI 标签数量
        public int getMaxUriTags() {
            return maxUriTags;
        }

        // 设置允许记录的最大 URI 标签数量
        public void setMaxUriTags(int maxUriTags) {
            this.maxUriTags = maxUriTags;
        }
    }

    // Grafana 外部展示配置
    public static class Grafana {
        private boolean enabled;
        private String publicUrl;

        // 是否启用 Grafana 链接展示
        public boolean isEnabled() {
            return enabled;
        }

        // 设置是否启用 Grafana 链接展示
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        // 获取 Grafana 访问地址
        public String getPublicUrl() {
            return publicUrl;
        }

        // 设置 Grafana 访问地址
        public void setPublicUrl(String publicUrl) {
            this.publicUrl = publicUrl;
        }
    }

    // 诊断 API 配置
    public static class Api {
        private boolean routeInventoryEnabled = true;
        private String routePrefix = "/api/v1";

        // 是否启用路由清单诊断接口
        public boolean isRouteInventoryEnabled() {
            return routeInventoryEnabled;
        }

        // 设置是否启用路由清单诊断接口
        public void setRouteInventoryEnabled(boolean routeInventoryEnabled) {
            this.routeInventoryEnabled = routeInventoryEnabled;
        }

        // 获取诊断 API 路由前缀
        public String getRoutePrefix() {
            return routePrefix;
        }

        // 设置诊断 API 路由前缀
        public void setRoutePrefix(String routePrefix) {
            this.routePrefix = routePrefix;
        }
    }
}
