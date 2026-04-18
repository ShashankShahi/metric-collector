package com.metriccollector.formatter;

import com.metriccollector.model.CollectedMetric;
import com.metriccollector.model.MetricType;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Formats metrics in the Prometheus exposition text format.
 * <p>
 * Output format:
 * <pre>
 * # HELP metric_name Description text
 * # TYPE metric_name gauge|counter
 * metric_name{label1="value1",label2="value2"} 123.456 1713456789000
 * </pre>
 *
 * @see <a href="https://prometheus.io/docs/instrumenting/exposition_formats/">Prometheus Exposition Formats</a>
 */
@Component
public class PrometheusFormatter implements MetricFormatter {

    @Override
    public String format(List<CollectedMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return "# No metrics available\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# MetricCollector Prometheus Export\n");
        sb.append("# Generated at ").append(java.time.Instant.now()).append("\n\n");

        // Group metrics by name to output HELP/TYPE headers once per metric name
        Map<String, List<CollectedMetric>> grouped = metrics.stream()
                .collect(Collectors.groupingBy(CollectedMetric::name, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<CollectedMetric>> entry : grouped.entrySet()) {
            String metricName = entry.getKey();
            List<CollectedMetric> metricList = entry.getValue();

            // Get help and type from first occurrence
            CollectedMetric first = metricList.get(0);

            // HELP line
            String help = first.help() != null && !first.help().isBlank()
                    ? first.help()
                    : metricName.replace('_', ' ');
            sb.append("# HELP ").append(metricName).append(' ').append(escapeHelp(help)).append('\n');

            // TYPE line
            sb.append("# TYPE ").append(metricName).append(' ')
              .append(first.type() == MetricType.COUNTER ? "counter" : "gauge").append('\n');

            // Value lines
            for (CollectedMetric metric : metricList) {
                sb.append(metricName);
                sb.append(formatLabels(metric.labels()));
                sb.append(' ');
                sb.append(formatValue(metric.value()));
                sb.append(' ');
                sb.append(metric.timestamp().toEpochMilli());
                sb.append('\n');
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    /**
     * Formats labels in Prometheus style: {key1="value1",key2="value2"}
     */
    private String formatLabels(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return "";
        }
        return labels.entrySet().stream()
                .map(e -> e.getKey() + "=\"" + escapeLabelValue(e.getValue()) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    /**
     * Formats a double value, avoiding unnecessary decimal places for whole numbers.
     */
    private String formatValue(double value) {
        if (Double.isNaN(value)) return "NaN";
        if (Double.isInfinite(value)) return value > 0 ? "+Inf" : "-Inf";
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /**
     * Escapes special characters in Prometheus HELP text.
     */
    private String escapeHelp(String text) {
        return text.replace("\\", "\\\\").replace("\n", "\\n");
    }

    /**
     * Escapes special characters in Prometheus label values.
     */
    private String escapeLabelValue(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n");
    }

    @Override
    public String formatName() {
        return "prometheus";
    }

    @Override
    public String contentType() {
        return "text/plain; version=0.0.4; charset=utf-8";
    }
}
