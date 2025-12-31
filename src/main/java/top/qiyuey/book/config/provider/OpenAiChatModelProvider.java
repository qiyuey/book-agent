package top.qiyuey.book.config.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * OpenAI ChatModel 提供商
 * 支持 HTTP 和 SOCKS5 代理配置
 */
@Slf4j
@Component
public class OpenAiChatModelProvider implements ChatModelProvider {

    private static final Set<String> MODEL_PREFIXES = Set.of("gpt-", "o1", "o3", "o4");

    private final OpenAiApi openAiApi;

    public OpenAiChatModelProvider(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.base-url:https://api.openai.com}") String baseUrl,
            OpenAiRestClientFactory restClientFactory) {

        if (apiKey != null && !apiKey.isBlank()) {
            this.openAiApi = OpenAiApi.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .webClientBuilder(restClientFactory.createWebClientBuilder())
                    .build();
            log.info("OpenAI API initialized with base URL: {}", baseUrl);
        } else {
            this.openAiApi = null;
            log.warn("OpenAI API key not configured, OpenAI models will be unavailable");
        }
    }

    @Override
    public boolean supports(String modelId) {
        if (modelId == null || openAiApi == null) {
            return false;
        }
        String lower = modelId.toLowerCase();
        return MODEL_PREFIXES.stream().anyMatch(lower::startsWith);
    }

    @Override
    public ChatModel createChatModel(String modelId) {
        if (openAiApi == null) {
            throw new IllegalStateException(
                    "OpenAI API is not configured. Please set OPENAI_API_KEY environment variable.");
        }

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(modelId)
                .temperature(0.7)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }

}
