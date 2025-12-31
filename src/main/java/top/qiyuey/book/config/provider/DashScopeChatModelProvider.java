package top.qiyuey.book.config.provider;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * DashScope (阿里云百炼) ChatModel 提供商
 */
@Component
public class DashScopeChatModelProvider implements ChatModelProvider {

    private final DashScopeApi dashScopeApi;

    public DashScopeChatModelProvider(DashScopeApi dashScopeApi) {
        this.dashScopeApi = dashScopeApi;
    }

    @Override
    public boolean supports(String modelId) {
        // DashScope 作为默认提供商，支持所有非 OpenAI 模型
        // 优先级最低，其他 Provider 不支持时由此兜底
        return true;
    }

    @Override
    public ChatModel createChatModel(String modelId) {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model(modelId)
                .temperature(0.7)
                .build();

        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(options)
                .build();
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE; // 最低优先级，作为兜底
    }
}
