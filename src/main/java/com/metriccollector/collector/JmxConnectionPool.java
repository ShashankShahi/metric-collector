package com.metriccollector.collector;

import com.metriccollector.config.MetricCollectorProperties.JmxTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages JMX connections to local and remote MBean servers.
 * <p>
 * For local targets, returns the platform MBeanServer directly.
 * For remote targets, maintains a connection cache with automatic reconnection on failure.
 */
@Component
public class JmxConnectionPool {

    private static final Logger log = LoggerFactory.getLogger(JmxConnectionPool.class);

    private final Map<String, JMXConnector> connectorCache = new ConcurrentHashMap<>();

    /**
     * Returns an {@link MBeanServerConnection} for the given JMX target.
     *
     * @param target the configured JMX target
     * @return a live MBeanServerConnection
     * @throws IOException if the connection cannot be established
     */
    public MBeanServerConnection getConnection(JmxTarget target) throws IOException {
        if (target.isLocal()) {
            return ManagementFactory.getPlatformMBeanServer();
        }
        return getRemoteConnection(target);
    }

    /**
     * Gets or creates a remote JMX connection, reconnecting if the cached connection is dead.
     */
    private synchronized MBeanServerConnection getRemoteConnection(JmxTarget target) throws IOException {
        String key = target.getName();

        // Check if we have a cached connector that's still alive
        JMXConnector existing = connectorCache.get(key);
        if (existing != null) {
            try {
                // Test the connection
                existing.getConnectionId();
                return existing.getMBeanServerConnection();
            } catch (IOException e) {
                log.warn("Cached JMX connection '{}' is dead, reconnecting...", key);
                closeQuietly(existing);
                connectorCache.remove(key);
            }
        }

        // Create new connection
        log.info("Establishing JMX connection to target '{}' at {}", key, target.getUrl());
        JMXServiceURL serviceUrl = new JMXServiceURL(target.getUrl());

        Map<String, Object> env = null;
        if (target.getUsername() != null && target.getPassword() != null) {
            env = Map.of(
                    JMXConnector.CREDENTIALS,
                    new String[]{target.getUsername(), target.getPassword()}
            );
        }

        JMXConnector connector = JMXConnectorFactory.connect(serviceUrl, env);
        connectorCache.put(key, connector);
        log.info("JMX connection established to target '{}'", key);

        return connector.getMBeanServerConnection();
    }

    /**
     * Closes all cached connections. Called on application shutdown.
     */
    public void closeAll() {
        connectorCache.forEach((name, connector) -> {
            log.info("Closing JMX connection to '{}'", name);
            closeQuietly(connector);
        });
        connectorCache.clear();
    }

    private void closeQuietly(JMXConnector connector) {
        try {
            if (connector != null) {
                connector.close();
            }
        } catch (IOException e) {
            log.debug("Error closing JMX connector: {}", e.getMessage());
        }
    }
}
