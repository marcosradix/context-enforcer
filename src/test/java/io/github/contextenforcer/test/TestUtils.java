package io.github.contextenforcer.test;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Shared test helpers for constructing Spring AI requests and advisor chains.
 */
public final class TestUtils {

    private TestUtils() {}

    public static ChatClientRequest createRequest(String userText) {
        return createRequest(null, userText, Map.of());
    }

    public static ChatClientRequest createRequest(String userText, Map<String, Object> context) {
        return createRequest(null, userText, context);
    }

    public static ChatClientRequest createRequest(String systemText, String userText, Map<String, Object> context) {
        List<Message> messages = new ArrayList<>();
        if (systemText != null) {
            messages.add(new SystemMessage(systemText));
        }
        messages.add(new UserMessage(userText));
        return ChatClientRequest.builder()
                .prompt(new Prompt(messages))
                .context(context)
                .build();
    }

    public static ChatClientRequest createRequestWithoutUserMessage() {
        return ChatClientRequest.builder()
                .prompt(new Prompt(List.of(new AssistantMessage("tool response"))))
                .build();
    }

    public static CallAdvisorChain callChain(Function<ChatClientRequest, ChatClientResponse> next) {
        return new CallAdvisorChain() {
            @Override
            public ChatClientResponse nextCall(ChatClientRequest request) {
                return next.apply(request);
            }

            @Override
            public List<CallAdvisor> getCallAdvisors() {
                return List.of();
            }

            @Override
            public CallAdvisorChain copy(CallAdvisor after) {
                return this;
            }
        };
    }

    public static StreamAdvisorChain streamChain(Function<ChatClientRequest, Flux<ChatClientResponse>> next) {
        return new StreamAdvisorChain() {
            @Override
            public Flux<ChatClientResponse> nextStream(ChatClientRequest request) {
                return next.apply(request);
            }

            @Override
            public List<StreamAdvisor> getStreamAdvisors() {
                return List.of();
            }

            @Override
            public StreamAdvisorChain copy(StreamAdvisor after) {
                return this;
            }
        };
    }
}
