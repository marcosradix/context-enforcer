package io.github.contextenforcer.observability;

import io.github.contextenforcer.core.StepResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * Micrometer metrics exporter for ContextEnforcer.
 * Records latency and rejection counters categorized by step and violation type.
 */
public class ContextEnforcerMetrics {

    private static final String METRIC_PREFIX = "spring.ai.context.enforcer";
    private final MeterRegistry registry;

    public ContextEnforcerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Records elapsed latency for requests that successfully passed all enforcement steps.
     *
     * @param durationNanos execution time in nanoseconds
     */
    public void recordAllowed(long durationNanos) {
        Timer.builder(METRIC_PREFIX + ".latency")
                .tag("outcome", "ALLOWED")
                .description("Execution latency of the ContextEnforcer pipeline")
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Records counter increment and latency for requests rejected by an enforcement step.
     *
     * @param rejected      the rejection details
     * @param durationNanos execution time up to rejection in nanoseconds
     */
    public void recordRejection(StepResult.Rejected rejected, long durationNanos) {
        Counter.builder(METRIC_PREFIX + ".rejected.total")
                .tag("step", rejected.stepName())
                .tag("violation_type", rejected.violationType().name())
                .description("Total number of prompts rejected by ContextEnforcer")
                .register(registry)
                .increment();

        Timer.builder(METRIC_PREFIX + ".latency")
                .tag("outcome", "REJECTED")
                .tag("step", rejected.stepName())
                .description("Execution latency of the ContextEnforcer pipeline for rejected requests")
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public MeterRegistry getRegistry() {
        return registry;
    }
}
