package io.github.contextenforcer.strategy;

import io.github.contextenforcer.config.ContextEnforcerProperties;
import io.github.contextenforcer.steps.IntentVerificationStep.IntentVerifierStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Semantic intent verification strategy based on vector embeddings and cosine similarity.
 * Pre-computes and caches embedding vectors for configured allowed topics during startup.
 * Fails secure: when allowed topics are configured but cannot be embedded, or the prompt cannot be
 * evaluated, the prompt is rejected.
 */
public class EmbeddingIntentVerifierStrategy implements IntentVerifierStrategy, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingIntentVerifierStrategy.class);

    private final EmbeddingModel embeddingModel;
    private final ContextEnforcerProperties properties;
    private final Map<String, float[]> topicEmbeddingsCache = new ConcurrentHashMap<>();

    public EmbeddingIntentVerifierStrategy(EmbeddingModel embeddingModel, ContextEnforcerProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        initCache();
    }

    /**
     * Pre-computes embedding vectors for all configured allowed topics.
     * Topics that fail to embed are retried lazily on the next verification.
     */
    public void initCache() {
        List<String> topics = configuredTopics();
        if (topics.isEmpty()) {
            log.info("ContextEnforcer: No allowed topics configured for semantic intent verification.");
            return;
        }

        log.info("ContextEnforcer: Pre-computing embedding vectors for {} allowed topic(s)...", topics.size());
        embedMissingTopics(topics);
    }

    @Override
    public boolean isWithinAllowedContext(String userPrompt, Map<String, Object> contextMetadata) {
        List<String> topics = configuredTopics();
        if (topics.isEmpty() || userPrompt == null || userPrompt.isBlank()) {
            return true;
        }

        if (topicEmbeddingsCache.size() < topics.size()) {
            embedMissingTopics(topics);
        }
        if (topicEmbeddingsCache.isEmpty()) {
            // Fail-secure: topics are configured but none could be embedded, so the boundary cannot be verified
            log.warn("ContextEnforcer: Prompt rejected because no allowed topic embedding is available.");
            return false;
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
                        String.format(Locale.ROOT, "%.4f", maxSimilarity), threshold);
            }
            return allowed;
        } catch (Exception e) {
            log.error("ContextEnforcer: Error while evaluating semantic similarity: {}", e.getMessage(), e);
            // Fail-secure: reject when similarity cannot be evaluated
            return false;
        }
    }

    private List<String> configuredTopics() {
        if (properties.intent() == null || CollectionUtils.isEmpty(properties.intent().allowedTopics())) {
            return List.of();
        }
        return properties.intent().allowedTopics().stream()
                .filter(topic -> topic != null && !topic.isBlank())
                .distinct()
                .toList();
    }

    private void embedMissingTopics(List<String> topics) {
        for (String topic : topics) {
            if (topicEmbeddingsCache.containsKey(topic)) {
                continue;
            }
            try {
                topicEmbeddingsCache.put(topic, embeddingModel.embed(topic));
                log.debug("ContextEnforcer: Cached embedding vector for topic '{}'", topic);
            } catch (Exception e) {
                log.error("ContextEnforcer: Failed to embed topic '{}': {}", topic, e.getMessage(), e);
            }
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
