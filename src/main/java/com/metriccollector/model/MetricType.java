package com.metriccollector.model;

/**
 * Type classification for collected metrics.
 */
public enum MetricType {

    /** A value that can go up and down (e.g. memory usage, thread count). */
    GAUGE,

    /** A monotonically increasing value (e.g. GC count, total requests). */
    COUNTER
}
