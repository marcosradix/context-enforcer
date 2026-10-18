package io.github.contextenforcer.core;

import org.springframework.ai.chat.client.ChatClientRequest;
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
     * @param request current ChatClientRequest
     * @return {@link StepResult.Proceed} if passed, or {@link StepResult.Rejected} if violated
     */
    StepResult execute(ChatClientRequest request);

    /**
     * Returns the text of the current (last) user message of the request, or an empty string if there is none.
     *
     * @param request the request to read from
     * @return the user text, never null
     */
    static String userText(ChatClientRequest request) {
        String text = request.prompt().getUserMessage().getText();
        return text != null ? text : "";
    }

    /**
     * Returns a copy of the request whose current (last) user message text is replaced, preserving media and metadata.
     *
     * @param request the request to copy
     * @param text    the new user text
     * @return the updated request
     */
    static ChatClientRequest withUserText(ChatClientRequest request, String text) {
        return request.mutate()
                .prompt(request.prompt().augmentUserMessage(text))
                .build();
    }
}
