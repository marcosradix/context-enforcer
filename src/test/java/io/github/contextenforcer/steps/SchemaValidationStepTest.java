package io.github.contextenforcer.steps;

import io.github.contextenforcer.config.ContextEnforcerProperties;
import io.github.contextenforcer.config.ContextEnforcerProperties.ValidationConfig;
import io.github.contextenforcer.core.StepResult;
import io.github.contextenforcer.test.TestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaValidationStepTest {

    @Test
    @DisplayName("Should reject prompt exceeding maximum length")
    void shouldRejectLengthExceeded() {
        ContextEnforcerProperties properties = new ContextEnforcerProperties(
                true,
                ContextEnforcerProperties.ViolationStrategy.THROW_EXCEPTION,
                "Fallback",
                null,
                new ValidationConfig(50, List.of("TEXT")),
                null
        );
        SchemaValidationStep step = new SchemaValidationStep(properties);

        String longPrompt = "A".repeat(51);
        AdvisedRequest request = TestUtils.createRequest(longPrompt);

        StepResult result = step.execute(request);

        assertThat(result).isInstanceOf(StepResult.Rejected.class);
        StepResult.Rejected rejected = (StepResult.Rejected) result;
        assertThat(rejected.stepName()).isEqualTo("SchemaValidationStep");
        assertThat(rejected.violationType()).isEqualTo(StepResult.ViolationType.SCHEMA_VALIDATION_FAILED);
        assertThat(rejected.reason()).contains("exceeds maximum allowed limit");
    }

    @Test
    @DisplayName("Should proceed when prompt is within length limits")
    void shouldProceedWithinLength() {
        ContextEnforcerProperties properties = new ContextEnforcerProperties(
                true,
                ContextEnforcerProperties.ViolationStrategy.THROW_EXCEPTION,
                "Fallback",
                null,
                new ValidationConfig(50, List.of("TEXT")),
                null
        );
        SchemaValidationStep step = new SchemaValidationStep(properties);

        AdvisedRequest request = TestUtils.createRequest("Short text");
        StepResult result = step.execute(request);

        assertThat(result).isInstanceOf(StepResult.Proceed.class);
    }

    @Test
    @DisplayName("Should reject invalid JSON when JSON format is strictly required")
    void shouldRejectInvalidJsonWhenRequired() {
        ContextEnforcerProperties properties = new ContextEnforcerProperties(
                true,
                ContextEnforcerProperties.ViolationStrategy.THROW_EXCEPTION,
                "Fallback",
                null,
                new ValidationConfig(1000, List.of("JSON")),
                null
        );
        SchemaValidationStep step = new SchemaValidationStep(properties);

        AdvisedRequest request = TestUtils.createRequest("{not a valid json}");
        StepResult result = step.execute(request);

        assertThat(result).isInstanceOf(StepResult.Rejected.class);
        StepResult.Rejected rejected = (StepResult.Rejected) result;
        assertThat(rejected.violationType()).isEqualTo(StepResult.ViolationType.SCHEMA_VALIDATION_FAILED);
        assertThat(rejected.reason()).contains("not valid JSON");
    }

    @Test
    @DisplayName("Should proceed with valid JSON when JSON format is required")
    void shouldProceedWithValidJson() {
        ContextEnforcerProperties properties = new ContextEnforcerProperties(
                true,
                ContextEnforcerProperties.ViolationStrategy.THROW_EXCEPTION,
                "Fallback",
                null,
                new ValidationConfig(1000, List.of("JSON")),
                null
        );
        SchemaValidationStep step = new SchemaValidationStep(properties);

        AdvisedRequest request = TestUtils.createRequest("{\"action\": \"check_balance\"}");
        StepResult result = step.execute(request);

        assertThat(result).isInstanceOf(StepResult.Proceed.class);
    }
}
