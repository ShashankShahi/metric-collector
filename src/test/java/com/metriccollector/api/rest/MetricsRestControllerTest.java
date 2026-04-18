package com.metriccollector.api.rest;

import com.metriccollector.formatter.MetricFormatter;
import com.metriccollector.model.CollectedMetric;
import com.metriccollector.model.MetricSnapshot;
import com.metriccollector.model.MetricType;
import com.metriccollector.registry.MetricRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsRestControllerTest {

    private WebTestClient webTestClient;
    private MetricRegistry metricRegistry;

    @BeforeEach
    void setUp() {
        metricRegistry = mock(MetricRegistry.class);

        MetricFormatter mockFormatter = mock(MetricFormatter.class);
        when(mockFormatter.formatName()).thenReturn("custom");
        when(mockFormatter.contentType()).thenReturn("text/plain");
        when(mockFormatter.format(org.mockito.ArgumentMatchers.anyList())).thenReturn("custom format output");

        MetricsRestController controller = new MetricsRestController(metricRegistry, List.of(mockFormatter));
        webTestClient = WebTestClient.bindToController(controller).build();

        CollectedMetric metric = new CollectedMetric(
                "test", 1.0, Map.of(), Instant.now(), MetricType.GAUGE
        );
        MetricSnapshot snapshot = new MetricSnapshot(List.of(metric), Instant.now(), 10);
        when(metricRegistry.getSnapshot()).thenReturn(snapshot);
    }

    @Test
    void testGetMetricsDefaultJson() {
        webTestClient.get()
                .uri("/api/v1/metrics")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.metrics.length()").isEqualTo(1)
                .jsonPath("$.metrics[0].name").isEqualTo("test");
    }

    @Test
    void testGetMetricsCustomFormat() {
        webTestClient.get()
                .uri("/api/v1/metrics?format=custom")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("text/plain")
                .expectBody(String.class)
                .isEqualTo("custom format output");
    }

    @Test
    void testGetMetricsUnknownFormat() {
        webTestClient.get()
                .uri("/api/v1/metrics?format=unknown")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Unknown format: unknown");
    }

    @Test
    void testHealthEndpoint() {
        webTestClient.get()
                .uri("/api/v1/metrics/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.metricCount").isEqualTo(1);
    }

    @Test
    void testFormatsEndpoint() {
        webTestClient.get()
                .uri("/api/v1/metrics/formats")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.formats").isArray();
    }
}
