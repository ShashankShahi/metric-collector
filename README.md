# MetricCollector

MetricCollector is a lightweight, reactive Spring Boot application designed to scrape JMX MBean metrics from Java applications and expose them in various monitoring formats. It supports both local and remote JMX targets and provides multiple ways to consume the metrics: Log Files, REST API, RSocket, and raw TCP.

## Features

- **JMX Metric Scraping**: Collect metrics from the local platform MBeanServer or remote JVMs via JMX RMI.
- **Multiple Output Formats**: Built-in support for **JSON**, **Prometheus**, **Zabbix**, and **Dynatrace**.
- **Log File Export**: Periodically writes formatted metrics to dedicated log files (useful for log-forwarding agents like Filebeat or Fluentd).
- **REST API**: WebFlux-based HTTP endpoints to fetch metrics on demand.
- **RSocket API**: Supports both request-response and continuous streaming of metrics.
- **TCP Socket**: A raw TCP server for lightweight scraping and streaming without HTTP overhead.
- **Security**: Built-in authentication for all network endpoints (API Keys, Tokens).

---

## Configuration Options

The application is configured via `application.yml` (or `application.properties`) under the `metric-collector.*` prefix.

### 1. JMX Configuration (`metric-collector.jmx.*`)
Configures what to scrape and how often.

```yaml
metric-collector:
  jmx:
    collection-interval-ms: 15000 # How often to scrape metrics (in milliseconds)
    targets:
      - name: local
        url: local    # Use "local" for the current JVM, or a JMX URL for remote
        # username: admin   # Optional: For remote JMX auth
        # password: secret  # Optional: For remote JMX auth
    mbeans:
      # List of ObjectNames to query (supports wildcards)
      - "java.lang:type=Memory"
      - "java.lang:type=Threading"
      - "java.lang:type=GarbageCollector,*"
```

### 2. File Export Configuration (`metric-collector.export.*`)
Configures writing metrics to disk.

```yaml
metric-collector:
  export:
    log-directory: ./logs/metrics  # Directory where files will be saved
    formats:
      prometheus:
        enabled: true
        file: prometheus-metrics.log
      zabbix:
        enabled: true
        file: zabbix-metrics.log
        hostname: my-app-host # Sent as the Zabbix host
      dynatrace:
        enabled: true
        file: dynatrace-metrics.log
        prefix: "custom.jmx"
```

### 3. TCP Server Configuration (`metric-collector.tcp.*`)
Configures the raw TCP socket server.

```yaml
metric-collector:
  tcp:
    enabled: true
    host: 0.0.0.0
    port: 9090
```

### 4. Security Configuration (`metric-collector.security.*`)
Configures authentication tokens for the various endpoints.

```yaml
metric-collector:
  security:
    api-key: "mc-api-key-change-me-in-production"          # For REST API
    rsocket-token: "mc-rsocket-token-change-me-in-production" # For RSocket
    tcp-token: "mc-tcp-token-change-me-in-production"         # For TCP
```

---

## How to Consume Metrics

### 1. REST API
The REST API runs on port `8443` by default and requires the `X-API-Key` header or a Bearer token.

**Endpoints:**
- `GET /api/v1/metrics/health` (Unauthenticated) - Check application health.
- `GET /api/v1/metrics` - Get current metrics (defaults to JSON).
- `GET /api/v1/metrics?format=prometheus` - Get metrics in Prometheus format.
- `GET /api/v1/metrics?format=zabbix` - Get metrics in Zabbix format.
- `GET /api/v1/metrics?format=dynatrace` - Get metrics in Dynatrace format.

**Example (cURL):**
```bash
curl -H "X-API-Key: mc-api-key-change-me-in-production" \
     http://localhost:8443/api/v1/metrics?format=prometheus
```

### 2. TCP Socket
The TCP server runs on port `9090` by default. It uses a simple newline-delimited text protocol.
You must authenticate first by sending `AUTH <token>\n`.

**Available Commands (after AUTH):**
- `SNAPSHOT` : Returns the latest metrics as JSON.
- `SNAPSHOT <format>` : Returns metrics in the requested format (e.g., `SNAPSHOT prometheus`).
- `STREAM` : Pushes a JSON snapshot continuously every collection cycle.
- `STREAM <format>` : Pushes formatted snapshots continuously.
- `PING` : Responds with `PONG`.

**Example (using `nc` / netcat):**
```bash
$ nc localhost 9090
AUTH mc-tcp-token-change-me-in-production
OK Authenticated
SNAPSHOT prometheus
# HELP java_lang_Memory_HeapMemoryUsage_used ...
...
---END---
```

### 3. RSocket
RSocket runs on TCP port `7001` (by default configured in `spring.rsocket.server.port`). 
It requires SIMPLE authentication using the configured `rsocket-token` in the setup payload.

**Routes:**
- `metrics.snapshot` (Request-Response): Returns a single `MetricSnapshot` object.
- `metrics.stream` (Request-Stream): Returns a Flux stream of `MetricSnapshot` objects as they are collected.
- `metrics.ping` (Fire-and-Forget): Keep-alive ping.

### 4. Log Files
If enabled, the application will automatically write metrics to the configured `log-directory` (default: `./logs/metrics/`). 
- `prometheus-metrics.log`
- `zabbix-metrics.log`
- `dynatrace-metrics.log`

These files are overwritten periodically with the latest snapshot, making them ideal for tools like Promtail or custom scripts that read local files.

---

## Building and Running

This project uses Gradle. To build the executable JAR:

```bash
./gradlew build
```

To run the application locally:

```bash
./gradlew bootRun
```
*(Or run the generated jar: `java -jar build/libs/metric-collector-1.0.0-SNAPSHOT.jar`)*

---

## Testing & Code Coverage

The project is configured with a strict testing requirement using the **JaCoCo** plugin.
- The build pipeline enforces a **minimum 95% instruction and branch coverage**.
- If test coverage falls below this threshold, the build will fail.

To run tests and generate a coverage report:
```bash
./gradlew check jacocoTestReport
```

The interactive HTML coverage report will be available at:
`build/reports/jacoco/test/html/index.html`
