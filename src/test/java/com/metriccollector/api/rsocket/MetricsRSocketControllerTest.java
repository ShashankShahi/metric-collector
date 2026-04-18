package com.metriccollector.api.rsocket;

import com.metriccollector.model.CollectedMetric;
import com.metriccollector.model.MetricSnapshot;
import com.metriccollector.model.MetricType;
import com.metriccollector.registry.MetricRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsRSocketControllerTest {

    private MetricsRSocketController controller;
    private MetricRegistry metricRegistry;

    @BeforeEach
    void setUp() {
        metricRegistry = mock(MetricRegistry.class);
        controller = new MetricsRSocketController(metricRegistry);
    }

    @Test
    void testGetSnapshot() {
        CollectedMetric metric = new CollectedMetric("test", 1.0, Map.of(), Instant.now(), MetricType.GAUGE);
        MetricSnapshot snapshot = new MetricSnapshot(List.of(metric), Instant.now(), 10);
        when(metricRegistry.getSnapshot()).thenReturn(snapshot);

        StepVerifier.create(controller.getSnapshot())
                .expectNextMatches(s -> s.size() == 1 && s.metrics().get(0).name().equals("test"))
                .verifyComplete();
    }

    @Test
    void testStreamMetrics() {
        CollectedMetric metric = new CollectedMetric("test", 1.0, Map.of(), Instant.now(), MetricType.GAUGE);
        MetricSnapshot snapshot = new MetricSnapshot(List.of(metric), Instant.now(), 10);
        when(metricRegistry.streamSnapshots()).thenReturn(Flux.just(snapshot, snapshot));

        StepVerifier.create(controller.streamMetrics())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void testPing() {
        StepVerifier.create(controller.ping())
                .verifyComplete();
    }
}
