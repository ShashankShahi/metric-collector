package com.metriccollector.api.rest;

import com.metriccollector.formatter.MetricFormatter;
import com.metriccollector.model.MetricSnapshot;
import com.metriccollector.registry.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * REST controller exposing metrics in multiple formats.
 * <p>
 * All endpoints under {@code /api/v1/metrics/**} require API-key authentication,
 * except for the health endpoint.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>{@code GET /api/v1/metrics?format=json|prometheus|zabbix|dynatrace} — Current metrics</li>
 *   <li>{@code GET /api/v1/metrics/health} — Health check (unauthenticated)</li>
 *   <li>{@code GET /api/v1/metrics/formats} — List available formats</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsRestController {

    private static final Logger log = LoggerFactory.getLogger(MetricsRestController.class);

    private final MetricRegistry metricRegistry;
    private final Map<String, MetricFormatter> formatters;

    public MetricsRestController(MetricRegistry metricRegistry,
                                  List<MetricFormatter> formatterList) {
        this.metricRegistry = metricRegistry;
        this.formatters = formatterList.stream()
                .collect(Collectors.toMap(MetricFormatter::formatName, Function.identity()));
    }

    /**
     * Returns metrics in the requested format.
     *
     * @param format one of: json (default), prometheus, zabbix, dynatrace
     * @return formatted metrics
     */
    @GetMapping
    public Mono<ResponseEntity<Object>> getMetrics(
            @RequestParam(value = "format", defaultValue = "json") String format) {

        MetricSnapshot snapshot = metricRegistry.getSnapshot();

        if ("json".equalsIgnoreCase(format)) {
            return Mono.just(ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(snapshot));
        }

        MetricFormatter formatter = formatters.get(format.toLowerCase());
        if (formatter == null) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "Unknown format: " + format,
                            "availableFormats", List.of("json", "prometheus", "zabbix", "dynatrace")
                    )));
        }

        String formatted = formatter.format(snapshot.metrics());
        return Mono.just(ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(formatter.contentType()))
                .body(formatted));
    }

    /**
     * Health check endpoint — unauthenticated.
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        MetricSnapshot snapshot = metricRegistry.getSnapshot();
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "UP",
                "metricCount", snapshot.size(),
                "lastCollectedAt", snapshot.collectedAt().toString(),
                "lastCollectionDurationMs", snapshot.collectionDurationMs()
        )));
    }

    /**
     * Lists available export formats.
     */
    @GetMapping("/formats")
    public Mono<ResponseEntity<Map<String, Object>>> listFormats() {
        List<Map<String, String>> formatList = formatters.values().stream()
                .map(f -> Map.of(
                        "name", f.formatName(),
                        "contentType", f.contentType()
                ))
                .collect(Collectors.toList());

        // Add JSON as a built-in format
        formatList.add(Map.of("name", "json", "contentType", "application/json"));

        return Mono.just(ResponseEntity.ok(Map.of(
                "formats", formatList
        )));
    }
}
