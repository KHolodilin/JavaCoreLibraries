package io.github.dgavrikov.core.utils;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

@UtilityClass
public class MetricUtils {
    public void initialCounter(@NotNull MeterRegistry meterRegistry,
                               String counterName,
                               String description,
                               String... tags) {
        Counter.builder(counterName)
                .description(description)
                .tags(tags)
                .register(meterRegistry);
    }
}
