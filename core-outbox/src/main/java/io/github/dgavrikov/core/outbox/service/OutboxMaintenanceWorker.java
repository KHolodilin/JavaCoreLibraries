package io.github.dgavrikov.core.outbox.service;

import io.github.dgavrikov.core.outbox.model.OutboxEvent;
import io.github.dgavrikov.core.outbox.properties.OutboxProperties;
import io.github.dgavrikov.core.outbox.repository.OutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.BlockingQueue;

@Slf4j
public class OutboxMaintenanceWorker implements ApplicationListener<ApplicationReadyEvent> {

    private final BlockingQueue<OutboxEvent> outboxMemoryQueue;
    private final TaskScheduler outboxScheduler;
    private final OutboxProperties outboxProperties;
    private final OutboxRepository outboxRepository;
    private final MeterRegistry meterRegistry;
    private final int capacityThreshold;

    public OutboxMaintenanceWorker(BlockingQueue<OutboxEvent> outboxMemoryQueue,
                                   TaskScheduler outboxScheduler,
                                   OutboxProperties outboxProperties,
                                   OutboxRepository outboxRepository,
                                   MeterRegistry meterRegistry) {
        this.outboxMemoryQueue = outboxMemoryQueue;
        this.outboxScheduler = outboxScheduler;
        this.outboxProperties = outboxProperties;
        this.outboxRepository = outboxRepository;
        this.capacityThreshold = (int) (outboxProperties.inMemoryQueue().capacity() * 0.5);
        this.meterRegistry = meterRegistry;
    }


    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        var fixedDelay = Duration.ofMillis(outboxProperties.recoveryProps().recoveryIntervalDelayMs());
        var initialDelay = Duration.ofMillis(outboxProperties.recoveryProps().initialDelayMs());

        var periodicTrigger = new PeriodicTrigger(fixedDelay);
        periodicTrigger.setInitialDelay(initialDelay);

        var cleanupTrigger = new CronTrigger(outboxProperties.cleanupProps().cronExpression());

        outboxScheduler.schedule(this::recoveryLostEvents, periodicTrigger);
        outboxScheduler.schedule(this::purgeSentEvents, cleanupTrigger);
    }

    private void recoveryLostEvents() {
        if (outboxMemoryQueue.size() > capacityThreshold) {
            log.debug("Outbox memory queue is heavily loaded (size: {}). Skipping recovery.",
                    outboxMemoryQueue.size());
            incrementMeterCounter(OutboxMeterCounter.MEMORY_QUEUE_HEAVILY_LOADED);
            return;
        }

        var timeBoundary = OffsetDateTime.now()
                .minusSeconds(outboxProperties.recoveryProps().timeDepthSec());

        List<OutboxEvent> lostEvents = outboxRepository.findAbandonedEventsForUpdate(
                timeBoundary,
                outboxProperties.recoveryProps().batchSize()
        );

        if (lostEvents.isEmpty()) return;

        log.debug("Recovery outbox worker found {} abandoned events. Pushing to memory queue.", lostEvents.size());

        for (var event : lostEvents) {
            boolean added = outboxMemoryQueue.offer(event);
            if (!added) {
                log.info("Outbox memory queue is FULL! Recovery process is stop.");
                incrementMeterCounter(OutboxMeterCounter.MEMORY_QUEUE_IS_FULL);
                break;
            }
        }
    }

    private void purgeSentEvents() {
        log.info("Starting purge of successfully sent outbox events...");

        OffsetDateTime retentionBoundary = OffsetDateTime.now()
                .minusHours(outboxProperties.cleanupProps().depthInHour());

        long deletedRows = 0L;
        try {
            while (true) {
                var delete = outboxRepository.deleteSentEventsOlderThan(retentionBoundary,
                        outboxProperties.cleanupProps().batchSize());
                deletedRows += delete;

                if (delete == 0)
                    break;
            }
        } catch (Exception e) {
            log.error("Error on purge process outbox");
            incrementMeterCounter(OutboxMeterCounter.CLEANUP_ERROR);
            throw new RuntimeException(e);
        }
        log.info("Outbox table purged. Deleted {} historical events.", deletedRows);
    }

    private void incrementMeterCounter(@NotNull OutboxMeterCounter counter) {
        if (meterRegistry == null)
            return;
        meterRegistry.counter(counter.getCounterName(), "service", "maintenance").increment();
    }

}
