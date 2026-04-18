package com.metriccollector.collector;

import com.metriccollector.model.CollectedMetric;
import com.metriccollector.model.MetricType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import java.time.Instant;
import java.util.*;

/**
 * Maps raw JMX MBean attributes to normalized {@link CollectedMetric} instances.
 * <p>
 * Handles primitive types, {@link CompositeData} (e.g. MemoryUsage), and arrays.
 * Automatically determines {@link MetricType} based on attribute semantics.
 */
@Component
public class MBeanAttributeMapper {

    private static final Logger log = LoggerFactory.getLogger(MBeanAttributeMapper.class);

    /**
     * Attribute name patterns that indicate a COUNTER (monotonically increasing).
     */
    private static final Set<String> COUNTER_KEYWORDS = Set.of(
            "count", "total", "totalcompilationtime", "collectioncount", "collectiontime",
            "totalloadedclasscount", "totalstartedthreadcount"
    );

    /**
     * Reads all numeric attributes from the given MBean and returns them as collected metrics.
     *
     * @param connection  the MBeanServerConnection
     * @param objectName  the MBean ObjectName
     * @param sourceName  the JMX target name (used as a label)
     * @return list of collected metrics
     */
    public List<CollectedMetric> mapAttributes(MBeanServerConnection connection,
                                                ObjectName objectName,
                                                String sourceName) {
        List<CollectedMetric> metrics = new ArrayList<>();
        Instant now = Instant.now();

        try {
            MBeanInfo info = connection.getMBeanInfo(objectName);
            String domain = objectName.getDomain();
            String type = objectName.getKeyProperty("type");
            String mbeanName = objectName.getKeyProperty("name");

            for (MBeanAttributeInfo attrInfo : info.getAttributes()) {
                if (!attrInfo.isReadable()) continue;

                String attrName = attrInfo.getName();
                try {
                    Object attrValue = connection.getAttribute(objectName, attrName);
                    if (attrValue == null) continue;

                    if (attrValue instanceof CompositeData compositeData) {
                        // Flatten CompositeData (e.g. MemoryUsage → used, committed, max, init)
                        metrics.addAll(mapCompositeData(compositeData, domain, type,
                                mbeanName, attrName, sourceName, now));
                    } else if (attrValue instanceof Number number) {
                        String metricName = buildMetricName(domain, type, mbeanName, attrName);
                        String help = buildHelp(type, mbeanName, attrName);
                        MetricType metricType = inferMetricType(attrName);
                        Map<String, String> labels = buildLabels(sourceName, mbeanName);

                        metrics.add(new CollectedMetric(
                                metricName, number.doubleValue(), labels, now, metricType, help));
                    }
                    // Skip non-numeric, non-composite types (Strings, arrays of strings, etc.)
                } catch (Exception e) {
                    log.trace("Cannot read attribute {}.{}: {}", objectName, attrName, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read MBean {}: {}", objectName, e.getMessage());
        }

        return metrics;
    }

    /**
     * Flattens a CompositeData value into individual metrics.
     */
    private List<CollectedMetric> mapCompositeData(CompositeData data, String domain,
                                                    String type, String mbeanName,
                                                    String attrName, String sourceName,
                                                    Instant now) {
        List<CollectedMetric> metrics = new ArrayList<>();

        for (String key : data.getCompositeType().keySet()) {
            Object itemValue = data.get(key);
            if (itemValue instanceof Number number) {
                String metricName = buildMetricName(domain, type, mbeanName, attrName + "_" + key);
                String help = buildHelp(type, mbeanName, attrName + " " + key);
                MetricType metricType = inferMetricType(key);
                Map<String, String> labels = buildLabels(sourceName, mbeanName);

                metrics.add(new CollectedMetric(
                        metricName, number.doubleValue(), labels, now, metricType, help));
            }
        }
        return metrics;
    }

    /**
     * Builds a Prometheus-style metric name from MBean components.
     * Example: java.lang:type=Memory, attr=HeapMemoryUsage, key=used → jvm_memory_heap_memory_usage_used
     */
    String buildMetricName(String domain, String type, String mbeanName, String attribute) {
        StringBuilder sb = new StringBuilder();

        // Prefix: domain mapping
        if ("java.lang".equals(domain)) {
            sb.append("jvm");
        } else {
            sb.append(sanitize(domain));
        }

        // Type
        if (type != null) {
            sb.append('_').append(sanitize(type));
        }

        // MBean name (for GC collectors, etc.)
        if (mbeanName != null) {
            // Don't repeat type in the name
            String sanitizedName = sanitize(mbeanName);
            if (!sanitizedName.equalsIgnoreCase(sanitize(type))) {
                sb.append('_').append(sanitizedName);
            }
        }

        // Attribute
        sb.append('_').append(sanitize(attribute));

        return sb.toString().toLowerCase();
    }

    /**
     * Builds a human-readable help string for a metric.
     */
    private String buildHelp(String type, String mbeanName, String attribute) {
        StringBuilder sb = new StringBuilder();
        if (type != null) sb.append(type);
        if (mbeanName != null) sb.append(" (").append(mbeanName).append(")");
        sb.append(" ").append(camelToSpaces(attribute));
        return sb.toString().trim();
    }

    /**
     * Builds the label map for a metric.
     */
    private Map<String, String> buildLabels(String sourceName, String mbeanName) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("source", sourceName);
        if (mbeanName != null) {
            labels.put("name", mbeanName);
        }
        return labels;
    }

    /**
     * Infers COUNTER vs GAUGE from the attribute name.
     */
    private MetricType inferMetricType(String attributeName) {
        String lower = attributeName.toLowerCase().replaceAll("[^a-z]", "");
        for (String keyword : COUNTER_KEYWORDS) {
            if (lower.contains(keyword)) {
                return MetricType.COUNTER;
            }
        }
        return MetricType.GAUGE;
    }

    /**
     * Sanitizes a string for use in a metric name: replaces non-alphanumeric with underscore.
     */
    private String sanitize(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9]", "_")
                    .replaceAll("_+", "_")
                    .replaceAll("^_|_$", "");
    }

    /**
     * Converts camelCase to spaced words.
     */
    private String camelToSpaces(String input) {
        if (input == null) return "";
        return input.replaceAll("([a-z])([A-Z])", "$1 $2")
                    .replaceAll("_", " ")
                    .toLowerCase();
    }
}
