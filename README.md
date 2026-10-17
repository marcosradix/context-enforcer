# ContextEnforcer — Spring AI Guardrails & Context Enforcement Framework

[![CI](https://github.com/marcosradix/context-enforcer/actions/workflows/ci.yml/badge.svg)](https://github.com/marcosradix/context-enforcer/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring_AI-1.0+-blue.svg)](https://spring.io/projects/spring-ai)
[![Java](https://img.shields.io/badge/Java-21%20%7C%2025-orange.svg)](https://openjdk.org/)

**ContextEnforcer** is a modular, high-performance security, guardrails, and context-enforcement starter for **Spring AI** and **Spring Boot 3.4+**.

It leverages the **Chain of Responsibility** pattern to intercept chat requests *before* prompts reach language models or downstream vector stores. By enforcing structural validation, injection prevention, safe contextual enrichment, and semantic intent verification upfront, ContextEnforcer saves LLM tokens, reduces latency, and protects enterprise AI applications against adversarial inputs and hallucinations.

---

## 🏛️ Architecture & Execution Flow

ContextEnforcer acts as a transparent, high-precedence interceptor in the Spring AI pipeline by implementing both `CallAroundAdvisor` (synchronous calls) and `StreamAroundAdvisor` (reactive Flux streaming).

Configured with high order precedence (`Ordered.HIGHEST_PRECEDENCE + 100`), ContextEnforcer executes **before** memory advisors (`MessageChatMemoryAdvisor`) or RAG retrievers (`QuestionAnswerAdvisor`), ensuring malicious inputs are blocked before polluting memory or consuming vector search resources.

```text
               [User Request via ChatClient]
                             │
                             ▼
 ┌───────────────────────────────────────────────────────────────┐
 │                   ContextEnforcerAdvisor                      │
 │                                                               │
 │  1. SanitizationStep        (Order: 10) ──► [Clean / Filter]  │
 │  2. SchemaValidationStep    (Order: 20) ──► [Format & Length] │
 │  3. ContextEnrichmentStep   (Order: 30) ──► [Safe Metadata]   │
 │  4. IntentVerificationStep  (Order: 40) ──► [Semantic Guard]  │
 └───────────────────────────────┬───────────────────────────────┘
                                 │
                 ┌───────────────┴───────────────┐
                 │                               │
        (Proceed: All Passed)           (Rejected: Violated)
                 │                               │
                 ▼                               ▼
     Next Spring AI Advisor            Short-Circuit Pipeline
        (Memory / RAG / LLM)          - Exception or Fallback
                                      - Micrometer Metrics Audit
```

---

## 🛡️ Default Enforcement Steps

| Step | Order | Responsibility | Violation Category |
| :--- | :---: | :--- | :--- |
| **`SanitizationStep`** | `10` | Strips invisible ASCII control characters and intercepts prompt injection / jailbreak patterns (`ignore previous instructions`, `DAN mode`, `system prompt`, etc.). | `PROMPT_INJECTION_DETECTED` / `SCHEMA_VALIDATION_FAILED` |
| **`SchemaValidationStep`** | `20` | Enforces character payload boundaries to prevent Denial of Wallet (token exhaustion) and verifies schema format (e.g., JSON validation if required). | `SCHEMA_VALIDATION_FAILED` |
| **`ContextEnrichmentStep`** | `30` | Securely enriches request metadata (tenantId, user roles, security scopes) into `systemParams` and `adviseContext` without concatenating or modifying `userText`. | `UNAUTHORIZED_CONTEXT` |
| **`IntentVerificationStep`** | `40` | Validates whether the user's intent strictly falls within permissible business domains using embeddings cosine similarity or keyword heuristics. | `OUT_OF_CONTEXT_INTENT` |

---

## 🚀 Quick Start

### 1. Add Dependency

Add the ContextEnforcer starter to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.contextenforcer</groupId>
    <artifactId>context-enforcer-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Or in `build.gradle`:

```groovy
implementation 'io.github.contextenforcer:context-enforcer-spring-boot-starter:1.0.0'
```

### 2. Configure `application.yml`

Configure your guardrails and enforcement behavior declaratively:

```yaml
context-enforcer:
  enabled: true
  on-violation: THROW_EXCEPTION # Options: THROW_EXCEPTION | RETURN_FALLBACK_MESSAGE
  fallback-message: "The request is outside the allowed business context."
  sanitization:
    strip-control-chars: true
    block-injection-patterns: true
  validation:
    max-length: 4000
    allowed-formats:
      - "TEXT"
      - "JSON"
  intent:
    similarity-threshold: 0.78
    allowed-topics:
      - "Financial operations, invoices, payments, and account balances"
      - "Technical support, bug reports, and account setup"
```

### 3. Attach Advisor to `ChatClient`

Include `ContextEnforcerAdvisor` in your `ChatClient` bean:

```java
package com.example.config;

import io.github.contextenforcer.advisor.ContextEnforcerAdvisor;
import io.github.contextenforcer.steps.ContextEnrichmentStep.ContextMetadataProvider;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class AiClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ContextEnforcerAdvisor contextEnforcerAdvisor) {
        return builder
                .defaultSystem("You are a helpful customer support assistant for {department}.")
                .defaultAdvisors(contextEnforcerAdvisor)
                .build();
    }

    /**
     * Supplies contextual metadata (e.g. from Spring Security Context) safely into prompts.
     */
    @Bean
    public ContextMetadataProvider customMetadataProvider() {
        return () -> Map.of(
                "tenantId", "acme-corp",
                "role", "ROLE_USER",
                "department", "BILLING"
        );
    }
}
```

---

## ⚙️ Violation Handling Strategies

ContextEnforcer supports two configurable handling modes when a guardrail fails:

1. **`THROW_EXCEPTION` (Default)**:
   Immediately halts execution and throws `ContextViolationException`. Ideal for REST APIs where violations should map to HTTP 400 / 403 via a `@ControllerAdvice`:
   ```java
   @RestControllerAdvice
   public class GlobalExceptionHandler {
       @ExceptionHandler(ContextViolationException.class)
       public ResponseEntity<Map<String, Object>> handleViolation(ContextViolationException ex) {
           return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                   "error", "Security Guardrail Violation",
                   "step", ex.getStepName(),
                   "type", ex.getViolationType(),
                   "message", ex.getMessage()
           ));
       }
   }
   ```

2. **`RETURN_FALLBACK_MESSAGE`**:
   Short-circuits without calling the LLM and returns a synthetic `AdvisedResponse` containing the configured `fallback-message`. This preserves normal conversational flow in chatbots without raising exceptions.

---

## 🧩 Extending and Customizing

ContextEnforcer is designed according to the **Open/Closed Principle (OCP)**. Every default component is declared with `@ConditionalOnMissingBean` and can be overridden or supplemented.

### Adding a Custom Enforcement Step

Create a Spring Bean implementing `EnforcementStep`:

```java
@Component
public class PiiMaskingStep implements EnforcementStep {

    @Override
    public String getName() {
        return "PiiMaskingStep";
    }

    @Override
    public int getOrder() {
        return 15; // Executes between Sanitization (10) and SchemaValidation (20)
    }

    @Override
    public StepResult execute(AdvisedRequest request) {
        String masked = maskCreditCards(request.userText());
        AdvisedRequest updated = AdvisedRequest.from(request).userText(masked).build();
        return new StepResult.Proceed(updated);
    }

    private String maskCreditCards(String text) {
        return text.replaceAll("\\b(?:\\d[ -]*?){13,16}\\b", "[REDACTED_CARD]");
    }
}
```

### Implementing Custom Intent Strategies

In addition to `EmbeddingIntentVerifierStrategy` (cosine similarity) and `KeywordIntentVerifierStrategy`, you can provide your own SLM (Small Language Model) guardrail strategy:

```java
@Bean
public IntentVerifierStrategy customSlmIntentVerifier() {
    return (userPrompt, contextMetadata) -> {
        // Delegate to a fast local model (e.g. Ollama, Gemini Flash) for binary classification
        return myFastClassifier.isAllowed(userPrompt);
    };
}
```

---

## 📊 Observability & Metrics

ContextEnforcer integrates directly with **Micrometer** and Spring Boot Actuator.

| Metric | Type | Tags | Description |
| :--- | :---: | :--- | :--- |
| `spring.ai.context.enforcer.latency` | Timer | `outcome` (`ALLOWED` \| `REJECTED`), `step` | Execution duration in nanoseconds for the enforcement pipeline. |
| `spring.ai.context.enforcer.rejected.total` | Counter | `step`, `violation_type` | Total number of blocked prompts partitioned by step and violation reason. |

Example Prometheus query:
```promql
rate(spring_ai_context_enforcer_rejected_total[5m])
```

---

## 🛠️ Build & Test

ContextEnforcer requires **Java 21+** (fully verified with **Java 25**) and **Maven 3.9+**.

```bash
# Run unit and integration tests
mvn clean test

# Package starter jar
mvn clean package
```

---

## 📄 License

This project is licensed under the [Apache License, Version 2.0](LICENSE).
