package top.qiyuey.book.agent;

import top.qiyuey.book.config.ModelConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * 读书问答 Agent 的 REST API 控制器 (WebFlux 版)
 */
@Tag(name = "读书问答", description = "AI 读书助手问答 API")
@RestController
@RequestMapping("/api/book")
public class BookController {

    private static final Logger log = LoggerFactory.getLogger(BookController.class);

    /**
     * 最大输入字符数限制
     * qwen-max 阶梯计费：<=32K tokens 价格较低
     * 中文约 1.5-2 字符/token，保守估计设为 20000 字符（约 10K-13K tokens）
     * 加上 System Prompt（约 1K tokens），总输入控制在 32K 以内
     */
    private static final int MAX_QUESTION_LENGTH = 20000;

    private final BookService bookService;
    private final ModelConfig modelConfig;

    public BookController(BookService bookService, ModelConfig modelConfig) {
        this.bookService = bookService;
        this.modelConfig = modelConfig;
    }

    /**
     * 获取可用模型列表
     */
    @Operation(summary = "获取可用模型列表", description = "返回所有可用的 LLM 模型及其信息")
    @GetMapping("/models")
    public ModelsResponse getAvailableModels() {
        return new ModelsResponse(modelConfig.getAvailable(), modelConfig.getDefaultModel());
    }

    /**
     * 读书问答（POST）- 流式返回，采用 Server-Sent Events
     * 立即返回连接并逐步推送进度与最终结果，避免客户端长时间等待
     */
    @Operation(
            summary = "读书问答（流式返回）",
            description = "通过 SSE 流式推送消息，先返回连接，随后推送进度与最终结果，避免等待超时。"
    )
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<BookResponseEvent>> askQuestion(
            @Parameter(description = "读书问答请求，包含问题和可选的会话ID", required = true)
            @RequestBody BookRequest request) {

        // 预处理：提取并填充默认值
        String question = request.getQuestion();
        String bookName = request.getBookName();
        String mode = StringUtils.hasText(request.getMode()) ? request.getMode() : "interpret";

        // 验证并截断过长输入，确保输入 token 在 32K 以内
        if (question != null && question.length() > MAX_QUESTION_LENGTH) {
            log.warn("问题长度 {} 超过限制 {}，将被截断", question.length(), MAX_QUESTION_LENGTH);
            question = question.substring(0, MAX_QUESTION_LENGTH) + "...(内容过长已截断)";
        }

        String threadId = StringUtils.hasText(request.getThreadId())
                ? request.getThreadId()
                : UUID.randomUUID().toString();
        String modelId = StringUtils.hasText(request.getModelId())
                ? request.getModelId()
                : modelConfig.getDefaultModel();

        // 只在流式部分使用 Reactor
        return bookService.executeBookQuery(question, bookName, threadId, modelId, mode)
                // 将业务对象包装为 SSE，增加事件类型以便前端区分
                .map(data -> ServerSentEvent.<BookResponseEvent>builder()
                        .event(data.getStatus().name())
                        .data(data)
                        .build())
                // 优雅地流式异常处理
                .onErrorResume(ex -> {
                    log.error("流式处理发生异常", ex);
                    return Flux.just(ServerSentEvent.<BookResponseEvent>builder()
                            .event("ERROR")
                            .data(new BookResponseEvent("系统繁忙: " + ex.getMessage()))
                            .build());
                });
    }

    /**
     * 读书问答请求对象
     */
    @Getter
    @Schema(description = "读书问答请求")
    public static class BookRequest {
        @Schema(description = "用户的问题/原文内容", example = "实践、认识、再实践、再认识...")
        private String question;

        @Schema(description = "书籍名称（可选）", example = "毛泽东选集")
        private String bookName;

        @Schema(description = "会话ID，用于维护对话上下文。如果不提供，将自动生成", example = "user-123")
        private String threadId;

        @Schema(description = "模型ID，可通过 /api/book/models 获取可用模型列表", example = "qwen-max")
        private String modelId;

        @Schema(description = "模式：interpret=解读模式，chat=问答模式", example = "interpret", allowableValues = {"interpret", "chat"})
        private String mode;

        public BookRequest() {}

        public BookRequest(String question, String bookName, String threadId, String modelId, String mode) {
            this.question = question;
            this.bookName = bookName;
            this.threadId = threadId;
            this.modelId = modelId;
            this.mode = mode;
        }
    }

    /**
     * 模型列表响应对象
     */
    @Getter
    @Schema(description = "可用模型列表响应")
    public static class ModelsResponse {
        @Schema(description = "可用模型列表")
        private final List<ModelConfig.ModelInfo> models;

        @Schema(description = "默认模型ID")
        private final String defaultModel;

        public ModelsResponse(List<ModelConfig.ModelInfo> models, String defaultModel) {
            this.models = models;
            this.defaultModel = defaultModel;
        }
    }
}
