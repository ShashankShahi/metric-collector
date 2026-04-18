package com.metriccollector.writer;

import com.metriccollector.config.MetricCollectorProperties;
import com.metriccollector.formatter.MetricFormatter;
import com.metriccollector.model.MetricSnapshot;
import com.metriccollector.registry.MetricRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Subscribes to the {@link MetricRegistry} reactive stream and writes
 * each new metric snapshot to log files using all enabled formatters.
 * <p>
 * Each format writes to a dedicated Logback logger that is configured
 * with its own rolling file appender in {@code logback-spring.xml}.
 */
@Component
public class MetricLogWriter {

    private static final Logger log = LoggerFactory.getLogger(MetricLogWriter.class);

    /**
     * Dedicated loggers for each format — these map to the named loggers
     * in logback-spring.xml that have their own file appenders.
     */
    private static final Logger prometheusLogger = LoggerFactory.getLogger("metrics.prometheus");
    private static final Logger zabbixLogger = LoggerFactory.getLogger("metrics.zabbix");
    private static final Logger dynatraceLogger = LoggerFactory.getLogger("metrics.dynatrace");

    private final MetricRegistry metricRegistry;
    private final MetricCollectorProperties properties;
    private final Map<String, MetricFormatter> formatters;

    private Disposable subscription;

    public MetricLogWriter(MetricRegistry metricRegistry,
                           MetricCollectorProperties properties,
                           List<MetricFormatter> formatterList) {
        this.metricRegistry = metricRegistry;
        this.properties = properties;
        this.formatters = formatterList.stream()
                .collect(Collectors.toMap(MetricFormatter::formatName, Function.identity()));
    }

    /**
     * Starts listening for new metric snapshots after the application context is ready.
     */
    @PostConstruct
    public void startListening() {
        log.info("MetricLogWriter starting — subscribing to metric snapshots");

        subscription = metricRegistry.streamSnapshots()
                .subscribe(
                        this::writeSnapshot,
                        error -> log.error("Error in metric log writer stream", error)
                );

        logEnabledFormats();
    }

    /**
     * Writes a snapshot to all enabled format logs.
     */
    private void writeSnapshot(MetricSnapshot snapshot) {
        if (snapshot.metrics().isEmpty()) return;

        var formatConfig = properties.getExport().getFormats();

        // Prometheus
        if (formatConfig.getPrometheus().isEnabled()) {
            writeToLogger(prometheusLogger, "prometheus", snapshot);
        }

        // Zabbix
        if (formatConfig.getZabbix().isEnabled()) {
            writeToLogger(zabbixLogger, "zabbix", snapshot);
        }

        // Dynatrace
        if (formatConfig.getDynatrace().isEnabled()) {
            writeToLogger(dynatraceLogger, "dynatrace", snapshot);
        }
    }

    /**
     * Formats and writes metrics to the appropriate logger.
     */
    private void writeToLogger(Logger targetLogger, String formatName, MetricSnapshot snapshot) {
        MetricFormatter formatter = formatters.get(formatName);
        if (formatter == null) {
            log.warn("No formatter found for format '{}'", formatName);
            return;
        }

        try {
            String formatted = formatter.format(snapshot.metrics());
            if (formatted != null && !formatted.isBlank()) {
                targetLogger.info(formatted);
            }
        } catch (Exception e) {
            log.error("Failed to write {} metrics to log: {}", formatName, e.getMessage(), e);
        }
    }

    /**
     * Logs which formats are enabled at startup.
     */
    private void logEnabledFormats() {
        var formatConfig = properties.getExport().getFormats();
        log.info("Export formats — Prometheus: {}, Zabbix: {}, Dynatrace: {}",
                formatConfig.getPrometheus().isEnabled() ? "ENABLED" : "disabled",
                formatConfig.getZabbix().isEnabled() ? "ENABLED" : "disabled",
                formatConfig.getDynatrace().isEnabled() ? "ENABLED" : "disabled");
        log.info("Log directory: {}", properties.getExport().getLogDirectory());
    }

    /**
     * Cleanup: unsubscribe from the reactive stream on shutdown.
     */
    @PreDestroy
    public void shutdown() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("MetricLogWriter stopped");
        }
    }
}
