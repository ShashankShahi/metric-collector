package com.metriccollector.api.rsocket;

import com.metriccollector.model.MetricSnapshot;
import com.metriccollector.registry.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * RSocket controller for metric scraping.
 * <p>
 * Supports two interaction models:
 * <ul>
 *   <li><b>Request-Response</b> ({@code metrics.snapshot}): Returns the latest snapshot</li>
 *   <li><b>Request-Stream</b> ({@code metrics.stream}): Streams snapshots as they are collected</li>
 * </ul>
 * <p>
 * All routes require authentication via the RSocket SETUP frame token.
 */
@Controller
public class MetricsRSocketController {

    private static final Logger log = LoggerFactory.getLogger(MetricsRSocketController.class);

    private final MetricRegistry metricRegistry;

    public MetricsRSocketController(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    /**
     * Request-Response: returns the current metric snapshot.
     * <p>
     * RSocket route: {@code metrics.snapshot}
     *
     * @return the latest MetricSnapshot
     */
    @MessageMapping("metrics.snapshot")
    public Mono<MetricSnapshot> getSnapshot() {
        log.debug("RSocket request: metrics.snapshot");
        return Mono.just(metricRegistry.getSnapshot());
    }

    /**
     * Request-Stream: streams metric snapshots as they are collected.
     * <p>
     * RSocket route: {@code metrics.stream}
     * <p>
     * The client will receive a new snapshot each time the JMX collector
     * completes a collection cycle.
     *
     * @return Flux of MetricSnapshot
     */
    @MessageMapping("metrics.stream")
    public Flux<MetricSnapshot> streamMetrics() {
        log.info("RSocket client subscribed to metrics.stream");
        return metricRegistry.streamSnapshots()
                .doOnCancel(() -> log.info("RSocket client unsubscribed from metrics.stream"))
                .doOnError(e -> log.error("Error in metrics.stream", e));
    }

    /**
     * Fire-and-Forget: ping endpoint for connection testing.
     * <p>
     * RSocket route: {@code metrics.ping}
     */
    @MessageMapping("metrics.ping")
    public Mono<Void> ping() {
        log.debug("RSocket ping received");
        return Mono.empty();
    }
}
