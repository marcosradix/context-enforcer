package io.github.contextenforcer.autoconfigure;

import io.github.contextenforcer.advisor.ContextEnforcerAdvisor;
import io.github.contextenforcer.observability.ContextEnforcerMetrics;
import io.github.contextenforcer.steps.ContextEnrichmentStep;
import io.github.contextenforcer.steps.IntentVerificationStep;
import io.github.contextenforcer.steps.SanitizationStep;
import io.github.contextenforcer.steps.SchemaValidationStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

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
}
