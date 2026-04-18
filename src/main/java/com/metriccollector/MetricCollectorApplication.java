package com.metriccollector;

import com.metriccollector.config.MetricCollectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MetricCollector Application — Collects JMX MBean metrics and exports them in
 * Prometheus, Zabbix, and Dynatrace formats via log files and secured REST/RSocket/TCP endpoints.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li>Scheduled JMX MBean metric collection (local + remote)</li>
 *   <li>Multi-format log file export (Prometheus, Zabbix, Dynatrace)</li>
 *   <li>Secured REST API with API-key authentication</li>
 *   <li>Secured RSocket endpoints with token-based SIMPLE auth</li>
 *   <li>Secured raw TCP socket for lightweight metric scraping</li>
 *   <li>Reactive architecture (Spring WebFlux + Project Reactor)</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MetricCollectorProperties.class)
public class MetricCollectorApplication {

    private static final Logger log = LoggerFactory.getLogger(MetricCollectorApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(MetricCollectorApplication.class, args);
        log.info("""
                
                ╔══════════════════════════════════════════════════════╗
                ║         MetricCollector Application Started         ║
                ║                                                      ║
                ║  REST API : http://localhost:8443/api/v1/metrics     ║
                ║  RSocket  : tcp://localhost:7000                     ║
                ║  TCP      : tcp://localhost:9090                     ║
                ║  Health   : http://localhost:8443/api/v1/metrics/health ║
                ╚══════════════════════════════════════════════════════╝
                """);
    }
}
