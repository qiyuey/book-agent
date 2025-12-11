package top.qiyuey.book.agent;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 读书问答 Agent 工厂
 * 支持动态创建使用不同模型的 Agent
 */
@Component
public class BookAgentFactory {

    private static final String SYSTEM_PROMPT = """
            # Role: 书籍原文深度解读专家

            ## 核心定位
            你是一位兼具学术深度与实践智慧的**原文解读专家**。你的核心能力是：
            1. 精准拆解原文的逻辑结构和核心论点
            2. 追溯概念的思想源流和历史背景
            3. 将抽象理论映射到现代生活的具体场景

            **你的格言**：理论必须落地，概念必须具象。

            ## 刚性约束 (必须遵守)
            1. **忠于原文**：解读必须紧扣用户提供的原文，不能脱离文本空谈
            2. **拒绝学究气**：不堆砌术语，用大白话讲透深刻道理
            3. **强制案例**：每个核心概念必须配一个现代生活/职场的具体例子
            4. **语气专业**：保持冷峻、客观、有洞察力，像一位读透万卷书的智者

            ## 输出结构 (Strict Output Format)
            请严格按照以下板块输出：

            ### 1. 原文拆解
            > 逐句/逐层分析原文的逻辑结构：
            > - 这段话的核心论点是什么？
            > - 关键概念有哪些？各自含义是什么？
            > - 论证逻辑是怎样展开的？

            ### 2. 思想溯源
            > 简要说明：
            > - 这段话出自哪本书/哪篇文章？什么背景下写的？
            > - 作者为什么要提出这个观点？要解决什么问题？
            > - 与其他思想流派有何异同？（如适用）

            ### 3. 现代映射
            > **必须**用 1-2 个具体的现代场景来演绎这段话的智慧：
            > - 在职场/生活/学习中，这个道理具体怎么体现？
            > - 正面案例：掌握这个智慧的人会怎么做？
            > - 反面案例：不懂这个道理的人常犯什么错？

            ### 4. 一句话精华
            > 用一句现代大白话，总结这段原文的核心智慧。要求：朗朗上口，便于记忆。
            """;

    private final DashScopeApi dashScopeApi;
    private final BaseCheckpointSaver checkpointSaver;

    /**
     * 缓存已创建的 Agent，避免重复创建
     */
    private final Map<String, ReactAgent> agentCache = new ConcurrentHashMap<>();

    public BookAgentFactory(DashScopeApi dashScopeApi,
                            BaseCheckpointSaver checkpointSaver) {
        this.dashScopeApi = dashScopeApi;
        this.checkpointSaver = checkpointSaver;
    }

    /**
     * 获取指定模型的 Agent
     * 如果缓存中存在则返回缓存的 Agent，否则创建新的
     */
    public ReactAgent getAgent(String modelId) {
        return agentCache.computeIfAbsent(modelId, this::createAgent);
    }

    /**
     * 创建指定模型的 Agent
     */
    private ReactAgent createAgent(String modelId) {
        // 创建指定模型的 ChatModel
        ChatModel chatModel = createChatModel(modelId);

        return ReactAgent.builder()
                .name("BookAgent-" + modelId)
                .model(chatModel)
                .systemPrompt(SYSTEM_PROMPT)
                .enableLogging(true)
                .saver(checkpointSaver)
                .build();
    }

    /**
     * 创建指定模型的 ChatModel
     */
    private ChatModel createChatModel(String modelId) {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .model(modelId)
                .temperature(0.7)
                .build();

        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(options)
                .build();
    }

}
