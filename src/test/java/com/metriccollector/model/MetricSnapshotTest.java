package com.metriccollector.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetricSnapshotTest {

    @Test
    void testSize() {
        CollectedMetric metric1 = new CollectedMetric("m1", 1, Map.of(), Instant.now(), MetricType.GAUGE);
        CollectedMetric metric2 = new CollectedMetric("m2", 2, Map.of(), Instant.now(), MetricType.COUNTER);
        
        MetricSnapshot snapshot = new MetricSnapshot(List.of(metric1, metric2), Instant.now(), 150);
        
        assertEquals(2, snapshot.size());
        assertEquals(150, snapshot.collectionDurationMs());
        assertEquals(2, snapshot.metrics().size());
        assertNotNull(snapshot.collectedAt());
    }

    @Test
    void testEmptySnapshot() {
        MetricSnapshot snapshot = MetricSnapshot.empty();
        
        assertEquals(0, snapshot.size());
        assertEquals(0, snapshot.collectionDurationMs());
        assertTrue(snapshot.metrics().isEmpty());
        assertNotNull(snapshot.collectedAt());
    }
}
