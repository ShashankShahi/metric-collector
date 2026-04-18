package com.metriccollector.collector;

import com.metriccollector.config.MetricCollectorProperties;
import com.metriccollector.model.CollectedMetric;
import com.metriccollector.model.MetricType;
import com.metriccollector.registry.MetricRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JmxCollectorServiceTest {

    private JmxCollectorService service;
    private MetricCollectorProperties properties;
    private JmxConnectionPool connectionPool;
    private MBeanAttributeMapper attributeMapper;
    private MetricRegistry metricRegistry;

    @BeforeEach
    void setUp() {
        properties = new MetricCollectorProperties();
        connectionPool = mock(JmxConnectionPool.class);
        attributeMapper = mock(MBeanAttributeMapper.class);
        metricRegistry = mock(MetricRegistry.class);

        service = new JmxCollectorService(properties, connectionPool, attributeMapper, metricRegistry);
    }

    @Test
    void testCollectMetrics() throws Exception {
        // Setup config
        MetricCollectorProperties.JmxTarget target = new MetricCollectorProperties.JmxTarget();
        target.setName("test-target");
        properties.getJmx().setTargets(List.of(target));
        properties.getJmx().setMbeans(List.of("java.lang:type=Memory"));

        // Setup Mocks
        MBeanServerConnection connection = mock(MBeanServerConnection.class);
        when(connectionPool.getConnection(target)).thenReturn(connection);

        ObjectName queryName = new ObjectName("java.lang:type=Memory");
        ObjectName resultName = new ObjectName("java.lang:type=Memory");
        when(connection.queryNames(queryName, null)).thenReturn(Set.of(resultName));

        CollectedMetric mockMetric = new CollectedMetric(
                "jvm_memory_used", 100.0, Map.of(), Instant.now(), MetricType.GAUGE
        );
        when(attributeMapper.mapAttributes(connection, resultName, "test-target"))
                .thenReturn(List.of(mockMetric));

        // Execute
        service.collectMetrics();

        // Verify
        ArgumentCaptor<com.metriccollector.model.MetricSnapshot> snapshotCaptor = 
                ArgumentCaptor.forClass(com.metriccollector.model.MetricSnapshot.class);
        verify(metricRegistry).updateSnapshot(snapshotCaptor.capture());

        com.metriccollector.model.MetricSnapshot snapshot = snapshotCaptor.getValue();
        assertEquals(1, snapshot.size());
        assertEquals("jvm_memory_used", snapshot.metrics().get(0).name());
    }

    @Test
    void testCollectMetricsConnectionFailure() throws Exception {
        MetricCollectorProperties.JmxTarget target = new MetricCollectorProperties.JmxTarget();
        target.setName("failed-target");
        properties.getJmx().setTargets(List.of(target));

        when(connectionPool.getConnection(target)).thenThrow(new java.io.IOException("Connection refused"));

        // Should not throw exception, should just log and continue
        service.collectMetrics();

        // Snapshot should NOT be updated because of connection failure
        verify(metricRegistry, never()).updateSnapshot(any());
    }

    @Test
    void testCollectMetricsWithAuthTarget() throws Exception {
        MetricCollectorProperties.JmxTarget target = new MetricCollectorProperties.JmxTarget();
        target.setName("auth-target");
        target.setUsername("admin");
        target.setPassword("secret");
        properties.getJmx().setTargets(List.of(target));
        
        when(connectionPool.getConnection(target)).thenThrow(new java.io.IOException("Connection refused"));
        service.collectMetrics();
        verify(metricRegistry, never()).updateSnapshot(any());
    }

    @Test
    void testCollectFromTargetException() throws Exception {
        MetricCollectorProperties.JmxTarget target = new MetricCollectorProperties.JmxTarget();
        target.setName("local-error");
        properties.getJmx().setTargets(List.of(target));
        properties.getJmx().setMbeans(List.of("invalid-pattern-*"));

        javax.management.MBeanServerConnection connection = mock(javax.management.MBeanServerConnection.class);
        when(connectionPool.getConnection(target)).thenReturn(connection);
        when(connection.queryNames(any(), any())).thenThrow(new RuntimeException("Query error"));

        service.collectMetrics();
        verify(metricRegistry, never()).updateSnapshot(any());
    }

    @Test
    void testShutdown() {
        service.shutdown();
        verify(connectionPool).closeAll();
    }
}
