package com.metriccollector.formatter;

import com.metriccollector.config.MetricCollectorProperties;
import com.metriccollector.model.CollectedMetric;
import com.metriccollector.model.MetricType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ZabbixFormatterTest {

    private ZabbixFormatter formatter;

    @BeforeEach
    void setUp() {
        MetricCollectorProperties props = new MetricCollectorProperties();
        props.getExport().getFormats().getZabbix().setHostname("test-host");
        formatter = new ZabbixFormatter(props);
    }

    @Test
    void testFormatEmpty() {
        assertEquals("", formatter.format(List.of()));
        assertEquals("", formatter.format(null));
    }

    @Test
    void testFormatSingleMetric() {
        Instant now = Instant.ofEpochSecond(1713456789, 123000000);
        CollectedMetric metric = new CollectedMetric(
                "jvm_memory_used",
                1024.0,
                Map.of("area", "heap", "pool", "G1"),
                now,
                MetricType.GAUGE
        );

        String result = formatter.format(List.of(metric));
        
        // Note: order of labels in map can vary, so we check parts
        assertTrue(result.startsWith("{\"host\":\"test-host\",\"key\":\"jvm.memory.used["));
        assertTrue(result.contains("heap"));
        assertTrue(result.contains("G1"));
        assertTrue(result.contains("]\",\"value\":\"1024\",\"clock\":1713456789,\"ns\":123000000}\n"));
        
        assertEquals("zabbix", formatter.formatName());
        assertEquals("application/x-ndjson", formatter.contentType());
    }

    @Test
    void testFormatWithoutLabels() {
        Instant now = Instant.ofEpochSecond(1713456789, 0);
        CollectedMetric metric = new CollectedMetric(
                "system_cpu_load",
                0.45,
                Map.of(),
                now,
                MetricType.GAUGE
        );

        String result = formatter.format(List.of(metric));
        assertEquals("{\"host\":\"test-host\",\"key\":\"system.cpu.load\",\"value\":\"0.45\",\"clock\":1713456789,\"ns\":0}\n", result);
    }

    @Test
    void testEscaping() {
        Instant now = Instant.ofEpochSecond(1000, 0);
        CollectedMetric metric = new CollectedMetric(
                "esc_metric",
                1.0,
                Map.of("lbl", "a \"quote\" and \\slash"),
                now,
                MetricType.GAUGE
        );

        String result = formatter.format(List.of(metric));
        assertTrue(result.contains("a \\\\\\\"quote\\\\\\\" and \\\\\\\\slash"));
    }
}
