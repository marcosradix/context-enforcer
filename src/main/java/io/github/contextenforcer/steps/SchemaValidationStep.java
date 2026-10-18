package io.github.contextenforcer.steps;

import io.github.contextenforcer.config.ContextEnforcerProperties;
import io.github.contextenforcer.core.EnforcementStep;
import io.github.contextenforcer.core.StepResult;
import org.springframework.ai.chat.client.ChatClientRequest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Enforcement Step 2 (Order: 20).
 * Validates structural payload constraints such as maximum character length and format expectations.
 */
public class SchemaValidationStep implements EnforcementStep {

    public static final int ORDER = 20;
    public static final String STEP_NAME = "SchemaValidationStep";

    private final ContextEnforcerProperties properties;
    private final ObjectMapper objectMapper;

    public SchemaValidationStep(ContextEnforcerProperties properties) {
        this(properties, JsonMapper.builder().build());
    }

    public SchemaValidationStep(ContextEnforcerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return STEP_NAME;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public StepResult execute(ChatClientRequest request) {
        String text = EnforcementStep.userText(request);
        int maxLength = properties.validation().maxLength();

        if (text.length() > maxLength) {
            return new StepResult.Rejected(
                    getName(),
                    StepResult.ViolationType.SCHEMA_VALIDATION_FAILED,
                    "Prompt length (%d) exceeds maximum allowed limit (%d).".formatted(text.length(), maxLength)
            );
        }

        List<String> allowedFormats = properties.validation().allowedFormats();
        if (allowedFormats != null && !allowedFormats.isEmpty()) {
            boolean onlyJson = allowedFormats.stream().allMatch("JSON"::equalsIgnoreCase);
            if (onlyJson) {
                try {
                    objectMapper.readTree(text);
                } catch (Exception e) {
                    return new StepResult.Rejected(
                            getName(),
                            StepResult.ViolationType.SCHEMA_VALIDATION_FAILED,
                            "Prompt payload is not valid JSON as required by configuration."
                    );
                }
            }
        }

        return new StepResult.Proceed(request);
    }
}
