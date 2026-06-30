package io.crest.observability;

import io.crest.utils.CrestPermissionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/observability")
public class ObservabilityServer {

    private final ObservabilityProperties observabilityProperties;

    public ObservabilityServer(ObservabilityProperties observabilityProperties) {
        this.observabilityProperties = observabilityProperties;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        CrestPermissionUtils.requireAdmin();
        ObservabilityProperties.Prometheus prometheus = observabilityProperties.getPrometheus();
        ObservabilityProperties.Grafana grafana = observabilityProperties.getGrafana();
        ObservabilityProperties.Api api = observabilityProperties.getApi();

        Map<String, Object> prometheusStatus = new LinkedHashMap<>();
        prometheusStatus.put("enabled", prometheus.isEnabled());
        prometheusStatus.put("endpoint", prometheus.getEndpoint());
        prometheusStatus.put("authType", "Bearer Token");
        prometheusStatus.put("tokenConfigured", StringUtils.isNotBlank(prometheus.getToken()));
        prometheusStatus.put("maxUriTags", prometheus.getMaxUriTags());

        Map<String, Object> apiStatus = new LinkedHashMap<>();
        apiStatus.put("routeInventoryEnabled", api.isRouteInventoryEnabled());
        apiStatus.put("routePrefix", StringUtils.defaultString(api.getRoutePrefix()));

        Map<String, Object> grafanaStatus = new LinkedHashMap<>();
        grafanaStatus.put("enabled", grafana.isEnabled());
        grafanaStatus.put("publicUrl", StringUtils.defaultString(grafana.getPublicUrl()));
        grafanaStatus.put("provisionedDashboards", List.of(
                "Crest 服务总览",
                "Crest API 监控",
                "Crest 告警与 SLO",
                "Crest JVM 运行时",
                "Crest 数据库与连接池",
                "Crest 任务与缓存"
        ));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prometheus", prometheusStatus);
        result.put("api", apiStatus);
        result.put("grafana", grafanaStatus);
        return result;
    }
}
