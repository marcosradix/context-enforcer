package io.github.contextenforcer.exception;

import io.github.contextenforcer.core.StepResult;

import java.io.Serial;

/**
 * Exception thrown when a prompt violates context enforcement rules and
 * {@link io.github.contextenforcer.config.ContextEnforcerProperties.ViolationStrategy#THROW_EXCEPTION} is configured.
 */
public class ContextViolationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final StepResult.Rejected rejection;

    public ContextViolationException(StepResult.Rejected rejection) {
        super("[%s - %s]: %s".formatted(rejection.stepName(), rejection.violationType(), rejection.reason()));
        this.rejection = rejection;
    }

    public StepResult.Rejected getRejection() {
        return rejection;
    }

    public StepResult.ViolationType getViolationType() {
        return rejection.violationType();
    }

    public String getStepName() {
        return rejection.stepName();
    }
}
