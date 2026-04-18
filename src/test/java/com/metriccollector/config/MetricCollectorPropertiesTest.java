package com.metriccollector.config;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MetricCollectorPropertiesTest {

    @Test
    void testDefaultValuesAndSetters() {
        MetricCollectorProperties props = new MetricCollectorProperties();
        
        // JMX Defaults
        assertNotNull(props.getJmx());
        assertEquals(15000, props.getJmx().getCollectionIntervalMs());
        assertTrue(props.getJmx().getTargets().isEmpty());
        assertTrue(props.getJmx().getMbeans().isEmpty());
        
        // Export Defaults
        assertNotNull(props.getExport());
        assertEquals("./logs/metrics", props.getExport().getLogDirectory());
        assertNotNull(props.getExport().getFormats());
        
        // Formats Defaults
        MetricCollectorProperties.Formats formats = props.getExport().getFormats();
        assertTrue(formats.getPrometheus().isEnabled());
        assertTrue(formats.getZabbix().isEnabled());
        assertTrue(formats.getDynatrace().isEnabled());
        
        // Security Defaults
        assertNotNull(props.getSecurity());
        assertEquals("changeme", props.getSecurity().getApiKey());
        assertEquals("changeme", props.getSecurity().getRsocketToken());
        assertEquals("changeme", props.getSecurity().getTcpToken());
        
        // TCP Defaults
        assertNotNull(props.getTcp());
        assertTrue(props.getTcp().isEnabled());
        assertEquals("0.0.0.0", props.getTcp().getHost());
        assertEquals(9090, props.getTcp().getPort());

        // Test Setters
        MetricCollectorProperties.Jmx jmx = new MetricCollectorProperties.Jmx();
        jmx.setCollectionIntervalMs(5000);
        jmx.setMbeans(List.of("java.lang:*"));
        
        MetricCollectorProperties.JmxTarget target = new MetricCollectorProperties.JmxTarget();
        target.setName("remote");
        target.setUrl("service:jmx:rmi...");
        target.setUsername("user");
        target.setPassword("pass");
        jmx.setTargets(List.of(target));
        props.setJmx(jmx);

        assertEquals(5000, props.getJmx().getCollectionIntervalMs());
        assertEquals("remote", props.getJmx().getTargets().get(0).getName());
        assertEquals("user", props.getJmx().getTargets().get(0).getUsername());
        assertEquals("pass", props.getJmx().getTargets().get(0).getPassword());
        assertEquals("service:jmx:rmi...", props.getJmx().getTargets().get(0).getUrl());
        assertFalse(props.getJmx().getTargets().get(0).isLocal());

        MetricCollectorProperties.JmxTarget localTarget = new MetricCollectorProperties.JmxTarget();
        localTarget.setName("local");
        localTarget.setUrl("LOCAL"); // test case insensitive local check
        assertTrue(localTarget.isLocal());

        MetricCollectorProperties.Export export = new MetricCollectorProperties.Export();
        export.setLogDirectory("/tmp");
        props.setExport(export);
        assertEquals("/tmp", props.getExport().getLogDirectory());

        MetricCollectorProperties.FormatConfig formatConfig = new MetricCollectorProperties.FormatConfig();
        formatConfig.setEnabled(false);
        formatConfig.setFile("test.log");
        formatConfig.setHostname("test-host");
        formatConfig.setPrefix("test-prefix");
        
        MetricCollectorProperties.Formats newFormats = new MetricCollectorProperties.Formats();
        newFormats.setPrometheus(formatConfig);
        newFormats.setZabbix(formatConfig);
        newFormats.setDynatrace(formatConfig);
        export.setFormats(newFormats);

        assertFalse(props.getExport().getFormats().getPrometheus().isEnabled());
        assertEquals("test.log", props.getExport().getFormats().getPrometheus().getFile());
        assertEquals("test-host", props.getExport().getFormats().getPrometheus().getHostname());
        assertEquals("test-prefix", props.getExport().getFormats().getPrometheus().getPrefix());

        MetricCollectorProperties.Security sec = new MetricCollectorProperties.Security();
        sec.setApiKey("key");
        sec.setRsocketToken("r-token");
        sec.setTcpToken("t-token");
        props.setSecurity(sec);
        assertEquals("key", props.getSecurity().getApiKey());
        assertEquals("r-token", props.getSecurity().getRsocketToken());
        assertEquals("t-token", props.getSecurity().getTcpToken());

        MetricCollectorProperties.Tcp tcp = new MetricCollectorProperties.Tcp();
        tcp.setEnabled(false);
        tcp.setHost("127.0.0.1");
        tcp.setPort(8080);
        props.setTcp(tcp);
        assertFalse(props.getTcp().isEnabled());
        assertEquals("127.0.0.1", props.getTcp().getHost());
        assertEquals(8080, props.getTcp().getPort());
    }
}
