package io.github.contextenforcer.test;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

/**
 * Shared test helpers for constructing valid Spring AI test models and requests.
 */
public final class TestUtils {

    private TestUtils() {}

    public static final ChatModel DUMMY_CHAT_MODEL = new ChatModel() {
        @Override
        public ChatResponse call(Prompt prompt) {
            return null;
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return null;
        }
    };

    public static AdvisedRequest createRequest(String userText) {
        return AdvisedRequest.builder()
                .chatModel(DUMMY_CHAT_MODEL)
                .userText(userText)
                .build();
    }

    public static AdvisedRequest createEmptyUserTextRequest(String userText) {
        return AdvisedRequest.builder()
                .chatModel(DUMMY_CHAT_MODEL)
                .userText(userText)
                .messages(List.of(new AssistantMessage("tool response")))
                .build();
    }

    public static AdvisedRequest createRequest(String userText, Map<String, Object> systemParams, Map<String, Object> adviseContext) {
        return AdvisedRequest.builder()
                .chatModel(DUMMY_CHAT_MODEL)
                .userText(userText)
                .systemParams(systemParams)
                .adviseContext(adviseContext)
                .build();
    }
}
