package io.github.contextenforcer.steps;

import io.github.contextenforcer.core.EnforcementStep;
import io.github.contextenforcer.core.StepResult;
import org.springframework.ai.chat.client.ChatClientRequest;

import java.util.Map;

/**
 * Enforcement Step 4 (Order: 40).
 * Verifies whether the user prompt strictly stays within the allowed business context and domain boundaries.
 */
public class IntentVerificationStep implements EnforcementStep {

    public static final int ORDER = 40;
    public static final String STEP_NAME = "IntentVerificationStep";

    @FunctionalInterface
    public interface IntentVerifierStrategy {
        /**
         * Checks if the given user prompt is within allowed business bounds.
         *
         * @param userPrompt      the user input text
         * @param contextMetadata current context metadata
         * @return true if permitted, false if out of context
         */
        boolean isWithinAllowedContext(String userPrompt, Map<String, Object> contextMetadata);
    }

    private final IntentVerifierStrategy verifierStrategy;

    public IntentVerificationStep(IntentVerifierStrategy verifierStrategy) {
        this.verifierStrategy = verifierStrategy;
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
        if (verifierStrategy == null) {
            return new StepResult.Proceed(request);
        }

        boolean allowed = verifierStrategy.isWithinAllowedContext(
                EnforcementStep.userText(request),
                request.context()
        );

        if (!allowed) {
            return new StepResult.Rejected(
                    getName(),
                    StepResult.ViolationType.OUT_OF_CONTEXT_INTENT,
                    "The request is outside the allowed business context."
            );
        }

        return new StepResult.Proceed(request);
    }

    public IntentVerifierStrategy getVerifierStrategy() {
        return verifierStrategy;
    }
}
