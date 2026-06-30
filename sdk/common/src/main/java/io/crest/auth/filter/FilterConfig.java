package io.crest.auth.filter;

import io.crest.observability.ObservabilityProperties;
import io.crest.observability.PrometheusMetricsAccessFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ObservabilityProperties.class)
@SuppressWarnings("unchecked")
// 定义过滤条件的数据结构和匹配信息
public class FilterConfig {

    @Bean
    public FilterRegistrationBean prometheusMetricsAccessFilter(ObservabilityProperties observabilityProperties) {
        FilterRegistrationBean filter = new FilterRegistrationBean<>();
        filter.setName("prometheusMetricsAccessFilter");
        filter.setFilter(new PrometheusMetricsAccessFilter(observabilityProperties));
        filter.addUrlPatterns("/*");
        filter.setOrder(-10);
        return filter;
    }

    @Bean
    public FilterRegistrationBean orderFilter() {
        FilterRegistrationBean filter = new FilterRegistrationBean<>();
        filter.setName("tokenFilter");
        filter.setFilter(new TokenFilter());
        filter.addUrlPatterns("/*");
        filter.setOrder(0);
        return filter;
    }

    @Bean
    public FilterRegistrationBean communityFilter() {
        FilterRegistrationBean filter = new FilterRegistrationBean<>();
        filter.setName("communityTokenFilter");
        filter.setFilter(new CommunityTokenFilter());
        filter.addUrlPatterns("/*");
        filter.setOrder(5);
        return filter;
    }
}
