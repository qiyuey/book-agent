package top.qiyuey.book.agent;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
public class BookService {

    private final BookAgentFactory agentFactory;
    private final ThreadService threadService;

    public BookService(BookAgentFactory agentFactory, ThreadService threadService) {
        this.agentFactory = agentFactory;
        this.threadService = threadService;
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

        // 记录用户消息并更新线程信息
        threadService.addMessage(threadId, "user", question);
        threadService.updateThread(threadId, null, modelId, bookName);
        threadService.generateTitleAsync(threadId, question, modelId);

        // 获取指定模型的 Agent
        ReactAgent agent = agentFactory.getAgent(modelId);
        
        // ... build user message
        String userMessage = buildUserMessage(question, bookName, mode);

        // 1. 起始事件
        String bookInfo = (bookName != null && !bookName.isBlank()) ? String.format(" [%s]", bookName) : "";
        String modeLabel = "chat".equals(mode) ? "回答" : "解读";
        BookResponseEvent startEvent = BookResponseEvent.builder()
                .status(BookResponseEvent.Status.START)
                .content(String.format("正在%s%s... (模型: %s)", modeLabel, bookInfo, modelId))
                .build();
        
        StringBuilder fullResponse = new StringBuilder();

        // 2. Agent流转换
        Flux<BookResponseEvent> agentStream;
        try {
             agentStream = agent.stream(userMessage, config)
                    .flatMap(output -> {
                        // ... existing logic ...
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
                        // 忽略非流式最终结果，避免内容重复
                        // Agent 结束时会返回包含完整历史记录的 State，如果再次提取最后一条消息，
                        // 会导致前端先收到了流式片段，又收到一次完整内容，从而显示两次。
                        
                        return Flux.empty();
                    });
        } catch (Exception e) {
             // ...
             log.error("创建 Agent 流失败", e);
             return Flux.concat(
                     Flux.just(startEvent),
                     Flux.just(BookResponseEvent.builder()
                             .status(BookResponseEvent.Status.ERROR)
                             .content("处理失败: " + e.getMessage())
                             .build())
             );
        }

        // 3. 组合流，添加超时和错误处理
        return Flux.concat(Flux.just(startEvent), agentStream)
                .timeout(Duration.ofMinutes(3))
                .doOnNext(event -> {
                    if (event.getStatus() == BookResponseEvent.Status.PROGRESS && event.getContent() != null) {
                        fullResponse.append(event.getContent());
                    }
                })
                .doOnComplete(() -> {
                     if (!fullResponse.isEmpty()) {
                         threadService.addMessage(threadId, "assistant", fullResponse.toString());
                     }
                })
                .onErrorResume(ex -> {
                    log.error("Agent 流处理异常", ex);
                    String errorMessage = buildUserFriendlyErrorMessage(ex);
                    return Flux.just(BookResponseEvent.builder()
                            .status(BookResponseEvent.Status.ERROR)
                            .content(errorMessage)
                            .build());
                });
    }

    /**
     * 将异常转换为用户友好的错误消息
     */
    private String buildUserFriendlyErrorMessage(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }

        // 超时
        if (ex instanceof TimeoutException || cause instanceof TimeoutException) {
            return "请求超时，请稍后重试。如果问题持续存在，可能是服务繁忙。";
        }

        // 网络连接问题
        if (ex instanceof WebClientRequestException || cause instanceof WebClientRequestException) {
            String message = cause.getMessage();
            if (message != null && message.contains("Connection reset")) {
                return "网络连接被重置，可能是代理配置问题或网络不稳定，请检查网络后重试。";
            }
            if (message != null && message.contains("Connection refused")) {
                return "无法连接到 AI 服务，请检查网络配置后重试。";
            }
            return "网络连接异常，请检查网络后重试。";
        }

        // Socket 异常
        if (cause instanceof SocketException) {
            return "网络连接异常：" + cause.getMessage() + "。请检查网络或代理配置。";
        }

        // DNS 解析失败
        if (cause instanceof UnknownHostException) {
            return "无法解析服务器地址，请检查网络连接。";
        }

        // API 错误（如认证失败、配额不足等）
        String message = ex.getMessage();
        if (message != null) {
            if (message.contains("401") || message.contains("Unauthorized")) {
                return "API 认证失败，请检查 API Key 配置。";
            }
            if (message.contains("429") || message.contains("rate limit")) {
                return "请求频率过高，请稍后重试。";
            }
            if (message.contains("500") || message.contains("502") || message.contains("503")) {
                return "AI 服务暂时不可用，请稍后重试。";
            }
        }

        // 默认错误消息
        return "处理请求时发生错误：" + (message != null ? message : ex.getClass().getSimpleName());
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
