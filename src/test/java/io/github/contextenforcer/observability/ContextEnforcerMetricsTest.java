package io.github.contextenforcer.observability;

import io.github.contextenforcer.core.StepResult;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ContextEnforcerMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private ContextEnforcerMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new ContextEnforcerMetrics(meterRegistry);
    }

    @Test
    @DisplayName("Should record allowed requests latency")
    void shouldRecordAllowedLatency() {
        metrics.recordAllowed(1_500_000L);

        var timer = meterRegistry.find("spring.ai.context.enforcer.latency")
                .tag("outcome", "ALLOWED")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should record rejection counter and latency")
    void shouldRecordRejection() {
        StepResult.Rejected rejected = new StepResult.Rejected(
                "SanitizationStep",
                StepResult.ViolationType.PROMPT_INJECTION_DETECTED,
                "Jailbreak attempt"
        );

        metrics.recordRejection(rejected, 500_000L);

        var counter = meterRegistry.find("spring.ai.context.enforcer.rejected.total")
                .tag("step", "SanitizationStep")
                .tag("violation_type", "PROMPT_INJECTION_DETECTED")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);

        var timer = meterRegistry.find("spring.ai.context.enforcer.latency")
                .tag("outcome", "REJECTED")
                .tag("step", "SanitizationStep")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Latency timers should share the same tag keys for all outcomes")
    void latencyTimersShouldShareTagKeys() {
        metrics.recordAllowed(1_000L);
        metrics.recordRejection(new StepResult.Rejected(
                "SanitizationStep", StepResult.ViolationType.PROMPT_INJECTION_DETECTED, "x"), 1_000L);

        var tagKeySets = meterRegistry.find("spring.ai.context.enforcer.latency").timers().stream()
                .map(t -> t.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet()))
                .distinct()
                .toList();

        assertThat(tagKeySets).hasSize(1);
        assertThat(tagKeySets.get(0)).containsExactlyInAnyOrder("outcome", "step");
    }
}
