package io.github.dgavrikov.core.inbox.service;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum InboxMeterCounter {
    MEMORY_QUEUE_HEAVILY_LOADED("inbox_memory_queue_heavily_loaded"),
    PLUGIN_NOT_REGISTRY("inbox_plugin_not_registry"),
    PAYLOAD_VALIDATION_ERROR("inbox_payload_validation_error"),
    MEMORY_QUEUE_IS_FULL("inbox_memory_queue_is_full"),
    CLEANUP_ERROR("inbox_cleanup_error"),
    DUPLICATE_MESSAGE_DB_DATABASE("inbox_duplicate_message_in_database"),
    EVENT_PROCESSING_FAILED("inbox_event_processing_failed");

    private final String counterName;

    public String getCounterName(){
        return counterName;
    }
}
