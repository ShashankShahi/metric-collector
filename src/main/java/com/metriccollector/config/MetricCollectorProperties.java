package com.metriccollector.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Type-safe configuration properties for MetricCollector,
 * bound from {@code metric-collector.*} in application.yml.
 */
@Validated
@ConfigurationProperties(prefix = "metric-collector")
public class MetricCollectorProperties {

    @Valid
    private Jmx jmx = new Jmx();

    @Valid
    private Export export = new Export();

    @Valid
    private Security security = new Security();

    @Valid
    private Tcp tcp = new Tcp();

    // ---- Getters / Setters ----

    public Jmx getJmx() { return jmx; }
    public void setJmx(Jmx jmx) { this.jmx = jmx; }
    public Export getExport() { return export; }
    public void setExport(Export export) { this.export = export; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public Tcp getTcp() { return tcp; }
    public void setTcp(Tcp tcp) { this.tcp = tcp; }

    // ======== Nested Classes ========

    /**
     * JMX connection and collection configuration.
     */
    public static class Jmx {

        @NotEmpty
        private List<@Valid JmxTarget> targets = List.of();

        @NotEmpty
        private List<String> mbeans = List.of();

        @Positive
        private long collectionIntervalMs = 15000;

        public List<JmxTarget> getTargets() { return targets; }
        public void setTargets(List<JmxTarget> targets) { this.targets = targets; }
        public List<String> getMbeans() { return mbeans; }
        public void setMbeans(List<String> mbeans) { this.mbeans = mbeans; }
        public long getCollectionIntervalMs() { return collectionIntervalMs; }
        public void setCollectionIntervalMs(long collectionIntervalMs) { this.collectionIntervalMs = collectionIntervalMs; }
    }

    /**
     * A single JMX target (local or remote).
     */
    public static class JmxTarget {

        @NotBlank
        private String name;

        @NotBlank
        private String url = "local";

        private String username;
        private String password;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        /**
         * Returns true if this target uses the local platform MBeanServer.
         */
        public boolean isLocal() {
            return "local".equalsIgnoreCase(url);
        }
    }

    /**
     * Export / log-file configuration.
     */
    public static class Export {

        private String logDirectory = "./logs/metrics";

        @Valid
        private Formats formats = new Formats();

        public String getLogDirectory() { return logDirectory; }
        public void setLogDirectory(String logDirectory) { this.logDirectory = logDirectory; }
        public Formats getFormats() { return formats; }
        public void setFormats(Formats formats) { this.formats = formats; }
    }

    /**
     * Per-format export settings.
     */
    public static class Formats {
        @Valid private FormatConfig prometheus = new FormatConfig();
        @Valid private FormatConfig zabbix = new FormatConfig();
        @Valid private FormatConfig dynatrace = new FormatConfig();

        public FormatConfig getPrometheus() { return prometheus; }
        public void setPrometheus(FormatConfig prometheus) { this.prometheus = prometheus; }
        public FormatConfig getZabbix() { return zabbix; }
        public void setZabbix(FormatConfig zabbix) { this.zabbix = zabbix; }
        public FormatConfig getDynatrace() { return dynatrace; }
        public void setDynatrace(FormatConfig dynatrace) { this.dynatrace = dynatrace; }
    }

    /**
     * Configuration for a single export format.
     */
    public static class FormatConfig {
        private boolean enabled = true;
        private String file;
        private String hostname = "metric-collector-host";
        private String prefix = "custom.jmx";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getFile() { return file; }
        public void setFile(String file) { this.file = file; }
        public String getHostname() { return hostname; }
        public void setHostname(String hostname) { this.hostname = hostname; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
    }

    /**
     * Security settings for REST, RSocket, and TCP endpoints.
     */
    public static class Security {

        @NotBlank
        private String apiKey = "changeme";

        @NotBlank
        private String rsocketToken = "changeme";

        @NotBlank
        private String tcpToken = "changeme";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getRsocketToken() { return rsocketToken; }
        public void setRsocketToken(String rsocketToken) { this.rsocketToken = rsocketToken; }
        public String getTcpToken() { return tcpToken; }
        public void setTcpToken(String tcpToken) { this.tcpToken = tcpToken; }
    }

    /**
     * TCP server configuration for raw TCP metric exposure.
     */
    public static class Tcp {

        /** Whether the TCP server is enabled. */
        private boolean enabled = true;

        /** Host/IP to bind the TCP server to. */
        @NotBlank
        private String host = "0.0.0.0";

        /** Port to bind the TCP server to. */
        @Positive
        private int port = 9090;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
    }
}
