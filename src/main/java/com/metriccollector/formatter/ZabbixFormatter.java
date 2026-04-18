package com.metriccollector.formatter;

import com.metriccollector.config.MetricCollectorProperties;
import com.metriccollector.model.CollectedMetric;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Formats metrics in Zabbix sender / trapper JSON format.
 * <p>
 * Each metric is output as a JSON object on a single line:
 * <pre>
 * {"host":"hostname","key":"metric.key[label]","value":"123.456","clock":1713456789,"ns":0}
 * </pre>
 * <p>
 * This format can be consumed by Zabbix trapper items or processed by
 * {@code zabbix_sender} utility.
 *
 * @see <a href="https://www.zabbix.com/documentation/current/en/manual/config/items/itemtypes/trapper">Zabbix Trapper Items</a>
 */
@Component
public class ZabbixFormatter implements MetricFormatter {

    private final String hostname;

    public ZabbixFormatter(MetricCollectorProperties properties) {
        this.hostname = properties.getExport().getFormats().getZabbix().getHostname();
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
     * Formats a single metric as a Zabbix sender JSON line.
     */
    private String formatSingleMetric(CollectedMetric metric) {
        String key = buildZabbixKey(metric.name(), metric.labels());
        long clock = metric.timestamp().getEpochSecond();
        int ns = metric.timestamp().getNano();
        String value = formatValue(metric.value());

        return String.format(
                "{\"host\":\"%s\",\"key\":\"%s\",\"value\":\"%s\",\"clock\":%d,\"ns\":%d}",
                escapeJson(hostname),
                escapeJson(key),
                escapeJson(value),
                clock,
                ns
        );
    }

    /**
     * Builds a Zabbix item key from metric name and labels.
     * Converts underscores to dots and appends labels as Zabbix key parameters.
     * Example: jvm_memory_heap_used → jvm.memory.heap.used[local]
     */
    private String buildZabbixKey(String name, Map<String, String> labels) {
        // Convert underscores to dots for Zabbix naming convention
        String key = name.replace('_', '.');

        // Append label values as key parameters
        if (labels != null && !labels.isEmpty()) {
            String params = labels.values().stream()
                    .map(this::escapeJson)
                    .collect(Collectors.joining(","));
            key += "[" + params + "]";
        }

        return key;
    }

    /**
     * Formats a numeric value, using integer representation when possible.
     */
    private String formatValue(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value) && !Double.isNaN(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /**
     * Escapes special characters for JSON string values.
     */
    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    @Override
    public String formatName() {
        return "zabbix";
    }

    @Override
    public String contentType() {
        return "application/x-ndjson";
    }
}
