package com.metriccollector.collector;

import com.metriccollector.model.CollectedMetric;
import com.metriccollector.model.MetricType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.management.*;
import javax.management.openmbean.*;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MBeanAttributeMapperTest {

    private MBeanAttributeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new MBeanAttributeMapper();
    }

    @Test
    void testBuildMetricName() {
        assertEquals("jvm_memory_used", mapper.buildMetricName("java.lang", "Memory", null, "used"));
        assertEquals("kafka_server_replicamanager_underreplicatedpartitions", 
            mapper.buildMetricName("kafka.server", "ReplicaManager", null, "UnderReplicatedPartitions"));
        assertEquals("jvm_garbagecollector_g1_old_generation_collectioncount", 
            mapper.buildMetricName("java.lang", "GarbageCollector", "G1 Old Generation", "CollectionCount"));
    }

    @Test
    void testMapSimpleNumericAttribute() throws Exception {
        MBeanServerConnection connection = mock(MBeanServerConnection.class);
        ObjectName objName = new ObjectName("java.lang:type=OperatingSystem");

        MBeanAttributeInfo attrInfo = new MBeanAttributeInfo(
                "ProcessCpuLoad", "double", "desc", true, false, false);
        MBeanInfo mBeanInfo = new MBeanInfo("className", "desc", new MBeanAttributeInfo[]{attrInfo}, null, null, null);

        when(connection.getMBeanInfo(objName)).thenReturn(mBeanInfo);
        when(connection.getAttribute(objName, "ProcessCpuLoad")).thenReturn(0.75);

        List<CollectedMetric> metrics = mapper.mapAttributes(connection, objName, "local");

        assertEquals(1, metrics.size());
        CollectedMetric metric = metrics.get(0);
        assertEquals("jvm_operatingsystem_processcpuload", metric.name());
        assertEquals(0.75, metric.value());
        assertEquals("local", metric.labels().get("source"));
        assertEquals(MetricType.GAUGE, metric.type());
    }

    @Test
    void testMapCounterAttribute() throws Exception {
        MBeanServerConnection connection = mock(MBeanServerConnection.class);
        ObjectName objName = new ObjectName("java.lang:type=GarbageCollector,name=G1");

        MBeanAttributeInfo attrInfo = new MBeanAttributeInfo(
                "CollectionCount", "long", "desc", true, false, false);
        MBeanInfo mBeanInfo = new MBeanInfo("className", "desc", new MBeanAttributeInfo[]{attrInfo}, null, null, null);

        when(connection.getMBeanInfo(objName)).thenReturn(mBeanInfo);
        when(connection.getAttribute(objName, "CollectionCount")).thenReturn(42L);

        List<CollectedMetric> metrics = mapper.mapAttributes(connection, objName, "local");

        assertEquals(1, metrics.size());
        CollectedMetric metric = metrics.get(0);
        assertEquals("jvm_garbagecollector_g1_collectioncount", metric.name());
        assertEquals(42.0, metric.value());
        assertEquals(MetricType.COUNTER, metric.type()); // inferred from 'count'
    }

    @Test
    void testMapCompositeData() throws Exception {
        MBeanServerConnection connection = mock(MBeanServerConnection.class);
        ObjectName objName = new ObjectName("java.lang:type=Memory");

        MBeanAttributeInfo attrInfo = new MBeanAttributeInfo(
                "HeapMemoryUsage", "javax.management.openmbean.CompositeData", "desc", true, false, false);
        MBeanInfo mBeanInfo = new MBeanInfo("className", "desc", new MBeanAttributeInfo[]{attrInfo}, null, null, null);

        // Mock CompositeData
        CompositeType type = new CompositeType("MemoryUsage", "desc",
                new String[]{"used", "max"}, new String[]{"used desc", "max desc"},
                new OpenType[]{SimpleType.LONG, SimpleType.LONG});
        CompositeDataSupport data = new CompositeDataSupport(type,
                new String[]{"used", "max"}, new Object[]{1024L, 2048L});

        when(connection.getMBeanInfo(objName)).thenReturn(mBeanInfo);
        when(connection.getAttribute(objName, "HeapMemoryUsage")).thenReturn(data);

        List<CollectedMetric> metrics = mapper.mapAttributes(connection, objName, "local");

        assertEquals(2, metrics.size());
        boolean foundUsed = false, foundMax = false;
        for (CollectedMetric m : metrics) {
            if ("jvm_memory_heapmemoryusage_used".equals(m.name())) {
                assertEquals(1024.0, m.value());
                foundUsed = true;
            } else if ("jvm_memory_heapmemoryusage_max".equals(m.name())) {
                assertEquals(2048.0, m.value());
                foundMax = true;
            }
        }
        assertTrue(foundUsed && foundMax);
    }

    @Test
    void testMapAttributeWithException() throws Exception {
        MBeanServerConnection connection = mock(MBeanServerConnection.class);
        ObjectName objName = new ObjectName("java.lang:type=Error");

        MBeanAttributeInfo attrInfo = new MBeanAttributeInfo(
                "ErrorAttr", "double", "desc", true, false, false);
        MBeanInfo mBeanInfo = new MBeanInfo("className", "desc", new MBeanAttributeInfo[]{attrInfo}, null, null, null);

        when(connection.getMBeanInfo(objName)).thenReturn(mBeanInfo);
        when(connection.getAttribute(objName, "ErrorAttr")).thenThrow(new RuntimeException("Simulated error"));

        List<CollectedMetric> metrics = mapper.mapAttributes(connection, objName, "local");
        assertTrue(metrics.isEmpty());
    }
}
