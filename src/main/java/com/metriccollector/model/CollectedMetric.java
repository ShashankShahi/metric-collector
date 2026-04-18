package com.metriccollector.model;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a single collected metric data point from a JMX MBean attribute.
 *
 * @param name      Normalized metric name (e.g. "jvm_memory_heap_used_bytes")
 * @param value     Numeric value of the metric
 * @param labels    Dimensional labels/tags (e.g. source=local, area=heap)
 * @param timestamp Time of collection
 * @param type      GAUGE or COUNTER classification
 * @param help      Human-readable description of the metric
 */
public record CollectedMetric(
        String name,
        double value,
        Map<String, String> labels,
        Instant timestamp,
        MetricType type,
        String help
) {

    /**
     * Convenience constructor without help text.
     */
    public CollectedMetric(String name, double value, Map<String, String> labels,
                           Instant timestamp, MetricType type) {
        this(name, value, labels, timestamp, type, "");
    }
}
