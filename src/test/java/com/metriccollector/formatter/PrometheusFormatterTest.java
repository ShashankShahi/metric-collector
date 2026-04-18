package com.metriccollector.formatter;

import com.metriccollector.model.CollectedMetric;
import com.metriccollector.model.MetricType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PrometheusFormatterTest {

    private final PrometheusFormatter formatter = new PrometheusFormatter();

    @Test
    void testFormatEmpty() {
        assertEquals("# No metrics available\n", formatter.format(List.of()));
        assertEquals("# No metrics available\n", formatter.format(null));
    }

    @Test
    void testFormatSingleMetric() {
        Instant now = Instant.ofEpochMilli(1713456789000L);
        CollectedMetric metric = new CollectedMetric(
                "jvm_memory_used_bytes",
                1024.0,
                Map.of("area", "heap"),
                now,
                MetricType.GAUGE,
                "Memory used in bytes"
        );

        String result = formatter.format(List.of(metric));
        
        assertTrue(result.contains("# HELP jvm_memory_used_bytes Memory used in bytes\n"));
        assertTrue(result.contains("# TYPE jvm_memory_used_bytes gauge\n"));
        assertTrue(result.contains("jvm_memory_used_bytes{area=\"heap\"} 1024 1713456789000\n"));
        assertEquals("prometheus", formatter.formatName());
        assertEquals("text/plain; version=0.0.4; charset=utf-8", formatter.contentType());
    }

    @Test
    void testFormatMultipleMetricsSameName() {
        Instant now = Instant.ofEpochMilli(1713456789000L);
        CollectedMetric metric1 = new CollectedMetric(
                "test_counter", 1, Map.of("lbl", "v1"), now, MetricType.COUNTER, "Test counter"
        );
        CollectedMetric metric2 = new CollectedMetric(
                "test_counter", 2, Map.of("lbl", "v2"), now, MetricType.COUNTER, "Test counter"
        );

        String result = formatter.format(List.of(metric1, metric2));
        
        // HELP and TYPE should only appear once
        int helpCount = result.split("# HELP").length - 1;
        int typeCount = result.split("# TYPE").length - 1;
        
        assertEquals(1, helpCount);
        assertEquals(1, typeCount);
        assertTrue(result.contains("test_counter{lbl=\"v1\"} 1 1713456789000\n"));
        assertTrue(result.contains("test_counter{lbl=\"v2\"} 2 1713456789000\n"));
    }

    @Test
    void testSpecialValues() {
        Instant now = Instant.ofEpochMilli(1000);
        CollectedMetric mNan = new CollectedMetric("nan_val", Double.NaN, Map.of(), now, MetricType.GAUGE);
        CollectedMetric mInf = new CollectedMetric("inf_val", Double.POSITIVE_INFINITY, Map.of(), now, MetricType.GAUGE);
        CollectedMetric mNinf = new CollectedMetric("ninf_val", Double.NEGATIVE_INFINITY, Map.of(), now, MetricType.GAUGE);

        String result = formatter.format(List.of(mNan, mInf, mNinf));
        
        assertTrue(result.contains("nan_val NaN 1000\n"));
        assertTrue(result.contains("inf_val +Inf 1000\n"));
        assertTrue(result.contains("ninf_val -Inf 1000\n"));
    }

    @Test
    void testEscaping() {
        Instant now = Instant.ofEpochMilli(1000);
        CollectedMetric metric = new CollectedMetric(
                "esc_metric",
                1.5,
                Map.of("path", "C:\\temp", "desc", "a \"quote\"\nnewline"),
                now,
                MetricType.GAUGE,
                "help with \\ and \n"
        );

        String result = formatter.format(List.of(metric));
        assertTrue(result.contains("# HELP esc_metric help with \\\\ and \\n\n"));
        assertTrue(result.contains("path=\"C:\\\\temp\""));
        assertTrue(result.contains("desc=\"a \\\"quote\\\"\\nnewline\""));
    }
}
