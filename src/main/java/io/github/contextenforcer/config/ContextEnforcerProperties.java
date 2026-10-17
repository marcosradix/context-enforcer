package io.github.contextenforcer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Collections;
import java.util.List;

/**
 * Configuration properties for ContextEnforcer Spring Boot Starter.
 */
@ConfigurationProperties(prefix = "context-enforcer")
public record ContextEnforcerProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("THROW_EXCEPTION") ViolationStrategy onViolation,
        @DefaultValue("The request is outside the allowed business context.") String fallbackMessage,
        SanitizationConfig sanitization,
        ValidationConfig validation,
        IntentConfig intent
) {

    public ContextEnforcerProperties {
        if (onViolation == null) {
            onViolation = ViolationStrategy.THROW_EXCEPTION;
        }
        if (fallbackMessage == null || fallbackMessage.isBlank()) {
            fallbackMessage = "The request is outside the allowed business context.";
        }
        if (sanitization == null) {
            sanitization = new SanitizationConfig(true, true);
        }
        if (validation == null) {
            validation = new ValidationConfig(4000, List.of("TEXT", "JSON"));
        }
        if (intent == null) {
            intent = new IntentConfig(0.78, Collections.emptyList());
        }
    }

    /**
     * Action to take when an enforcement violation occurs.
     */
    public enum ViolationStrategy {
        THROW_EXCEPTION,
        RETURN_FALLBACK_MESSAGE
    }

    /**
     * Sanitization settings for removing control chars and blocking prompt injections.
     */
    public record SanitizationConfig(
            @DefaultValue("true") boolean stripControlChars,
            @DefaultValue("true") boolean blockInjectionPatterns
    ) {}

    /**
     * Payload and schema validation constraints.
     */
    public record ValidationConfig(
            @DefaultValue("4000") int maxLength,
            @DefaultValue({"TEXT", "JSON"}) List<String> allowedFormats
    ) {
        public ValidationConfig {
            if (maxLength <= 0) {
                maxLength = 4000;
            }
            if (allowedFormats == null) {
                allowedFormats = List.of("TEXT", "JSON");
            }
        }
    }

    /**
     * Intent verification and topic boundary settings.
     */
    public record IntentConfig(
            @DefaultValue("0.78") double similarityThreshold,
            List<String> allowedTopics
    ) {
        public IntentConfig {
            if (similarityThreshold <= 0.0) {
                similarityThreshold = 0.78;
            }
            if (allowedTopics == null) {
                allowedTopics = Collections.emptyList();
            }
        }
    }
}
