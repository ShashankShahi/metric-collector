package com.metriccollector.writer;

import com.metriccollector.config.MetricCollectorProperties;
import com.metriccollector.formatter.MetricFormatter;
import com.metriccollector.model.CollectedMetric;
import com.metriccollector.model.MetricSnapshot;
import com.metriccollector.registry.MetricRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricLogWriterTest {

    private MetricLogWriter writer;
    private Path tempDir;
    private MetricRegistry metricRegistry;
    private List<MetricFormatter> formatters;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("metrics-test");
        
        MetricCollectorProperties props = new MetricCollectorProperties();
        props.getExport().setLogDirectory(tempDir.toString());
        props.getExport().getFormats().getPrometheus().setEnabled(true);
        props.getExport().getFormats().getPrometheus().setFile("prom.log");
        
        props.getExport().getFormats().getZabbix().setEnabled(false);
        props.getExport().getFormats().getDynatrace().setEnabled(false);

        metricRegistry = mock(MetricRegistry.class);
        MetricSnapshot snapshot = new MetricSnapshot(List.of(
            new CollectedMetric("test", 1.0, java.util.Map.of(), Instant.now(), com.metriccollector.model.MetricType.GAUGE)
        ), Instant.now(), 10);
        when(metricRegistry.streamSnapshots()).thenReturn(reactor.core.publisher.Flux.just(snapshot));

        MetricFormatter mockFormatter = mock(MetricFormatter.class);
        when(mockFormatter.formatName()).thenReturn("prometheus");
        when(mockFormatter.format(org.mockito.ArgumentMatchers.anyList())).thenReturn("prometheus output");

        formatters = List.of(mockFormatter);
        writer = new MetricLogWriter(metricRegistry, props, formatters);
    }

    @AfterEach
    void tearDown() throws Exception {
        writer.shutdown();
        Files.walk(tempDir)
             .sorted(java.util.Comparator.reverseOrder())
             .map(Path::toFile)
             .forEach(File::delete);
    }

    @Test
    void testWriteMetrics() throws Exception {
        writer.startListening();

        // Give it a moment to process the reactive stream
        Thread.sleep(100);

        // Verify formatter was called
        org.mockito.Mockito.verify(formatters.get(0)).format(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void testWriteEmptyMetrics() throws Exception {
        MetricCollectorProperties props = new MetricCollectorProperties();
        props.getExport().setLogDirectory(tempDir.toString());
        props.getExport().getFormats().getPrometheus().setEnabled(true);
        when(metricRegistry.streamSnapshots()).thenReturn(reactor.core.publisher.Flux.just(MetricSnapshot.empty()));
        writer = new MetricLogWriter(metricRegistry, props, formatters);
        writer.startListening();
        Thread.sleep(100);
        
        // No metrics, should not call formatter
        org.mockito.Mockito.verify(formatters.get(0), org.mockito.Mockito.never()).format(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void testAllFormatsAndExceptions() throws Exception {
        MetricCollectorProperties props = new MetricCollectorProperties();
        props.getExport().setLogDirectory(tempDir.toString());
        props.getExport().getFormats().getPrometheus().setEnabled(true);
        props.getExport().getFormats().getZabbix().setEnabled(true);
        props.getExport().getFormats().getDynatrace().setEnabled(true);

        MetricFormatter promFormatter = mock(MetricFormatter.class);
        when(promFormatter.formatName()).thenReturn("prometheus");
        when(promFormatter.format(org.mockito.ArgumentMatchers.anyList())).thenThrow(new RuntimeException("Simulated formatter error"));

        MetricFormatter zabbixFormatter = mock(MetricFormatter.class);
        when(zabbixFormatter.formatName()).thenReturn("zabbix");
        when(zabbixFormatter.format(org.mockito.ArgumentMatchers.anyList())).thenReturn(""); // Test empty string

        // Missing dynatrace formatter intentionally

        MetricLogWriter errorWriter = new MetricLogWriter(metricRegistry, props, List.of(promFormatter, zabbixFormatter));
        errorWriter.startListening();
        Thread.sleep(100);

        // Verify it was called and caught the exception
        org.mockito.Mockito.verify(promFormatter).format(org.mockito.ArgumentMatchers.anyList());
        org.mockito.Mockito.verify(zabbixFormatter).format(org.mockito.ArgumentMatchers.anyList());
    }
}
