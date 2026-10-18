package io.github.contextenforcer.steps;

import io.github.contextenforcer.config.ContextEnforcerProperties;
import io.github.contextenforcer.core.EnforcementStep;
import io.github.contextenforcer.core.StepResult;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Enforcement Step 1 (Order: 10).
 * Strips invisible control/format characters and detects prompt injection / jailbreak patterns.
 * Detection runs on a normalized view of the prompt (NFKC, invisible characters removed, whitespace collapsed)
 * so that obfuscation such as zero-width characters, full-width letters or extra spacing cannot bypass it.
 * Every user message of the prompt is checked and sanitized, not only the current one.
 */
public class SanitizationStep implements EnforcementStep {

    public static final int ORDER = 10;
    public static final String STEP_NAME = "SanitizationStep";

    // Control chars (except CR, LF, TAB) and Unicode format chars (zero-width spaces, bidi overrides, etc.)
    private static final Pattern INVISIBLE_CHARS = Pattern.compile("[\\p{Cc}\\p{Cf}&&[^\r\n\t]]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern INJECTION_PATTERN = Pattern.compile(
            "(?i)(ignore (all |the |any )?(previous|prior|above|earlier) instructions"
                    + "|disregard (all |the |any )?((previous|prior|above|earlier) )?instructions"
                    + "|forget (everything|all (previous|prior) instructions)"
                    + "|system prompt|you are now|bypass (all |the )?constraints|dan mode|jailbreak)"
    );

    private final ContextEnforcerProperties properties;
    private final List<Pattern> customInjectionPatterns;

    public SanitizationStep(ContextEnforcerProperties properties) {
        this.properties = properties;
        this.customInjectionPatterns = properties == null
                ? List.of()
                : properties.sanitization().customInjectionPatterns().stream()
                        .filter(p -> p != null && !p.isBlank())
                        .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
                        .toList();
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
        String visibleUserText = INVISIBLE_CHARS.matcher(EnforcementStep.userText(request)).replaceAll("");
        if (visibleUserText.isBlank()) {
            return new StepResult.Rejected(
                    getName(),
                    StepResult.ViolationType.SCHEMA_VALIDATION_FAILED,
                    "Prompt text must not be null or blank."
            );
        }

        boolean strip = properties.sanitization().stripControlChars();
        List<Message> sanitizedMessages = new ArrayList<>();
        for (Message message : request.prompt().getInstructions()) {
            if (!(message instanceof UserMessage userMessage) || userMessage.getText() == null) {
                sanitizedMessages.add(message);
                continue;
            }

            String text = userMessage.getText();
            String visible = INVISIBLE_CHARS.matcher(text).replaceAll("");
            if (properties.sanitization().blockInjectionPatterns() && containsInjection(visible)) {
                return new StepResult.Rejected(
                        getName(),
                        StepResult.ViolationType.PROMPT_INJECTION_DETECTED,
                        "Prompt injection or jailbreak pattern detected in user request."
                );
            }
            sanitizedMessages.add(userMessage.mutate().text((strip ? visible : text).trim()).build());
        }

        ChatClientRequest updatedRequest = request.mutate()
                .prompt(request.prompt().mutate().messages(sanitizedMessages).build())
                .build();

        return new StepResult.Proceed(updatedRequest);
    }

    private boolean containsInjection(String visibleText) {
        String normalized = Normalizer.normalize(visibleText, Normalizer.Form.NFKC);
        // NFKC may produce new format chars from compatibility forms, so strip again before collapsing whitespace
        normalized = WHITESPACE.matcher(INVISIBLE_CHARS.matcher(normalized).replaceAll("")).replaceAll(" ");

        if (INJECTION_PATTERN.matcher(normalized).find()) {
            return true;
        }
        for (Pattern custom : customInjectionPatterns) {
            if (custom.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }
}
