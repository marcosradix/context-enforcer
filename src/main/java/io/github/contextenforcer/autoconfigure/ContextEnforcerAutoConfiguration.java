package io.github.contextenforcer.autoconfigure;

import io.github.contextenforcer.advisor.ContextEnforcerAdvisor;
import io.github.contextenforcer.config.ContextEnforcerProperties;
import io.github.contextenforcer.core.EnforcementStep;
import io.github.contextenforcer.observability.ContextEnforcerMetrics;
import io.github.contextenforcer.steps.ContextEnrichmentStep;
import io.github.contextenforcer.steps.ContextEnrichmentStep.ContextMetadataProvider;
import io.github.contextenforcer.steps.IntentVerificationStep;
import io.github.contextenforcer.steps.IntentVerificationStep.IntentVerifierStrategy;
import io.github.contextenforcer.steps.SanitizationStep;
import io.github.contextenforcer.steps.SchemaValidationStep;
import io.github.contextenforcer.strategy.EmbeddingIntentVerifierStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Collections;
import java.util.List;

/**
 * Spring Boot auto-configuration for ContextEnforcer.
 * Automatically wires steps, metrics, strategies, and the primary advisor into the application context.
 */
@AutoConfiguration
@ConditionalOnClass(ContextEnforcerAdvisor.class)
@EnableConfigurationProperties(ContextEnforcerProperties.class)
@ConditionalOnProperty(prefix = "context-enforcer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ContextEnforcerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ContextEnforcerMetrics contextEnforcerMetrics(@Autowired(required = false) MeterRegistry meterRegistry) {
        return new ContextEnforcerMetrics(meterRegistry != null ? meterRegistry : new SimpleMeterRegistry());
    }

    @Bean
    @ConditionalOnMissingBean
    public SanitizationStep sanitizationStep(ContextEnforcerProperties properties) {
        return new SanitizationStep(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public SchemaValidationStep schemaValidationStep(ContextEnforcerProperties properties) {
        return new SchemaValidationStep(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextMetadataProvider defaultContextMetadataProvider() {
        return Collections::emptyMap;
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextEnrichmentStep contextEnrichmentStep(ContextMetadataProvider metadataProvider) {
        return new ContextEnrichmentStep(metadataProvider);
    }

    @Bean
    @ConditionalOnMissingBean(IntentVerifierStrategy.class)
    @ConditionalOnBean(EmbeddingModel.class)
    public IntentVerifierStrategy embeddingIntentVerifierStrategy(
            EmbeddingModel embeddingModel,
            ContextEnforcerProperties properties
    ) {
        return new EmbeddingIntentVerifierStrategy(embeddingModel, properties);
    }

    @Bean
    @ConditionalOnMissingBean(IntentVerifierStrategy.class)
    public IntentVerifierStrategy fallbackNoOpIntentVerifierStrategy() {
        return (userPrompt, metadata) -> true;
    }

    @Bean
    @ConditionalOnMissingBean
    public IntentVerificationStep intentVerificationStep(IntentVerifierStrategy verifierStrategy) {
        return new IntentVerificationStep(verifierStrategy);
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextEnforcerAdvisor contextEnforcerAdvisor(
            List<EnforcementStep> steps,
            ContextEnforcerProperties properties,
            ContextEnforcerMetrics metrics
    ) {
        return new ContextEnforcerAdvisor(steps, properties, metrics);
    }
}
