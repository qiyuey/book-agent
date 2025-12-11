package top.qiyuey.book.agent;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
public class BookService {

    private final BookAgentFactory agentFactory;

    public BookService(BookAgentFactory agentFactory) {
        this.agentFactory = agentFactory;
    }

    /**
     * 执行读书问答逻辑
     *
     * @param question 用户问题/原文内容
     * @param bookName 书籍名称（可选）
     * @param threadId 会话 ID
     * @param modelId  模型 ID
     * @param mode     模式：interpret=解读，chat=问答
     * @return 响应事件流
     */
    public Flux<BookResponseEvent> executeBookQuery(String question, String bookName, String threadId, String modelId, String mode) {
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        // 获取指定模型的 Agent
        ReactAgent agent = agentFactory.getAgent(modelId);

        // 构建用户消息，根据模式不同处理
        String userMessage = buildUserMessage(question, bookName, mode);

        // 1. 起始事件
        String bookInfo = (bookName != null && !bookName.isBlank()) ? String.format(" [%s]", bookName) : "";
        String modeLabel = "chat".equals(mode) ? "回答" : "解读";
        BookResponseEvent startEvent = BookResponseEvent.builder()
                .status(BookResponseEvent.Status.START)
                .content(String.format("正在%s%s... (模型: %s)", modeLabel, bookInfo, modelId))
                .build();

        // 2. Agent流转换
        Flux<BookResponseEvent> agentStream;
        try {
            agentStream = agent.stream(userMessage, config)
                    .flatMap(output -> {
                        // 调试日志
                        if (log.isDebugEnabled()) {
                            String delta = (output instanceof StreamingOutput<?> s)
                                    ? safeStreamingText(s)
                                    : "N/A";
                            log.debug("Agent Output: class={}, text={}, state={}",
                                    output.getClass().getSimpleName(),
                                    delta,
                                    output.state());
                        }

                        // 尝试获取消息
                        Message message = null;
                        if (output instanceof StreamingOutput<?> so) {
                            message = so.message();
                        } else if (output.state() != null && output.state().data().containsKey("messages")) {
                            Object messagesObj = output.state().data().get("messages");
                            if (messagesObj instanceof List<?> list && !list.isEmpty()) {
                                Object lastItem = list.getLast();
                                if (lastItem instanceof Message m) {
                                    message = m;
                                }
                            }
                        }

                        // 处理流式文本输出
                        if (output instanceof StreamingOutput<?> streamingOutput) {
                            String text = safeStreamingText(streamingOutput);
                            if (text != null && !text.isEmpty()) {
                                return Flux.just(BookResponseEvent.builder()
                                        .status(BookResponseEvent.Status.PROGRESS)
                                        .content(text)
                                        .build());
                            }
                        }
                        // 处理非流式最终结果
                        else {
                            try {
                                if (message instanceof AssistantMessage am) {
                                    String content = am.getText();
                                    if (content != null && !content.isEmpty()) {
                                        return Flux.just(BookResponseEvent.builder()
                                                .status(BookResponseEvent.Status.PROGRESS)
                                                .content(content)
                                                .build());
                                    }
                                }
                            } catch (Exception e) {
                                log.warn("提取非流式内容失败", e);
                            }
                        }

                        return Flux.empty();
                    });
        } catch (Exception e) {
            log.error("创建 Agent 流失败", e);
            return Flux.concat(
                    Flux.just(startEvent),
                    Flux.just(BookResponseEvent.builder()
                            .status(BookResponseEvent.Status.ERROR)
                            .content("处理失败: " + e.getMessage())
                            .build())
            );
        }

        // 3. 组合流
        return Flux.concat(Flux.just(startEvent), agentStream);
    }

    /**
     * 提取 StreamingOutput 文本，兼容新旧 API。
     * 优先尝试新的方法名：text()/delta()/content()，否则回退到 chunk()（已废弃）。
     */
    private String safeStreamingText(StreamingOutput<?> so) {
        try {
            // 尝试 text()
            try {
                var m = so.getClass().getMethod("text");
                Object v = m.invoke(so);
                return v != null ? v.toString() : null;
            } catch (NoSuchMethodException ignore) { }

            // 尝试 delta()
            try {
                var m = so.getClass().getMethod("delta");
                Object v = m.invoke(so);
                return v != null ? v.toString() : null;
            } catch (NoSuchMethodException ignore) { }

            // 尝试 content()
            try {
                var m = so.getClass().getMethod("content");
                Object v = m.invoke(so);
                return v != null ? v.toString() : null;
            } catch (NoSuchMethodException ignore) { }

            // 最后回退 chunk()（旧 API，避免直接调用以绕过编译期过时告警）
            try {
                var m = so.getClass().getMethod("chunk");
                Object v = m.invoke(so);
                return v != null ? v.toString() : null;
            } catch (NoSuchMethodException ignore) { }

        } catch (Exception e) {
            log.debug("提取 StreamingOutput 文本失败: {}", e.toString());
        }
        return null;
    }

    /**
     * 构建用户消息
     * 根据模式和书籍名称构建不同的消息格式
     */
    private String buildUserMessage(String question, String bookName, String mode) {
        boolean hasBookName = bookName != null && !bookName.isBlank();

        if ("chat".equals(mode)) {
            // 问答模式：直接传递用户问题，可带书籍上下文
            if (hasBookName) {
                return String.format("关于《%s》的问题：%s", bookName, question);
            }
            return question;
        } else {
            // 解读模式：请求深度解读原文
            if (hasBookName) {
                return String.format("请解读以下来自《%s》的原文：\n\n%s", bookName, question);
            }
            return "请解读以下原文：\n\n" + question;
        }
    }
}
