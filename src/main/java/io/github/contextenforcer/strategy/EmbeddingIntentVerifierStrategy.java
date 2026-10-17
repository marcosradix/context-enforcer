package io.github.contextenforcer.strategy;

import io.github.contextenforcer.config.ContextEnforcerProperties;
import io.github.contextenforcer.steps.IntentVerificationStep.IntentVerifierStrategy;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Semantic intent verification strategy based on vector embeddings and cosine similarity.
 * Pre-computes and caches embedding vectors for configured allowed topics during startup.
 */
public class EmbeddingIntentVerifierStrategy implements IntentVerifierStrategy {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingIntentVerifierStrategy.class);

    private final EmbeddingModel embeddingModel;
    private final ContextEnforcerProperties properties;
    private final Map<String, float[]> topicEmbeddingsCache = new ConcurrentHashMap<>();

    public EmbeddingIntentVerifierStrategy(EmbeddingModel embeddingModel, ContextEnforcerProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @PostConstruct
    public void initCache() {
        if (properties.intent() == null) {
            return;
        }
        List<String> topics = properties.intent().allowedTopics();
        if (CollectionUtils.isEmpty(topics)) {
            log.info("ContextEnforcer: No allowed topics configured for semantic intent verification.");
            return;
        }

        log.info("ContextEnforcer: Pre-computing embedding vectors for {} allowed topic(s)...", topics.size());
        for (String topic : topics) {
            if (topic != null && !topic.isBlank()) {
                try {
                    float[] vector = embeddingModel.embed(topic);
                    topicEmbeddingsCache.put(topic, vector);
                    log.debug("ContextEnforcer: Cached embedding vector for topic '{}'", topic);
                } catch (Exception e) {
                    log.error("ContextEnforcer: Failed to embed topic '{}': {}", topic, e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public boolean isWithinAllowedContext(String userPrompt, Map<String, Object> contextMetadata) {
        if (topicEmbeddingsCache.isEmpty() || userPrompt == null || userPrompt.isBlank()) {
            return true;
        }

        try {
            float[] promptVector = embeddingModel.embed(userPrompt);
            double threshold = properties.intent().similarityThreshold();

            double maxSimilarity = topicEmbeddingsCache.values().stream()
                    .mapToDouble(topicVector -> cosineSimilarity(promptVector, topicVector))
                    .max()
                    .orElse(0.0);

            boolean allowed = maxSimilarity >= threshold;
            if (!allowed) {
                log.warn("ContextEnforcer: Prompt rejected by semantic intent verification. Max similarity: {}, threshold: {}",
                        String.format("%.4f", maxSimilarity), threshold);
            }
            return allowed;
        } catch (Exception e) {
            log.error("ContextEnforcer: Error while evaluating semantic similarity: {}", e.getMessage(), e);
            // Default to fail-secure or log warning
            return false;
        }
    }

    /**
     * Calculates cosine similarity between two float vectors: cos(θ) = (A · B) / (||A|| * ||B||).
     */
    public static double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null) {
            return 0.0;
        }
        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException(
                    "Embedding vector dimensions do not match: %d != %d".formatted(vectorA.length, vectorB.length)
            );
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += (double) vectorA[i] * vectorB[i];
            normA += (double) vectorA[i] * vectorA[i];
            normB += (double) vectorB[i] * vectorB[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public Map<String, float[]> getTopicEmbeddingsCache() {
        return topicEmbeddingsCache;
    }
}
