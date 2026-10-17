package io.github.contextenforcer.steps;

import io.github.contextenforcer.config.ContextEnforcerProperties;
import io.github.contextenforcer.core.EnforcementStep;
import io.github.contextenforcer.core.StepResult;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;

import java.util.regex.Pattern;

/**
 * Enforcement Step 1 (Order: 10).
 * Strips invisible/control ASCII characters and detects prompt injection / jailbreak patterns.
 */
public class SanitizationStep implements EnforcementStep {

    public static final int ORDER = 10;
    public static final String STEP_NAME = "SanitizationStep";

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");
    private static final Pattern INJECTION_PATTERN = Pattern.compile(
            "(?i)(ignore (all )?previous instructions|system prompt|you are now|forget everything|bypass constraints|dan mode|jailbreak|disregard instructions)"
    );

    private final ContextEnforcerProperties properties;

    public SanitizationStep(ContextEnforcerProperties properties) {
        this.properties = properties;
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
        if (text == null || text.isBlank()) {
            return new StepResult.Rejected(
                    getName(),
                    StepResult.ViolationType.SCHEMA_VALIDATION_FAILED,
                    "Prompt text must not be null or blank."
            );
        }

        if (properties.sanitization().blockInjectionPatterns() && INJECTION_PATTERN.matcher(text).find()) {
            return new StepResult.Rejected(
                    getName(),
                    StepResult.ViolationType.PROMPT_INJECTION_DETECTED,
                    "Prompt injection or jailbreak pattern detected in user request."
            );
        }

        String sanitized = properties.sanitization().stripControlChars()
                ? CONTROL_CHARS.matcher(text).replaceAll("")
                : text;

        AdvisedRequest updatedRequest = AdvisedRequest.from(request)
                .userText(sanitized.trim())
                .build();

        return new StepResult.Proceed(updatedRequest);
    }
}
