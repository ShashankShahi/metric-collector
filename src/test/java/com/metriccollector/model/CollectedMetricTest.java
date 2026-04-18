package com.metriccollector.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CollectedMetricTest {

    @Test
    void testFullConstructor() {
        Instant now = Instant.now();
        CollectedMetric metric = new CollectedMetric(
                "test_metric",
                42.0,
                Map.of("label", "value"),
                now,
                MetricType.GAUGE,
                "A test metric"
        );

        assertEquals("test_metric", metric.name());
        assertEquals(42.0, metric.value());
        assertEquals("value", metric.labels().get("label"));
        assertEquals(now, metric.timestamp());
        assertEquals(MetricType.GAUGE, metric.type());
        assertEquals("A test metric", metric.help());
    }

    @Test
    void testConvenienceConstructor() {
        Instant now = Instant.now();
        CollectedMetric metric = new CollectedMetric(
                "test_metric",
                42.0,
                Map.of("label", "value"),
                now,
                MetricType.COUNTER
        );

        assertEquals("test_metric", metric.name());
        assertEquals(42.0, metric.value());
        assertEquals("value", metric.labels().get("label"));
        assertEquals(now, metric.timestamp());
        assertEquals(MetricType.COUNTER, metric.type());
        assertEquals("", metric.help()); // Defaults to empty string
    }
}
