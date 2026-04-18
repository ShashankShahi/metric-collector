package com.metriccollector.registry;

import com.metriccollector.model.MetricSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-memory store for the latest metric snapshot.
 * <p>
 * Provides both pull-based ({@link #getSnapshot()}) and push-based
 * ({@link #streamSnapshots()}) access to metric data.
 */
@Component
public class MetricRegistry {

    private static final Logger log = LoggerFactory.getLogger(MetricRegistry.class);

    /**
     * Holds the latest snapshot. Atomic for thread safety without locking.
     */
    private final AtomicReference<MetricSnapshot> currentSnapshot =
            new AtomicReference<>(MetricSnapshot.empty());

    /**
     * Reactive sink that emits each new snapshot to all subscribers.
     * Uses LATEST strategy: slow subscribers get only the most recent snapshot.
     */
    private final Sinks.Many<MetricSnapshot> snapshotSink =
            Sinks.many().multicast().onBackpressureBuffer(16);

    /**
     * Updates the current snapshot and notifies all reactive subscribers.
     *
     * @param snapshot the new snapshot to store
     */
    public void updateSnapshot(MetricSnapshot snapshot) {
        currentSnapshot.set(snapshot);

        Sinks.EmitResult result = snapshotSink.tryEmitNext(snapshot);
        if (result.isFailure()) {
            log.warn("Failed to emit snapshot to reactive stream: {}", result);
        }

        log.debug("Snapshot updated: {} metrics at {}", snapshot.size(), snapshot.collectedAt());
    }

    /**
     * Returns the latest metric snapshot (pull-based).
     *
     * @return the most recent MetricSnapshot, never null
     */
    public MetricSnapshot getSnapshot() {
        return currentSnapshot.get();
    }

    /**
     * Returns a reactive stream of metric snapshots (push-based).
     * New subscribers will receive all future snapshots.
     *
     * @return Flux of MetricSnapshot
     */
    public Flux<MetricSnapshot> streamSnapshots() {
        return snapshotSink.asFlux();
    }
}
