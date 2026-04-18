package com.metriccollector.collector;

import com.metriccollector.config.MetricCollectorProperties.JmxTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServerConnection;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class JmxConnectionPoolTest {

    private JmxConnectionPool pool;

    @BeforeEach
    void setUp() {
        pool = new JmxConnectionPool();
    }

    @AfterEach
    void tearDown() {
        pool.closeAll();
    }

    @Test
    void testGetLocalConnection() throws IOException {
        JmxTarget target = new JmxTarget();
        target.setName("local-test");
        target.setUrl("local");

        MBeanServerConnection connection = pool.getConnection(target);
        assertNotNull(connection);
        assertTrue(connection.getMBeanCount() > 0);
    }

    @Test
    void testGetPlatformMBeanServer() throws Exception {
        JmxTarget local = new JmxTarget();
        local.setName("local");
        
        MBeanServerConnection conn = pool.getConnection(local);
        assertNotNull(conn);
        assertTrue(conn instanceof javax.management.MBeanServer);
    }

    @Test
    void testCloseAll() throws Exception {
        // Need reflection to inject a mock JMXConnector
        pool.closeAll(); // Should complete without error even if empty
    }

    @Test
    void testGetRemoteConnectionFailure() {
        JmxTarget target = new JmxTarget();
        target.setName("remote-test");
        target.setUrl("service:jmx:rmi:///jndi/rmi://invalid-host:9999/jmxrmi");
        target.setUsername("user");
        target.setPassword("pass");

        assertThrows(IOException.class, () -> pool.getConnection(target));
    }

    @Test
    void testRemoteConnectionSuccess() throws Exception {
        // Start a real local JMX server to test the remote connection logic
        java.rmi.registry.LocateRegistry.createRegistry(11099);
        javax.management.MBeanServer mbs = java.lang.management.ManagementFactory.getPlatformMBeanServer();
        javax.management.remote.JMXServiceURL url = new javax.management.remote.JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:11099/jmxrmi");
        javax.management.remote.JMXConnectorServer cs = javax.management.remote.JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbs);
        cs.start();

        try {
            JmxTarget target = new JmxTarget();
            target.setName("remote-success");
            target.setUrl(url.toString());

            // 1. Initial connection
            javax.management.MBeanServerConnection conn1 = pool.getConnection(target);
            assertNotNull(conn1);

            // 2. Cached connection
            javax.management.MBeanServerConnection conn2 = pool.getConnection(target);
            assertEquals(conn1, conn2);

            // 3. Close the pool
            pool.closeAll();
        } finally {
            cs.stop();
        }
    }
}
