package com.metriccollector.collector;

import com.metriccollector.config.MetricCollectorProperties;
import com.metriccollector.config.MetricCollectorProperties.JmxTarget;
import com.metriccollector.model.CollectedMetric;
import com.metriccollector.model.MetricSnapshot;
import com.metriccollector.registry.MetricRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.time.Instant;

/**
 * Scheduled service that collects JMX MBean metrics from all configured targets
 * and publishes them to the {@link MetricRegistry}.
 * <p>
 * Runs at a fixed rate defined by {@code metric-collector.jmx.collection-interval-ms}.
 */
@Service
public class JmxCollectorService {

    private static final Logger log = LoggerFactory.getLogger(JmxCollectorService.class);

    private final MetricCollectorProperties properties;
    private final JmxConnectionPool connectionPool;
    private final MBeanAttributeMapper attributeMapper;
    private final MetricRegistry metricRegistry;

    public JmxCollectorService(MetricCollectorProperties properties,
                                JmxConnectionPool connectionPool,
                                MBeanAttributeMapper attributeMapper,
                                MetricRegistry metricRegistry) {
        this.properties = properties;
        this.connectionPool = connectionPool;
        this.attributeMapper = attributeMapper;
        this.metricRegistry = metricRegistry;
    }

    /**
     * Scheduled collection task. Runs at the configured interval.
     */
    @Scheduled(fixedRateString = "${metric-collector.jmx.collection-interval-ms:15000}")
    public void collectMetrics() {
        long startTime = System.currentTimeMillis();
        List<CollectedMetric> allMetrics = new ArrayList<>();
        int targetCount = 0;
        int errorCount = 0;

        for (JmxTarget target : properties.getJmx().getTargets()) {
            targetCount++;
            try {
                MBeanServerConnection connection = connectionPool.getConnection(target);
                List<CollectedMetric> targetMetrics = collectFromTarget(connection, target);
                allMetrics.addAll(targetMetrics);
                log.debug("Collected {} metrics from target '{}'", targetMetrics.size(), target.getName());
            } catch (Exception e) {
                errorCount++;
                log.error("Failed to collect metrics from target '{}': {}", target.getName(), e.getMessage());
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;

        if (!allMetrics.isEmpty()) {
            MetricSnapshot snapshot = new MetricSnapshot(
                    List.copyOf(allMetrics),
                    Instant.now(),
                    durationMs
            );
            metricRegistry.updateSnapshot(snapshot);
            log.info("Collected {} metrics from {}/{} targets in {}ms",
                    allMetrics.size(), targetCount - errorCount, targetCount, durationMs);
        } else {
            log.warn("No metrics collected from any target (errors: {})", errorCount);
        }
    }

    /**
     * Collects metrics from a single JMX target for all configured MBean patterns.
     */
    private List<CollectedMetric> collectFromTarget(MBeanServerConnection connection,
                                                     JmxTarget target) throws Exception {
        List<CollectedMetric> metrics = new ArrayList<>();

        for (String mbeanPattern : properties.getJmx().getMbeans()) {
            try {
                ObjectName objectNamePattern = new ObjectName(mbeanPattern);
                Set<ObjectName> matchingNames = connection.queryNames(objectNamePattern, null);

                for (ObjectName objectName : matchingNames) {
                    List<CollectedMetric> mapped = attributeMapper.mapAttributes(
                            connection, objectName, target.getName());
                    metrics.addAll(mapped);
                }
            } catch (Exception e) {
                log.warn("Error querying MBean pattern '{}' on target '{}': {}",
                        mbeanPattern, target.getName(), e.getMessage());
            }
        }

        return metrics;
    }

    /**
     * Cleanup: close all JMX connections on shutdown.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down JMX collector, closing connections...");
        connectionPool.closeAll();
    }
}
