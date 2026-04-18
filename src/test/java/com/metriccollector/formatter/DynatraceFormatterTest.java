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

class DynatraceFormatterTest {

    private DynatraceFormatter formatter;

    @BeforeEach
    void setUp() {
        MetricCollectorProperties props = new MetricCollectorProperties();
        props.getExport().getFormats().getDynatrace().setPrefix("custom");
        formatter = new DynatraceFormatter(props);
    }

    @Test
    void testFormatEmpty() {
        assertEquals("", formatter.format(List.of()));
        assertEquals("", formatter.format(null));
    }

    @Test
    void testFormatSingleGauge() {
        Instant now = Instant.ofEpochMilli(1713456789000L);
        CollectedMetric metric = new CollectedMetric(
                "jvm_memory_used",
                1024.0,
                Map.of("area", "heap"),
                now,
                MetricType.GAUGE
        );

        String result = formatter.format(List.of(metric));
        assertEquals("custom.jvm.memory.used,area=heap gauge,1024 1713456789000\n", result);
        
        assertEquals("dynatrace", formatter.formatName());
        assertEquals("text/plain; charset=utf-8", formatter.contentType());
    }

    @Test
    void testFormatSingleCounter() {
        Instant now = Instant.ofEpochMilli(1713456789000L);
        CollectedMetric metric = new CollectedMetric(
                "requests_total",
                5.0,
                Map.of(),
                now,
                MetricType.COUNTER
        );

        String result = formatter.format(List.of(metric));
        assertEquals("custom.requests.total count,delta=5 1713456789000\n", result);
    }

    @Test
    void testPrefixAvoidDouble() {
        Instant now = Instant.ofEpochMilli(1713456789000L);
        CollectedMetric metric = new CollectedMetric(
                "custom_metric_val", // already starts with custom
                10.0,
                Map.of(),
                now,
                MetricType.GAUGE
        );

        String result = formatter.format(List.of(metric));
        assertEquals("custom.metric.val gauge,10 1713456789000\n", result);
    }

    @Test
    void testDimensionSanitization() {
        Instant now = Instant.ofEpochMilli(1000L);
        CollectedMetric metric = new CollectedMetric(
                "test",
                1.0,
                Map.of(
                        "Bad Key!@#", "value",
                        "key", "value with spaces",
                        "key2", "value\"with\"quotes",
                        "key3", "value\nnewlines"
                ),
                now,
                MetricType.GAUGE
        );

        String result = formatter.format(List.of(metric));
        assertTrue(result.contains("bad_key=value"));
        assertTrue(result.contains("key=\"value with spaces\""));
        assertTrue(result.contains("key2=value\"with\"quotes"));
        assertTrue(result.contains("key3=valuenewlines"));
    }

    @Test
    void testSpecialValues() {
        Instant now = Instant.ofEpochMilli(1000L);
        CollectedMetric mNan = new CollectedMetric("nan_val", Double.NaN, Map.of(), now, MetricType.GAUGE);
        CollectedMetric mInf = new CollectedMetric("inf_val", Double.POSITIVE_INFINITY, Map.of(), now, MetricType.GAUGE);

        String result = formatter.format(List.of(mNan, mInf));
        
        assertTrue(result.contains("custom.nan.val gauge,0 1000\n"));
        assertTrue(result.contains("custom.inf.val gauge,0 1000\n"));
    }
}
