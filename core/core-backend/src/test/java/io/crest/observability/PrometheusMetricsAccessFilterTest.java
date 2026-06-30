package io.crest.observability;

import io.crest.constant.AuthConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PrometheusMetricsAccessFilterTest {

    @Test
    void shouldIgnoreNonPrometheusRequests() throws ServletException, IOException {
        ObservabilityProperties properties = new ObservabilityProperties();
        PrometheusMetricsAccessFilter filter = new PrometheusMetricsAccessFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/info");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(request.getAttribute(AuthConstant.PROMETHEUS_AUTHENTICATED_ATTR)).isNull();
    }

    @Test
    void shouldHidePrometheusEndpointWhenDisabled() throws ServletException, IOException {
        ObservabilityProperties properties = new ObservabilityProperties();
        PrometheusMetricsAccessFilter filter = new PrometheusMetricsAccessFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/actuator/prometheus");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(404);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectEnabledPrometheusEndpointWithoutConfiguredToken() throws ServletException, IOException {
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.getPrometheus().setEnabled(true);
        PrometheusMetricsAccessFilter filter = new PrometheusMetricsAccessFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/actuator/prometheus");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("Prometheus token is not configured");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectPrometheusRequestWithWrongBearerToken() throws ServletException, IOException {
        ObservabilityProperties properties = enabledProperties("metrics-secret");
        PrometheusMetricsAccessFilter filter = new PrometheusMetricsAccessFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/actuator/prometheus");
        request.addHeader("Authorization", "Bearer wrong-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer realm=\"crest-prometheus\"");
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void shouldAuthenticatePrometheusRequestWithMatchingBearerToken() throws ServletException, IOException {
        ObservabilityProperties properties = enabledProperties("metrics-secret");
        PrometheusMetricsAccessFilter filter = new PrometheusMetricsAccessFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/actuator/prometheus");
        request.addHeader("Authorization", "Bearer metrics-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(AuthConstant.PROMETHEUS_AUTHENTICATED_ATTR)).isEqualTo(Boolean.TRUE);
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldMatchConfiguredEndpointWithContextPath() throws ServletException, IOException {
        ObservabilityProperties properties = enabledProperties("metrics-secret");
        properties.getPrometheus().setEndpoint("/internal/metrics/");
        PrometheusMetricsAccessFilter filter = new PrometheusMetricsAccessFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/crest/internal/metrics");
        request.setContextPath("/crest");
        request.addHeader("Authorization", "Bearer metrics-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(AuthConstant.PROMETHEUS_AUTHENTICATED_ATTR)).isEqualTo(Boolean.TRUE);
        verify(chain).doFilter(request, response);
    }

    private ObservabilityProperties enabledProperties(String token) {
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.getPrometheus().setEnabled(true);
        properties.getPrometheus().setToken(token);
        return properties;
    }
}
