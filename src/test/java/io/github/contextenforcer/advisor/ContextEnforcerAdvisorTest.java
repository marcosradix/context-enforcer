package io.github.contextenforcer.advisor;

import io.github.contextenforcer.config.ContextEnforcerProperties;
import io.github.contextenforcer.core.EnforcementStep;
import io.github.contextenforcer.core.StepResult;
import io.github.contextenforcer.exception.ContextViolationException;
import io.github.contextenforcer.observability.ContextEnforcerMetrics;
import io.github.contextenforcer.test.TestUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextEnforcerAdvisorTest {

    private SimpleMeterRegistry meterRegistry;
    private ContextEnforcerMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new ContextEnforcerMetrics(meterRegistry);
    }

    private static class StubStep implements EnforcementStep {
        private final int order;
        private final String name;
        private final StepResult resultToReturn;
        boolean executed = false;

        StubStep(int order, String name, StepResult resultToReturn) {
            this.order = order;
            this.name = name;
            this.resultToReturn = resultToReturn;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public StepResult execute(AdvisedRequest request) {
            this.executed = true;
            return resultToReturn != null ? resultToReturn : new StepResult.Proceed(request);
        }
    }

    @Test
    @DisplayName("aroundCall should pass request down the chain when all steps proceed")
    void aroundCallShouldProceedWhenStepsPass() {
        StubStep step1 = new StubStep(10, "Step1", null);

        ContextEnforcerProperties props = new ContextEnforcerProperties(
                true,
                ContextEnforcerProperties.ViolationStrategy.THROW_EXCEPTION,
                "Fallback",
                null,
                null,
                null
        );

        ContextEnforcerAdvisor advisor = new ContextEnforcerAdvisor(List.of(step1), props, metrics);

        AdvisedRequest request = TestUtils.createRequest("Hello");
        AdvisedResponse expectedResponse = new AdvisedResponse(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Hi there!")))),
                Map.of()
        );

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        CallAroundAdvisorChain chain = req -> {
            chainCalled.set(true);
            return expectedResponse;
        };

        AdvisedResponse response = advisor.aroundCall(request, chain);

        assertThat(response).isSameAs(expectedResponse);
        assertThat(chainCalled.get()).isTrue();
        assertThat(step1.executed).isTrue();
        assertThat(meterRegistry.find("spring.ai.context.enforcer.latency").timer().count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("aroundCall should throw ContextViolationException when violation strategy is THROW_EXCEPTION")
    void aroundCallShouldThrowExceptionOnViolation() {
        StubStep step1 = new StubStep(
                10,
                "MockStep",
                new StepResult.Rejected("MockStep", StepResult.ViolationType.PROMPT_INJECTION_DETECTED, "Jailbreak detected")
        );

        ContextEnforcerProperties props = new ContextEnforcerProperties(
                true,
                ContextEnforcerProperties.ViolationStrategy.THROW_EXCEPTION,
                "Fallback message",
                null,
                null,
                null
        );

        ContextEnforcerAdvisor advisor = new ContextEnforcerAdvisor(List.of(step1), props, metrics);
        AdvisedRequest request = TestUtils.createRequest("Bad prompt");

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        CallAroundAdvisorChain chain = req -> {
            chainCalled.set(true);
            return null;
        };

        assertThatThrownBy(() -> advisor.aroundCall(request, chain))
                .isInstanceOf(ContextViolationException.class)
                .hasMessageContaining("[MockStep - PROMPT_INJECTION_DETECTED]: Jailbreak detected");

        assertThat(chainCalled.get()).isFalse();
        assertThat(meterRegistry.find("spring.ai.context.enforcer.rejected.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("aroundCall should return fallback AdvisedResponse when violation strategy is RETURN_FALLBACK_MESSAGE")
    void aroundCallShouldReturnFallbackOnViolation() {
        StubStep step1 = new StubStep(
                10,
                "MockStep",
                new StepResult.Rejected("MockStep", StepResult.ViolationType.OUT_OF_CONTEXT_INTENT, "Out of topic")
        );

        String fallback = "Custom business fallback message.";
        ContextEnforcerProperties props = new ContextEnforcerProperties(
                true,
                ContextEnforcerProperties.ViolationStrategy.RETURN_FALLBACK_MESSAGE,
                fallback,
                null,
                null,
                null
        );

        ContextEnforcerAdvisor advisor = new ContextEnforcerAdvisor(List.of(step1), props, metrics);
        AdvisedRequest request = TestUtils.createRequest("Irrelevant question", Map.of(), Map.of("session", "abc"));

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        CallAroundAdvisorChain chain = req -> {
            chainCalled.set(true);
            return null;
        };

        AdvisedResponse response = advisor.aroundCall(request, chain);

        assertThat(response).isNotNull();
        assertThat(response.response().getResult().getOutput().getText()).isEqualTo(fallback);
        assertThat(response.adviseContext()).containsEntry("session", "abc");
        assertThat(chainCalled.get()).isFalse();
        assertThat(meterRegistry.find("spring.ai.context.enforcer.rejected.total").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("aroundStream should emit fallback response reactively on violation")
    void aroundStreamShouldEmitFallbackOnViolation() {
        StubStep step1 = new StubStep(
                10,
                "StreamStep",
                new StepResult.Rejected("StreamStep", StepResult.ViolationType.SCHEMA_VALIDATION_FAILED, "Payload too large")
        );

        String fallback = "Stream fallback message.";
        ContextEnforcerProperties props = new ContextEnforcerProperties(
                true,
                ContextEnforcerProperties.ViolationStrategy.RETURN_FALLBACK_MESSAGE,
                fallback,
                null,
                null,
                null
        );

        ContextEnforcerAdvisor advisor = new ContextEnforcerAdvisor(List.of(step1), props, metrics);
        AdvisedRequest request = TestUtils.createRequest("Large prompt");

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        StreamAroundAdvisorChain chain = req -> {
            chainCalled.set(true);
            return Flux.empty();
        };

        Flux<AdvisedResponse> stream = advisor.aroundStream(request, chain);

        StepVerifier.create(stream)
                .assertNext(res -> assertThat(res.response().getResult().getOutput().getText()).isEqualTo(fallback))
                .verifyComplete();

        assertThat(chainCalled.get()).isFalse();
    }

    @Test
    @DisplayName("aroundCall should bypass pipeline when enabled is false")
    void shouldBypassWhenDisabled() {
        ContextEnforcerProperties props = new ContextEnforcerProperties(
                false,
                ContextEnforcerProperties.ViolationStrategy.THROW_EXCEPTION,
                "Fallback",
                null,
                null,
                null
        );

        StubStep step = new StubStep(10, "Step", null);
        ContextEnforcerAdvisor advisor = new ContextEnforcerAdvisor(List.of(step), props, metrics);

        AdvisedRequest request = TestUtils.createRequest("Test");
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        CallAroundAdvisorChain chain = req -> {
            chainCalled.set(true);
            return null;
        };

        advisor.aroundCall(request, chain);

        assertThat(step.executed).isFalse();
        assertThat(chainCalled.get()).isTrue();
    }
}
