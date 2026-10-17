package io.github.contextenforcer.advisor;

import io.github.contextenforcer.config.ContextEnforcerProperties;
import io.github.contextenforcer.core.EnforcementStep;
import io.github.contextenforcer.core.StepResult;
import io.github.contextenforcer.exception.ContextViolationException;
import io.github.contextenforcer.observability.ContextEnforcerMetrics;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAroundAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Primary Spring AI Advisor enforcing context, guardrails, and security.
 * Implements both {@link CallAroundAdvisor} (synchronous) and {@link StreamAroundAdvisor} (reactive streaming).
 * Configured with high precedence (ORDER = HIGHEST_PRECEDENCE + 100) to execute before memory or RAG advisors.
 */
public class ContextEnforcerAdvisor implements CallAroundAdvisor, StreamAroundAdvisor {

    public static final String ADVISOR_NAME = "ContextEnforcerAdvisor";
    public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    private final List<EnforcementStep> steps;
    private final ContextEnforcerProperties properties;
    private final ContextEnforcerMetrics metrics;

    public ContextEnforcerAdvisor(
            List<EnforcementStep> steps,
            ContextEnforcerProperties properties,
            ContextEnforcerMetrics metrics
    ) {
        this.steps = steps != null
                ? steps.stream().sorted(Comparator.comparingInt(Ordered::getOrder)).toList()
                : Collections.emptyList();
        this.properties = properties;
        this.metrics = metrics;
    }

    @Override
    public String getName() {
        return ADVISOR_NAME;
    }

    @Override
    public int getOrder() {
        return DEFAULT_ORDER;
    }

    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest, CallAroundAdvisorChain chain) {
        if (!properties.enabled()) {
            return chain.nextAroundCall(advisedRequest);
        }

        return switch (executePipeline(advisedRequest)) {
            case StepResult.Proceed(var updatedRequest) -> chain.nextAroundCall(updatedRequest);
            case StepResult.Rejected rejected -> handleViolationSync(rejected, advisedRequest.adviseContext());
        };
    }

    @Override
    public Flux<AdvisedResponse> aroundStream(AdvisedRequest advisedRequest, StreamAroundAdvisorChain chain) {
        if (!properties.enabled()) {
            return chain.nextAroundStream(advisedRequest);
        }

        return switch (executePipeline(advisedRequest)) {
            case StepResult.Proceed(var updatedRequest) -> chain.nextAroundStream(updatedRequest);
            case StepResult.Rejected rejected -> Flux.defer(() ->
                    Flux.just(handleViolationSync(rejected, advisedRequest.adviseContext()))
            );
        };
    }

    private StepResult executePipeline(AdvisedRequest initialRequest) {
        long startTime = System.nanoTime();
        AdvisedRequest currentRequest = initialRequest;

        for (EnforcementStep step : steps) {
            StepResult result = step.execute(currentRequest);
            switch (result) {
                case StepResult.Proceed(var nextReq) -> currentRequest = nextReq;
                case StepResult.Rejected rejected -> {
                    if (metrics != null) {
                        metrics.recordRejection(rejected, System.nanoTime() - startTime);
                    }
                    return rejected;
                }
            }
        }

        if (metrics != null) {
            metrics.recordAllowed(System.nanoTime() - startTime);
        }
        return new StepResult.Proceed(currentRequest);
    }

    private AdvisedResponse handleViolationSync(StepResult.Rejected rejected, Map<String, Object> adviseContext) {
        if (properties.onViolation() == ContextEnforcerProperties.ViolationStrategy.THROW_EXCEPTION) {
            throw new ContextViolationException(rejected);
        }

        ChatResponse fallbackChatResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage(properties.fallbackMessage()))
        ));

        Map<String, Object> context = adviseContext != null ? adviseContext : Collections.emptyMap();
        return new AdvisedResponse(fallbackChatResponse, context);
    }

    public List<EnforcementStep> getSteps() {
        return steps;
    }

    public ContextEnforcerProperties getProperties() {
        return properties;
    }

    public ContextEnforcerMetrics getMetrics() {
        return metrics;
    }
}
