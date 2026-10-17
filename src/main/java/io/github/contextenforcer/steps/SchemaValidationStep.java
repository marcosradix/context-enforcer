package io.github.contextenforcer.steps;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.contextenforcer.config.ContextEnforcerProperties;
import io.github.contextenforcer.core.EnforcementStep;
import io.github.contextenforcer.core.StepResult;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;

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
        this(properties, new ObjectMapper());
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
    public StepResult execute(AdvisedRequest request) {
        String text = request.userText();
        int maxLength = properties.validation().maxLength();

        if (text != null && text.length() > maxLength) {
            return new StepResult.Rejected(
                    getName(),
                    StepResult.ViolationType.SCHEMA_VALIDATION_FAILED,
                    "Prompt length (%d) exceeds maximum allowed limit (%d).".formatted(text.length(), maxLength)
            );
        }

        List<String> allowedFormats = properties.validation().allowedFormats();
        if (allowedFormats != null && !allowedFormats.isEmpty()) {
            boolean onlyJson = allowedFormats.size() == 1 && allowedFormats.contains("JSON");
            if (onlyJson && text != null) {
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
