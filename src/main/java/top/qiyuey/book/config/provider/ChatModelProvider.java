package top.qiyuey.book.config.provider;

import org.springframework.ai.chat.model.ChatModel;

/**
 * ChatModel 提供商接口
 * 每个模型供应商实现此接口，遵循开闭原则
 */
public interface ChatModelProvider {

    /**
     * 判断是否支持该模型
     */
    boolean supports(String modelId);

    /**
     * 创建指定模型的 ChatModel
     */
    ChatModel createChatModel(String modelId);

    /**
     * 提供商优先级，数值越小优先级越高
     */
    default int getOrder() {
        return 0;
    }
}
