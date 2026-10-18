package io.github.contextenforcer.strategy;

import io.github.contextenforcer.steps.IntentVerificationStep.IntentVerifierStrategy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Heuristic keyword-based intent verification strategy.
 * Useful for lightweight environments without embedding models or as a rapid filter.
 * Matching is case-insensitive and independent of the JVM default locale; null or blank keywords are ignored.
 */
public class KeywordIntentVerifierStrategy implements IntentVerifierStrategy {

    private final List<String> allowedKeywords;
    private final List<String> blockedKeywords;

    public KeywordIntentVerifierStrategy(List<String> allowedKeywords, List<String> blockedKeywords) {
        this.allowedKeywords = normalize(allowedKeywords);
        this.blockedKeywords = normalize(blockedKeywords);
    }

    @Override
    public boolean isWithinAllowedContext(String userPrompt, Map<String, Object> contextMetadata) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return false;
        }

        String lowerPrompt = userPrompt.toLowerCase(Locale.ROOT);

        // Check blocked keywords first
        if (blockedKeywords.stream().anyMatch(lowerPrompt::contains)) {
            return false;
        }

        // If allowed keywords list is specified, at least one must match
        return allowedKeywords.isEmpty() || allowedKeywords.stream().anyMatch(lowerPrompt::contains);
    }

    private static List<String> normalize(List<String> keywords) {
        if (keywords == null) {
            return List.of();
        }
        return keywords.stream()
                .filter(Objects::nonNull)
                .filter(k -> !k.isBlank())
                .map(k -> k.toLowerCase(Locale.ROOT))
                .toList();
    }
}
