package io.github.contextenforcer.strategy;

import io.github.contextenforcer.config.ContextEnforcerProperties;
import io.github.contextenforcer.config.ContextEnforcerProperties.IntentConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbeddingIntentVerifierStrategyTest {

    @Test
    @DisplayName("Should correctly compute cosine similarity between vectors")
    void shouldComputeCosineSimilarityAccurately() {
        // Parallel vectors -> similarity 1.0
        float[] v1 = new float[]{1.0f, 0.0f, 0.0f};
        float[] v2 = new float[]{2.0f, 0.0f, 0.0f};
        assertThat(EmbeddingIntentVerifierStrategy.cosineSimilarity(v1, v2)).isCloseTo(1.0, offset(1e-5));

        // Orthogonal vectors -> similarity 0.0
        float[] v3 = new float[]{0.0f, 1.0f, 0.0f};
        assertThat(EmbeddingIntentVerifierStrategy.cosineSimilarity(v1, v3)).isCloseTo(0.0, offset(1e-5));

        // Opposite vectors -> similarity -1.0
        float[] v4 = new float[]{-1.0f, 0.0f, 0.0f};
        assertThat(EmbeddingIntentVerifierStrategy.cosineSimilarity(v1, v4)).isCloseTo(-1.0, offset(1e-5));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when vector dimensions differ")
    void shouldThrowOnMismatchedDimensions() {
        float[] v1 = new float[]{1.0f, 2.0f};
        float[] v2 = new float[]{1.0f, 2.0f, 3.0f};

        assertThatThrownBy(() -> EmbeddingIntentVerifierStrategy.cosineSimilarity(v1, v2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Embedding vector dimensions do not match");
    }

    @Test
    @DisplayName("Should accept prompt when semantic similarity meets threshold")
    void shouldAcceptWhenSimilarityMeetsThreshold() {
        EmbeddingModel mockModel = mock(EmbeddingModel.class);
        String topic = "Financial operations and invoices";
        when(mockModel.embed(topic)).thenReturn(new float[]{1.0f, 0.0f, 0.0f});

        ContextEnforcerProperties properties = new ContextEnforcerProperties(
                true,
                ContextEnforcerProperties.ViolationStrategy.THROW_EXCEPTION,
                "Fallback",
                null,
                null,
                new IntentConfig(0.75, List.of(topic))
        );

        EmbeddingIntentVerifierStrategy strategy = new EmbeddingIntentVerifierStrategy(mockModel, properties);
        strategy.initCache();

        // Prompt is highly aligned with topic vector (similarity 0.8)
        when(mockModel.embed("Pay bill")).thenReturn(new float[]{0.8f, 0.6f, 0.0f});

        boolean allowed = strategy.isWithinAllowedContext("Pay bill", Map.of());
        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("Should reject prompt when semantic similarity is below threshold")
    void shouldRejectWhenSimilarityBelowThreshold() {
        EmbeddingModel mockModel = mock(EmbeddingModel.class);
        String topic = "Financial operations and invoices";
        when(mockModel.embed(topic)).thenReturn(new float[]{1.0f, 0.0f, 0.0f});

        ContextEnforcerProperties properties = new ContextEnforcerProperties(
                true,
                ContextEnforcerProperties.ViolationStrategy.THROW_EXCEPTION,
                "Fallback",
                null,
                null,
                new IntentConfig(0.75, List.of(topic))
        );

        EmbeddingIntentVerifierStrategy strategy = new EmbeddingIntentVerifierStrategy(mockModel, properties);
        strategy.initCache();

        // Prompt is orthogonal / completely off-topic (similarity 0.0)
        when(mockModel.embed("How to play tennis")).thenReturn(new float[]{0.0f, 1.0f, 0.0f});

        boolean allowed = strategy.isWithinAllowedContext("How to play tennis", Map.of());
        assertThat(allowed).isFalse();
    }

    @Test
    @DisplayName("Should fail secure when topics are configured but none could be embedded")
    void shouldRejectWhenTopicEmbeddingFailed() {
        EmbeddingModel mockModel = mock(EmbeddingModel.class);
        String topic = "Financial operations and invoices";
        when(mockModel.embed(topic)).thenThrow(new IllegalStateException("provider down"));

        EmbeddingIntentVerifierStrategy strategy = new EmbeddingIntentVerifierStrategy(mockModel, propertiesWith(topic));
        strategy.afterPropertiesSet();

        assertThat(strategy.isWithinAllowedContext("Pay bill", Map.of())).isFalse();
    }

    @Test
    @DisplayName("Should lazily retry embedding topics that failed during startup")
    void shouldRetryFailedTopicEmbedding() {
        EmbeddingModel mockModel = mock(EmbeddingModel.class);
        String topic = "Financial operations and invoices";
        when(mockModel.embed(topic))
                .thenThrow(new IllegalStateException("provider down"))
                .thenReturn(new float[]{1.0f, 0.0f, 0.0f});
        when(mockModel.embed("Pay bill")).thenReturn(new float[]{0.8f, 0.6f, 0.0f});

        EmbeddingIntentVerifierStrategy strategy = new EmbeddingIntentVerifierStrategy(mockModel, propertiesWith(topic));
        strategy.afterPropertiesSet();

        assertThat(strategy.isWithinAllowedContext("Pay bill", Map.of())).isTrue();
        assertThat(strategy.getTopicEmbeddingsCache()).containsKey(topic);
    }

    private static ContextEnforcerProperties propertiesWith(String... topics) {
        return new ContextEnforcerProperties(
                true,
                ContextEnforcerProperties.ViolationStrategy.THROW_EXCEPTION,
                "Fallback",
                null,
                null,
                new IntentConfig(0.75, List.of(topics))
        );
    }
}
