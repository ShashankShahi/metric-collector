package com.metriccollector.formatter;

import com.metriccollector.model.CollectedMetric;

import java.util.List;

/**
 * Interface for formatting collected metrics into monitoring-system-specific formats.
 */
public interface MetricFormatter {

    /**
     * Formats a list of metrics into the target format.
     *
     * @param metrics the metrics to format
     * @return formatted string representation
     */
    String format(List<CollectedMetric> metrics);

    /**
     * Returns the name of this format (e.g. "prometheus", "zabbix", "dynatrace").
     */
    String formatName();

    /**
     * Returns the MIME content type for this format.
     */
    String contentType();
}
