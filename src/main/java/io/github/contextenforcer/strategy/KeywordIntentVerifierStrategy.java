package io.github.contextenforcer.strategy;

import io.github.contextenforcer.steps.IntentVerificationStep.IntentVerifierStrategy;

import java.util.List;
import java.util.Map;

/**
 * Heuristic keyword-based intent verification strategy.
 * Useful for lightweight environments without embedding models or as a rapid filter.
 */
public class KeywordIntentVerifierStrategy implements IntentVerifierStrategy {

    private final List<String> allowedKeywords;
    private final List<String> blockedKeywords;

    public KeywordIntentVerifierStrategy(List<String> allowedKeywords, List<String> blockedKeywords) {
        this.allowedKeywords = allowedKeywords != null ? allowedKeywords : List.of();
        this.blockedKeywords = blockedKeywords != null ? blockedKeywords : List.of();
    }

    @Override
    public boolean isWithinAllowedContext(String userPrompt, Map<String, Object> contextMetadata) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return false;
        }

        String lowerPrompt = userPrompt.toLowerCase();

        // Check blocked keywords first
        for (String blocked : blockedKeywords) {
            if (lowerPrompt.contains(blocked.toLowerCase())) {
                return false;
            }
        }

        // If allowed keywords list is specified, at least one must match
        if (!allowedKeywords.isEmpty()) {
            boolean matched = false;
            for (String allowed : allowedKeywords) {
                if (lowerPrompt.contains(allowed.toLowerCase())) {
                    matched = true;
                    break;
                }
            }
            return matched;
        }

        return true;
    }
}
