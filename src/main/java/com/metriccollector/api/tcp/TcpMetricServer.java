package com.metriccollector.api.tcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metriccollector.config.MetricCollectorProperties;
import com.metriccollector.formatter.MetricFormatter;
import com.metriccollector.model.MetricSnapshot;
import com.metriccollector.registry.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.tcp.TcpServer;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Raw TCP server for exposing metrics over a plain TCP socket.
 * <p>
 * This provides a lightweight, infrastructure-friendly protocol for metric scraping
 * that works in environments where HTTP/RSocket may not be available or desired
 * (e.g. embedded devices, legacy monitoring agents, firewall-restricted networks).
 *
 * <h3>Protocol</h3>
 * The protocol is newline-delimited text:
 * <ol>
 *   <li>Client connects and sends: {@code AUTH <token>\n}</li>
 *   <li>Server responds: {@code OK Authenticated\n} or {@code ERR Invalid token\n}</li>
 *   <li>Client sends one of:
 *     <ul>
 *       <li>{@code SNAPSHOT\n} — Server responds with the current snapshot as JSON, terminated by {@code \n---END---\n}</li>
 *       <li>{@code SNAPSHOT <format>\n} — Server responds with formatted metrics (prometheus, zabbix, dynatrace), terminated by {@code \n---END---\n}</li>
 *       <li>{@code STREAM\n} — Server pushes JSON snapshots continuously, each terminated by {@code \n---END---\n}</li>
 *       <li>{@code STREAM <format>\n} — Server pushes formatted snapshots continuously</li>
 *       <li>{@code PING\n} — Server responds with {@code PONG\n}</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * @see MetricCollectorProperties.Tcp
 */
@Component
@ConditionalOnProperty(name = "metric-collector.tcp.enabled", havingValue = "true", matchIfMissing = true)
public class TcpMetricServer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TcpMetricServer.class);

    private static final String END_MARKER = "\n---END---\n";
    private static final String CRLF = "\n";

    private final MetricRegistry metricRegistry;
    private final MetricCollectorProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, MetricFormatter> formatters;

    private DisposableServer server;

    public TcpMetricServer(MetricRegistry metricRegistry,
                           MetricCollectorProperties properties,
                           ObjectMapper objectMapper,
                           List<MetricFormatter> formatterList) {
        this.metricRegistry = metricRegistry;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.formatters = formatterList.stream()
                .collect(Collectors.toMap(MetricFormatter::formatName, Function.identity()));
    }

    @Override
    public void run(String... args) {
        MetricCollectorProperties.Tcp tcpConfig = properties.getTcp();

        server = TcpServer.create()
                .host(tcpConfig.getHost())
                .port(tcpConfig.getPort())
                .handle((inbound, outbound) -> {
                    // Track authentication state per connection
                    final boolean[] authenticated = {false};

                    return inbound.receive()
                            .asString(StandardCharsets.UTF_8)
                            .flatMap(rawMessage -> {
                                // Messages may be batched; split on newlines
                                String message = rawMessage.trim();
                                if (message.isEmpty()) {
                                    return Mono.empty();
                                }

                                log.debug("TCP received: {}", message);

                                // --- AUTH ---
                                if (message.toUpperCase().startsWith("AUTH ")) {
                                    String token = message.substring(5).trim();
                                    if (properties.getSecurity().getTcpToken().equals(token)) {
                                        authenticated[0] = true;
                                        log.debug("TCP client authenticated");
                                        return outbound.sendString(Mono.just("OK Authenticated" + CRLF))
                                                .then();
                                    } else {
                                        log.warn("TCP authentication failed — invalid token");
                                        return outbound.sendString(Mono.just("ERR Invalid token" + CRLF))
                                                .then(Mono.fromRunnable(() -> {}));
                                    }
                                }

                                // --- Require auth for all other commands ---
                                if (!authenticated[0]) {
                                    return outbound.sendString(
                                            Mono.just("ERR Authentication required. Send: AUTH <token>" + CRLF))
                                            .then();
                                }

                                // --- PING ---
                                if ("PING".equalsIgnoreCase(message)) {
                                    return outbound.sendString(Mono.just("PONG" + CRLF)).then();
                                }

                                // --- SNAPSHOT ---
                                if (message.toUpperCase().startsWith("SNAPSHOT")) {
                                    String format = extractFormat(message, "SNAPSHOT");
                                    return handleSnapshot(outbound, format);
                                }

                                // --- STREAM ---
                                if (message.toUpperCase().startsWith("STREAM")) {
                                    String format = extractFormat(message, "STREAM");
                                    return handleStream(outbound, format);
                                }

                                // --- Unknown ---
                                return outbound.sendString(
                                        Mono.just("ERR Unknown command. Available: AUTH, SNAPSHOT, STREAM, PING" + CRLF))
                                        .then();
                            })
                            .then();
                })
                .bindNow();

        log.info("TCP Metric Server started on {}:{}", tcpConfig.getHost(), server.port());
    }

    /**
     * Extracts the optional format parameter from a command string.
     * E.g., "SNAPSHOT prometheus" → "prometheus", "SNAPSHOT" → "json"
     */
    private String extractFormat(String message, String command) {
        String remainder = message.substring(command.length()).trim();
        return remainder.isEmpty() ? "json" : remainder.toLowerCase();
    }

    /**
     * Handles a one-shot SNAPSHOT request.
     */
    private Mono<Void> handleSnapshot(reactor.netty.NettyOutbound outbound, String format) {
        return Mono.fromCallable(() -> formatSnapshot(metricRegistry.getSnapshot(), format))
                .flatMap(data -> outbound.sendString(Mono.just(data + END_MARKER)).then());
    }

    /**
     * Handles a continuous STREAM request, pushing snapshots as they arrive.
     */
    private Mono<Void> handleStream(reactor.netty.NettyOutbound outbound, String format) {
        Flux<String> snapshotStream = metricRegistry.streamSnapshots()
                .map(snapshot -> formatSnapshot(snapshot, format) + END_MARKER);

        return outbound.sendString(snapshotStream).then();
    }

    /**
     * Formats a snapshot into the requested format string.
     */
    private String formatSnapshot(MetricSnapshot snapshot, String format) {
        if ("json".equalsIgnoreCase(format)) {
            try {
                return objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(snapshot);
            } catch (Exception e) {
                log.error("Failed to serialize snapshot to JSON", e);
                return "ERR Serialization failed";
            }
        }

        MetricFormatter formatter = formatters.get(format);
        if (formatter == null) {
            return "ERR Unknown format: " + format + ". Available: json, "
                    + String.join(", ", formatters.keySet());
        }
        return formatter.format(snapshot.metrics());
    }

    @PreDestroy
    public void stop() {
        if (server != null && !server.isDisposed()) {
            log.info("Stopping TCP Metric Server...");
            server.disposeNow(Duration.ofSeconds(5));
        }
    }
}
