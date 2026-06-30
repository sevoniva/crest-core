package io.crest.observability;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityMetricsConfig {

    @Bean
    public MeterFilter httpServerUriTagLimit(ObservabilityProperties observabilityProperties) {
        int maxUriTags = observabilityProperties.getPrometheus().getMaxUriTags();
        if (maxUriTags <= 0) {
            return MeterFilter.accept();
        }
        return MeterFilter.maximumAllowableTags("http.server.requests", "uri", maxUriTags, MeterFilter.deny());
    }
}
