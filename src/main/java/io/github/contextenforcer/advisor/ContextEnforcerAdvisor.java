package io.github.contextenforcer.advisor;

import io.github.contextenforcer.config.ContextEnforcerProperties;
import io.github.contextenforcer.core.EnforcementStep;
import io.github.contextenforcer.core.StepResult;
import io.github.contextenforcer.exception.ContextViolationException;
import io.github.contextenforcer.observability.ContextEnforcerMetrics;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Primary Spring AI Advisor enforcing context, guardrails, and security.
 * Implements both {@link CallAdvisor} (synchronous) and {@link StreamAdvisor} (reactive streaming).
 * Configured with high precedence (ORDER = HIGHEST_PRECEDENCE + 100) to execute before memory or RAG advisors.
 */
public class ContextEnforcerAdvisor implements CallAdvisor, StreamAdvisor {

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
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        if (!properties.enabled()) {
            return chain.nextCall(request);
        }

        return switch (executePipeline(request)) {
            case StepResult.Proceed(var updatedRequest) -> chain.nextCall(updatedRequest);
            case StepResult.Rejected rejected -> handleViolation(rejected, request.context());
        };
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        if (!properties.enabled()) {
            return chain.nextStream(request);
        }

        // Steps may block (e.g. embedding calls), so run the pipeline on subscription and off the caller's thread
        return Mono.just(request)
                .publishOn(Schedulers.boundedElastic())
                .map(this::executePipeline)
                .flatMapMany(result -> switch (result) {
                    case StepResult.Proceed(var updatedRequest) -> chain.nextStream(updatedRequest);
                    case StepResult.Rejected rejected -> Flux.just(handleViolation(rejected, request.context()));
                });
    }

    private StepResult executePipeline(ChatClientRequest initialRequest) {
        long startTime = System.nanoTime();
        ChatClientRequest currentRequest = initialRequest;

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

    private ChatClientResponse handleViolation(StepResult.Rejected rejected, Map<String, Object> context) {
        if (properties.onViolation() == ContextEnforcerProperties.ViolationStrategy.THROW_EXCEPTION) {
            throw new ContextViolationException(rejected);
        }

        ChatResponse fallbackChatResponse = new ChatResponse(List.of(
                new Generation(new AssistantMessage(properties.fallbackMessage()))
        ));

        return ChatClientResponse.builder()
                .chatResponse(fallbackChatResponse)
                .context(context != null ? context : Collections.emptyMap())
                .build();
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
