package io.github.contextenforcer.steps;

import io.github.contextenforcer.core.StepResult;
import io.github.contextenforcer.test.TestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextEnrichmentStepTest {

    @Test
    @DisplayName("Should securely inject metadata into systemParams and adviseContext without touching userText")
    void shouldInjectMetadataSafely() {
        ContextEnrichmentStep.ContextMetadataProvider provider = () -> Map.of(
                "tenantId", "tenant-123",
                "role", "ROLE_ADMIN",
                "department", "FINANCE"
        );
        ContextEnrichmentStep step = new ContextEnrichmentStep(provider);

        AdvisedRequest initialRequest = TestUtils.createRequest(
                "What is the quarterly balance?",
                Map.of("existingParam", "value1"),
                Map.of("contextKey", "contextVal")
        );

        StepResult result = step.execute(initialRequest);

        assertThat(result).isInstanceOf(StepResult.Proceed.class);
        StepResult.Proceed proceed = (StepResult.Proceed) result;
        AdvisedRequest enriched = proceed.request();

        // User text must remain strictly untouched to prevent injection
        assertThat(enriched.userText()).isEqualTo("What is the quarterly balance?");

        // System params must include both original and injected metadata
        assertThat(enriched.systemParams()).containsEntry("existingParam", "value1");
        assertThat(enriched.systemParams()).containsEntry("tenantId", "tenant-123");
        assertThat(enriched.systemParams()).containsEntry("role", "ROLE_ADMIN");
        assertThat(enriched.systemParams()).containsEntry("department", "FINANCE");

        // Advise context must also include the injected metadata
        assertThat(enriched.adviseContext()).containsEntry("contextKey", "contextVal");
        assertThat(enriched.adviseContext()).containsEntry("tenantId", "tenant-123");
    }

    @Test
    @DisplayName("Should proceed cleanly when metadata provider returns empty map or is null")
    void shouldHandleEmptyMetadataProvider() {
        ContextEnrichmentStep step = new ContextEnrichmentStep(() -> Map.of());
        AdvisedRequest request = TestUtils.createRequest("Hello");

        StepResult result = step.execute(request);

        assertThat(result).isInstanceOf(StepResult.Proceed.class);
    }
}
