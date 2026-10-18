package io.github.contextenforcer.steps;

import io.github.contextenforcer.core.EnforcementStep;
import io.github.contextenforcer.core.StepResult;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enforcement Step 3 (Order: 30).
 * Safely enriches the ChatClientRequest with application runtime context (e.g. user identity,
 * tenant, security roles), keeping the user text strictly isolated:
 * <ul>
 *     <li>metadata is added to the request {@link ChatClientRequest#context() context}, visible to downstream
 *     advisors and intent strategies;</li>
 *     <li>{@code {key}} placeholders left in system messages are replaced with the matching metadata value.
 *     Spring AI only renders the system template when system params are supplied, so unrendered placeholders
 *     reach this step intact. Replacement is literal: values are never interpreted as templates.</li>
 * </ul>
 */
public class ContextEnrichmentStep implements EnforcementStep {

    public static final int ORDER = 30;
    public static final String STEP_NAME = "ContextEnrichmentStep";

    @FunctionalInterface
    public interface ContextMetadataProvider {
        Map<String, Object> provideMetadata();
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([\\w.-]+)}");

    private final ContextMetadataProvider metadataProvider;

    public ContextEnrichmentStep(ContextMetadataProvider metadataProvider) {
        this.metadataProvider = metadataProvider;
    }

    @Override
    public String getName() {
        return STEP_NAME;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public StepResult execute(ChatClientRequest request) {
        Map<String, Object> securityMetadata = metadataProvider != null ? metadataProvider.provideMetadata() : null;
        if (securityMetadata == null || securityMetadata.isEmpty()) {
            return new StepResult.Proceed(request);
        }

        Map<String, Object> updatedContext = new HashMap<>(request.context());
        updatedContext.putAll(securityMetadata);

        List<Message> messages = request.prompt().getInstructions().stream()
                .map(message -> message instanceof SystemMessage systemMessage
                        ? fillPlaceholders(systemMessage, securityMetadata)
                        : message)
                .toList();

        ChatClientRequest enrichedRequest = request.mutate()
                .prompt(request.prompt().mutate().messages(messages).build())
                .context(updatedContext)
                .build();

        return new StepResult.Proceed(enrichedRequest);
    }

    private static Message fillPlaceholders(SystemMessage message, Map<String, Object> metadata) {
        String text = message.getText();
        if (text == null || text.indexOf('{') < 0) {
            return message;
        }
        // Single pass, so a value containing "{otherKey}" is never expanded again
        String filled = PLACEHOLDER.matcher(text).replaceAll(match -> Matcher.quoteReplacement(
                metadata.containsKey(match.group(1)) ? String.valueOf(metadata.get(match.group(1))) : match.group()));
        return message.mutate().text(filled).build();
    }

    public ContextMetadataProvider getMetadataProvider() {
        return metadataProvider;
    }
}
