package io.github.contextenforcer.core;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.core.Ordered;

/**
 * Contract for a step within the ContextEnforcer Chain of Responsibility.
 * Extends {@link Ordered} to guarantee deterministic pipeline execution.
 */
public interface EnforcementStep extends Ordered {

    /**
     * Unique human-readable name of the enforcement step.
     *
     * @return step identifier
     */
    String getName();

    /**
     * Executes enforcement validation, sanitization, enrichment, or verification on the given request.
     *
     * @param request current AdvisedRequest
     * @return {@link StepResult.Proceed} if passed, or {@link StepResult.Rejected} if violated
     */
    StepResult execute(AdvisedRequest request);
}
