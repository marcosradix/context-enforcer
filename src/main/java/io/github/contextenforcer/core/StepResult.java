package io.github.contextenforcer.core;

import org.springframework.ai.chat.client.ChatClientRequest;

/**
 * Result of an enforcement step in the ContextEnforcer pipeline.
 * Uses Java sealed interface with record permits for type-safe pattern matching.
 */
public sealed interface StepResult permits StepResult.Proceed, StepResult.Rejected {

    /**
     * Indicates that the request passed validation and provides the (potentially enriched or sanitized) request.
     *
     * @param request the current ChatClientRequest to pass to the next step
     */
    record Proceed(ChatClientRequest request) implements StepResult {}

    /**
     * Indicates that the request was rejected by an enforcement step.
     *
     * @param stepName      name of the step that rejected the request
     * @param violationType category of the violation
     * @param reason        detailed explanation of why the request was rejected
     */
    record Rejected(
            String stepName,
            ViolationType violationType,
            String reason
    ) implements StepResult {}

    /**
     * Categorization of context violations.
     */
    enum ViolationType {
        PROMPT_INJECTION_DETECTED,
        SCHEMA_VALIDATION_FAILED,
        OUT_OF_CONTEXT_INTENT,
        UNAUTHORIZED_CONTEXT
    }
}
