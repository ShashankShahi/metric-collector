package com.metriccollector.registry;

import com.metriccollector.model.CollectedMetric;
import com.metriccollector.model.MetricSnapshot;
import com.metriccollector.model.MetricType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetricRegistryTest {

    private MetricRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MetricRegistry();
    }

    @Test
    void testInitialSnapshotIsEmpty() {
        MetricSnapshot snapshot = registry.getSnapshot();
        assertNotNull(snapshot);
        assertEquals(0, snapshot.size());
    }

    @Test
    void testUpdateAndGetSnapshot() {
        CollectedMetric metric = new CollectedMetric("m1", 1.0, Map.of(), Instant.now(), MetricType.GAUGE);
        MetricSnapshot snapshot = new MetricSnapshot(List.of(metric), Instant.now(), 100);

        registry.updateSnapshot(snapshot);

        MetricSnapshot retrieved = registry.getSnapshot();
        assertEquals(1, retrieved.size());
        assertEquals("m1", retrieved.metrics().get(0).name());
    }

    @Test
    void testStreamSnapshots() {
        CollectedMetric metric1 = new CollectedMetric("m1", 1.0, Map.of(), Instant.now(), MetricType.GAUGE);
        MetricSnapshot snapshot1 = new MetricSnapshot(List.of(metric1), Instant.now(), 100);

        CollectedMetric metric2 = new CollectedMetric("m2", 2.0, Map.of(), Instant.now(), MetricType.GAUGE);
        MetricSnapshot snapshot2 = new MetricSnapshot(List.of(metric2), Instant.now(), 100);

        // Subscriber 1
        StepVerifier.create(registry.streamSnapshots())
                .then(() -> registry.updateSnapshot(snapshot1))
                .expectNext(snapshot1)
                .then(() -> registry.updateSnapshot(snapshot2))
                .expectNext(snapshot2)
                .thenCancel()
                .verify();
    }
}
