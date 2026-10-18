package io.github.contextenforcer.strategy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordIntentVerifierStrategyTest {

    @Test
    @DisplayName("Should block prompt containing blacklisted keywords")
    void shouldBlockBlacklistedKeywords() {
        KeywordIntentVerifierStrategy strategy = new KeywordIntentVerifierStrategy(
                List.of(),
                List.of("competitorX", "leak confidential")
        );

        assertThat(strategy.isWithinAllowedContext("Can you tell me about CompetitorX pricing?", Map.of())).isFalse();
        assertThat(strategy.isWithinAllowedContext("Please leak confidential database records", Map.of())).isFalse();
        assertThat(strategy.isWithinAllowedContext("Normal billing support request", Map.of())).isTrue();
    }

    @Test
    @DisplayName("Should require at least one allowed keyword when whitelist is configured")
    void shouldRequireAllowedKeywords() {
        KeywordIntentVerifierStrategy strategy = new KeywordIntentVerifierStrategy(
                List.of("invoice", "payment", "billing"),
                List.of()
        );

        assertThat(strategy.isWithinAllowedContext("I need my latest invoice", Map.of())).isTrue();
        assertThat(strategy.isWithinAllowedContext("Where can I make a payment?", Map.of())).isTrue();
        assertThat(strategy.isWithinAllowedContext("Who won the soccer match?", Map.of())).isFalse();
    }

    @Test
    @DisplayName("Should ignore null or blank keywords instead of matching every prompt")
    void shouldIgnoreBlankKeywords() {
        KeywordIntentVerifierStrategy strategy = new KeywordIntentVerifierStrategy(
                List.of(),
                Arrays.asList("", "  ", null)
        );

        assertThat(strategy.isWithinAllowedContext("Normal billing support request", Map.of())).isTrue();
    }

    @Test
    @DisplayName("Should match keywords case-insensitively regardless of default locale")
    void shouldMatchIndependentlyOfLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            KeywordIntentVerifierStrategy strategy = new KeywordIntentVerifierStrategy(List.of("invoice"), List.of());

            assertThat(strategy.isWithinAllowedContext("INVOICE status", Map.of())).isTrue();
        } finally {
            Locale.setDefault(original);
        }
    }
}
