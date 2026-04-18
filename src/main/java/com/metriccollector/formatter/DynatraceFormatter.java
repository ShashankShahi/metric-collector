package com.metriccollector.formatter;

import com.metriccollector.config.MetricCollectorProperties;
import com.metriccollector.model.CollectedMetric;
import com.metriccollector.model.MetricType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formats metrics in the Dynatrace MINT (Metrics Ingest) line protocol.
 * <p>
 * Each line represents a single data point:
 * <pre>
 * metric.key,dimension1=value1,dimension2=value2 gauge,123.456 1713456789000
 * </pre>
 *
 * @see <a href="https://docs.dynatrace.com/docs/extend-dynatrace/extend-metrics/reference/metric-ingestion-protocol">Dynatrace MINT Protocol</a>
 */
@Component
public class DynatraceFormatter implements MetricFormatter {

    private final String prefix;

    public DynatraceFormatter(MetricCollectorProperties properties) {
        this.prefix = properties.getExport().getFormats().getDynatrace().getPrefix();
    }

    @Override
    public String format(List<CollectedMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (CollectedMetric metric : metrics) {
            sb.append(formatSingleMetric(metric));
            sb.append('\n');
        }

        return sb.toString();
    }

    /**
     * Formats a single metric in MINT line protocol.
     */
    private String formatSingleMetric(CollectedMetric metric) {
        StringBuilder sb = new StringBuilder();

        // Metric key with prefix
        sb.append(buildMetricKey(metric.name()));

        // Dimensions
        String dimensions = formatDimensions(metric.labels());
        if (!dimensions.isEmpty()) {
            sb.append(',').append(dimensions);
        }

        // Space, then payload type and value
        sb.append(' ');
        sb.append(metric.type() == MetricType.COUNTER ? "count,delta=" : "gauge,");
        sb.append(formatValue(metric.value()));

        // Timestamp in milliseconds
        sb.append(' ');
        sb.append(metric.timestamp().toEpochMilli());

        return sb.toString();
    }

    /**
     * Builds the Dynatrace metric key from the metric name, applying the configured prefix.
     * Converts underscores to dots for Dynatrace naming convention.
     */
    private String buildMetricKey(String name) {
        // Remove the "jvm_" prefix and replace underscores with dots
        String key = name.replace('_', '.');

        // Apply configured prefix
        if (prefix != null && !prefix.isBlank()) {
            // Avoid double prefix if metric already starts with it
            if (!key.startsWith(prefix)) {
                key = prefix + "." + key;
            }
        }

        return key;
    }

    /**
     * Formats dimensions as comma-separated key=value pairs.
     * Dynatrace dimension keys: lowercase letters, numbers, hyphens, periods, colons, underscores.
     */
    private String formatDimensions(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return "";
        }

        return labels.entrySet().stream()
                .map(e -> sanitizeDimensionKey(e.getKey()) + "=" + sanitizeDimensionValue(e.getValue()))
                .collect(Collectors.joining(","));
    }

    /**
     * Sanitizes a dimension key for Dynatrace (lowercase, limited character set).
     */
    private String sanitizeDimensionKey(String key) {
        return key.toLowerCase()
                  .replaceAll("[^a-z0-9._:-]", "_")
                  .replaceAll("_+", "_")
                  .replaceAll("^_|_$", "");
    }

    /**
     * Sanitizes a dimension value — Dynatrace allows up to 250 chars, must not contain
     * newlines or leading/trailing whitespace.
     */
    private String sanitizeDimensionValue(String value) {
        if (value == null) return "";
        String sanitized = value.replace("\n", "").replace("\r", "").trim();
        if (sanitized.length() > 250) {
            sanitized = sanitized.substring(0, 250);
        }
        // Quote if contains special characters
        if (sanitized.contains(",") || sanitized.contains(" ") || sanitized.contains("=")) {
            return "\"" + sanitized.replace("\"", "\\\"") + "\"";
        }
        return sanitized;
    }

    /**
     * Formats a numeric value.
     */
    private String formatValue(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";  // Dynatrace doesn't support NaN/Inf
        }
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    @Override
    public String formatName() {
        return "dynatrace";
    }

    @Override
    public String contentType() {
        return "text/plain; charset=utf-8";
    }
}
