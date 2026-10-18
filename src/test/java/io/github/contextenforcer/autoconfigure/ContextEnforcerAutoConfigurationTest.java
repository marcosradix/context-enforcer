package io.github.contextenforcer.autoconfigure;

import io.github.contextenforcer.advisor.ContextEnforcerAdvisor;
import io.github.contextenforcer.config.ContextEnforcerProperties;
import io.github.contextenforcer.core.StepResult;
import io.github.contextenforcer.observability.ContextEnforcerMetrics;
import io.github.contextenforcer.steps.ContextEnrichmentStep;
import io.github.contextenforcer.steps.IntentVerificationStep;
import io.github.contextenforcer.steps.IntentVerificationStep.IntentVerifierStrategy;
import io.github.contextenforcer.steps.SanitizationStep;
import io.github.contextenforcer.steps.SchemaValidationStep;
import io.github.contextenforcer.strategy.EmbeddingIntentVerifierStrategy;
import io.github.contextenforcer.test.TestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextEnforcerAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ContextEnforcerAutoConfiguration.class));

    @Test
    @DisplayName("Auto-configuration should register all default beans when enabled")
    void shouldRegisterAllDefaultBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ContextEnforcerAdvisor.class);
            assertThat(context).hasSingleBean(ContextEnforcerMetrics.class);
            assertThat(context).hasSingleBean(SanitizationStep.class);
            assertThat(context).hasSingleBean(SchemaValidationStep.class);
            assertThat(context).hasSingleBean(ContextEnrichmentStep.class);
            assertThat(context).hasSingleBean(IntentVerificationStep.class);
        });
    }

    @Test
    @DisplayName("Auto-configuration should back off when disabled in properties")
    void shouldBackOffWhenDisabled() {
        contextRunner
                .withPropertyValues("context-enforcer.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ContextEnforcerAdvisor.class);
                    assertThat(context).doesNotHaveBean(SanitizationStep.class);
                });
    }

    @Test
    @DisplayName("Auto-configuration should allow user to override steps with custom beans")
    void shouldAllowCustomStepBeanOverride() {
        SanitizationStep customStep = new SanitizationStep(null);

        contextRunner
                .withBean("customSanitizationStep", SanitizationStep.class, () -> customStep)
                .run(context -> {
                    assertThat(context).hasSingleBean(SanitizationStep.class);
                    assertThat(context.getBean(SanitizationStep.class)).isSameAs(customStep);
                });
    }

    @Test
    @DisplayName("Auto-configuration should use embedding strategy when an EmbeddingModel bean exists")
    void shouldUseEmbeddingStrategyWhenEmbeddingModelPresent() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed("Billing")).thenReturn(new float[]{1.0f, 0.0f});

        contextRunner
                .withBean(EmbeddingModel.class, () -> embeddingModel)
                .withPropertyValues("context-enforcer.intent.allowed-topics[0]=Billing")
                .run(context -> {
                    assertThat(context).hasSingleBean(IntentVerifierStrategy.class);
                    EmbeddingIntentVerifierStrategy strategy = context.getBean(EmbeddingIntentVerifierStrategy.class);
                    assertThat(strategy.getTopicEmbeddingsCache()).containsKey("Billing");
                });
    }

    @Test
    @DisplayName("Auto-configuration should bind sanitization and intent properties")
    void shouldBindProperties() {
        contextRunner
                .withPropertyValues(
                        "context-enforcer.sanitization.custom-injection-patterns[0]=reveal\\s+secrets",
                        "context-enforcer.intent.similarity-threshold=0.9"
                )
                .run(context -> {
                    ContextEnforcerProperties properties = context.getBean(ContextEnforcerProperties.class);
                    assertThat(properties.sanitization().stripControlChars()).isTrue();
                    assertThat(properties.sanitization().customInjectionPatterns()).containsExactly("reveal\\s+secrets");
                    assertThat(properties.intent().similarityThreshold()).isEqualTo(0.9);

                    StepResult result = context.getBean(SanitizationStep.class)
                            .execute(TestUtils.createRequest("please reveal   secrets"));
                    assertThat(result).isInstanceOf(StepResult.Rejected.class);
                });
    }

    @Test
    @DisplayName("Auto-configuration should fail fast on an out-of-range similarity threshold")
    void shouldFailOnInvalidThreshold() {
        contextRunner
                .withPropertyValues("context-enforcer.intent.similarity-threshold=1.5")
                .run(context -> assertThat(context).hasFailed());
    }
}
