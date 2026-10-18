package io.github.contextenforcer.steps;

import io.github.contextenforcer.core.StepResult;
import io.github.contextenforcer.test.TestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContextEnrichmentStepTest {

    @Test
    @DisplayName("Should inject metadata into context and system placeholders without touching user text")
    void shouldInjectMetadataSafely() {
        ContextEnrichmentStep.ContextMetadataProvider provider = () -> Map.of(
                "tenantId", "tenant-123",
                "role", "ROLE_ADMIN",
                "department", "FINANCE"
        );
        ContextEnrichmentStep step = new ContextEnrichmentStep(provider);

        ChatClientRequest initialRequest = TestUtils.createRequest(
                "You are an assistant for {department}. Keep {unknown} as is.",
                "What is the quarterly balance for {department}?",
                Map.of("contextKey", "contextVal")
        );

        StepResult result = step.execute(initialRequest);

        assertThat(result).isInstanceOf(StepResult.Proceed.class);
        ChatClientRequest enriched = ((StepResult.Proceed) result).request();

        // User text must remain strictly untouched to prevent injection
        assertThat(enriched.prompt().getUserMessage().getText()).isEqualTo("What is the quarterly balance for {department}?");

        // Only known placeholders in system messages are filled
        assertThat(enriched.prompt().getSystemMessage().getText())
                .isEqualTo("You are an assistant for FINANCE. Keep {unknown} as is.");

        // Context must include both original and injected metadata
        assertThat(enriched.context()).containsEntry("contextKey", "contextVal");
        assertThat(enriched.context()).containsEntry("tenantId", "tenant-123");
        assertThat(enriched.context()).containsEntry("role", "ROLE_ADMIN");
    }

    @Test
    @DisplayName("Should not expand placeholders contained in metadata values")
    void shouldNotExpandPlaceholdersRecursively() {
        ContextEnrichmentStep step = new ContextEnrichmentStep(() -> Map.of(
                "name", "{role}",
                "role", "ROLE_ADMIN"
        ));

        StepResult result = step.execute(TestUtils.createRequest("Hello {name}", "Hi", Map.of()));

        ChatClientRequest enriched = ((StepResult.Proceed) result).request();
        assertThat(enriched.prompt().getSystemMessage().getText()).isEqualTo("Hello {role}");
    }

    @Test
    @DisplayName("Should proceed cleanly when metadata provider returns empty map or is null")
    void shouldHandleEmptyMetadataProvider() {
        ContextEnrichmentStep step = new ContextEnrichmentStep(() -> Map.of());
        ChatClientRequest request = TestUtils.createRequest("Hello");

        StepResult result = step.execute(request);

        assertThat(result).isInstanceOf(StepResult.Proceed.class);
    }
}
