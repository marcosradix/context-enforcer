package io.github.contextenforcer.steps;

import io.github.contextenforcer.core.StepResult;
import io.github.contextenforcer.test.TestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;

import static org.assertj.core.api.Assertions.assertThat;

class IntentVerificationStepTest {

    @Test
    @DisplayName("Should proceed when strategy allows the prompt")
    void shouldProceedWhenAllowed() {
        IntentVerificationStep step = new IntentVerificationStep((prompt, metadata) -> true);
        AdvisedRequest request = TestUtils.createRequest("Transfer funds");

        StepResult result = step.execute(request);

        assertThat(result).isInstanceOf(StepResult.Proceed.class);
    }

    @Test
    @DisplayName("Should reject with OUT_OF_CONTEXT_INTENT when strategy denies the prompt")
    void shouldRejectWhenDenied() {
        IntentVerificationStep step = new IntentVerificationStep((prompt, metadata) -> false);
        AdvisedRequest request = TestUtils.createRequest("What is the recipe for chocolate cake?");

        StepResult result = step.execute(request);

        assertThat(result).isInstanceOf(StepResult.Rejected.class);
        StepResult.Rejected rejected = (StepResult.Rejected) result;
        assertThat(rejected.stepName()).isEqualTo("IntentVerificationStep");
        assertThat(rejected.violationType()).isEqualTo(StepResult.ViolationType.OUT_OF_CONTEXT_INTENT);
        assertThat(rejected.reason()).contains("outside the allowed business context");
    }
}
