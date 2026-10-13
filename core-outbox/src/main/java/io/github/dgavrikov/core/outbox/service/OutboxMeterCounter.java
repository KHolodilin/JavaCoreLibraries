package io.github.dgavrikov.core.outbox.service;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum OutboxMeterCounter {
    PLUGIN_NOT_REGISTRY("outbox_plugin_not_registry"),
    EVENT_SENDING_FAILED("outbox_event_sending_failed"),
    MEMORY_QUEUE_HEAVILY_LOADED("outbox_memory_queue_heavily_loaded"),
    MEMORY_QUEUE_IS_FULL("outbox_memory_queue_is_full"),
    CLEANUP_ERROR("outbox_cleanup_errors");

    private final String counterName;

    public String getCounterName(){
        return counterName;
    }
}
