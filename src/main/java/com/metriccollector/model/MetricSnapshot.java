package com.metriccollector.model;

import java.time.Instant;
import java.util.List;

/**
 * A snapshot of all collected metrics at a point in time.
 *
 * @param metrics           List of all collected metric data points
 * @param collectedAt       Timestamp when this snapshot was created
 * @param collectionDurationMs  How long the collection took in milliseconds
 */
public record MetricSnapshot(
        List<CollectedMetric> metrics,
        Instant collectedAt,
        long collectionDurationMs
) {

    /**
     * Returns the total number of metrics in this snapshot.
     */
    public int size() {
        return metrics.size();
    }

    /**
     * Creates an empty snapshot.
     */
    public static MetricSnapshot empty() {
        return new MetricSnapshot(List.of(), Instant.now(), 0);
    }
}
