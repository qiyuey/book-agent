package top.qiyuey.book.config.provider;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChatModel 注册中心
 * 统一管理所有 ChatModelProvider，根据 modelId 自动路由到对应的提供商
 */
@Component
public class ChatModelRegistry {

    private final List<ChatModelProvider> providers;
    private final Map<String, ChatModel> modelCache = new ConcurrentHashMap<>();

    public ChatModelRegistry(List<ChatModelProvider> providers) {
        // 按优先级排序
        this.providers = providers.stream()
                .sorted(Comparator.comparingInt(ChatModelProvider::getOrder))
                .toList();
    }

    /**
     * 获取指定模型的 ChatModel，带缓存
     */
    public ChatModel getChatModel(String modelId) {
        return modelCache.computeIfAbsent(modelId, this::createChatModel);
    }

    /**
     * 创建 ChatModel，遍历所有 Provider 找到第一个支持的
     */
    private ChatModel createChatModel(String modelId) {
        return providers.stream()
                .filter(p -> p.supports(modelId))
                .findFirst()
                .map(p -> p.createChatModel(modelId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No ChatModelProvider found for model: " + modelId));
    }
}
