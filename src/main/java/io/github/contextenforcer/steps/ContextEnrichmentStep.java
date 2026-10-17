package io.github.contextenforcer.steps;

import io.github.contextenforcer.core.EnforcementStep;
import io.github.contextenforcer.core.StepResult;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Enforcement Step 3 (Order: 30).
 * Safely enriches the AdvisedRequest with application runtime context (e.g. user identity,
 * tenant, security roles) directly into systemParams and adviseContext, keeping userText strictly isolated.
 */
public class ContextEnrichmentStep implements EnforcementStep {

    public static final int ORDER = 30;
    public static final String STEP_NAME = "ContextEnrichmentStep";

    @FunctionalInterface
    public interface ContextMetadataProvider {
        Map<String, Object> provideMetadata();
    }

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
    public StepResult execute(AdvisedRequest request) {
        Map<String, Object> securityMetadata = metadataProvider != null ? metadataProvider.provideMetadata() : null;
        if (securityMetadata == null || securityMetadata.isEmpty()) {
            return new StepResult.Proceed(request);
        }

        Map<String, Object> updatedSystemParams = new HashMap<>();
        if (request.systemParams() != null) {
            updatedSystemParams.putAll(request.systemParams());
        }
        updatedSystemParams.putAll(securityMetadata);

        Map<String, Object> updatedAdviseContext = new HashMap<>();
        if (request.adviseContext() != null) {
            updatedAdviseContext.putAll(request.adviseContext());
        }
        updatedAdviseContext.putAll(securityMetadata);

        AdvisedRequest enrichedRequest = AdvisedRequest.from(request)
                .systemParams(updatedSystemParams)
                .adviseContext(updatedAdviseContext)
                .build();

        return new StepResult.Proceed(enrichedRequest);
    }

    public ContextMetadataProvider getMetadataProvider() {
        return metadataProvider;
    }
}
